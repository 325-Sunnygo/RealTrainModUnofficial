package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.rtm.modelpack.ScriptExecuter;
import net.minecraft.server.level.ServerLevel;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家の「サーバースクリプト」機構 (ModelSetBase.serverSE + ScriptExecuter)。
 *
 * <p>設置物の定義 JSON に {@code "serverScriptPath": "scripts/xxx.js"} があると、本家は
 * サーバー側で毎 tick {@code onUpdate(entity, scriptExecuter)} を呼ぶ。列車検知器
 * (hi03TrainDetector 等) は<b>全処理をここに書く</b>ので、この機構が無いと 1 行も動かない。
 *
 * <p>スクリプトは 1.7.10 の API 名で書かれているため、{@code jp.ngt.mccompat} の互換クラスを
 * グローバルに束縛してから eval する。{@code importPackage} は 1.21 に存在しないパッケージ
 * (net.minecraft.tileentity 等) を指すので何も入らない — 束縛で先に定義しておくことで、
 * スクリプト側の名前解決が通る。
 */
public final class InstalledObjectServerScripts {

    /** 定義 ID → スクリプトエンジン (読み込み失敗も INVALID として覚える)。 */
    private static final Map<String, ScriptEngine> ENGINES = new ConcurrentHashMap<>();
    private static final Set<String> INVALID = ConcurrentHashMap.newKeySet();
    /** onUpdate が落ちた定義は以後呼ばない (毎 tick 例外を投げ続けないため)。 */
    /** ログ済みの定義 ID (実行は止めない。本家に「失敗したら停止」は無い)。 */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /**
     * スクリプトが 1.7.10 の名前で参照するクラスを、実クラスへ束縛する。
     * <p>importPackage では解決できない (1.21 に該当パッケージが無い) ものだけ。
     */
    private static final String PRELUDE = String.join("\n",
            //rtm-ts Multi-target 対策 (詳細は PackScriptSource.PRELUDE)。prepend されたターゲット IIFE が
            //base の var 宣言前に走っても落ちないよう、最初に RTMX_COMPAT_TARGETS を確定させる。
            "var RTMX_COMPAT_TARGETS = (typeof RTMX_COMPAT_TARGETS !== 'undefined' && RTMX_COMPAT_TARGETS) ? RTMX_COMPAT_TARGETS : {};",
            "var __t = function(n){ try { return Java.type(n); } catch (e) { return null; } };",
            "var TileEntityCommandBlock = __t('jp.ngt.mccompat.tileentity.TileEntityCommandBlock');",
            "var CommandBlockLogic     = __t('jp.ngt.mccompat.tileentity.CommandBlockLogic');",
            //ATS-Ps 地上子ビーコンが連動信号機を `tile instanceof TileEntitySignal` で判定し
            //`NGTUtil.getField(TileEntitySignal.class, tile, ["signalLevel"])` で現示を読む。
            //RTMU の信号機タイルは InstalledObjectBlockEntity なので、それを TileEntitySignal として束縛する
            //(signalLevel フィールドは tick() で getLegacySignalState() を反映済み)。
            "var TileEntitySignal      = __t('com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity');",
            "var AxisAlignedBB         = __t('jp.ngt.mccompat.AxisAlignedBB');",
            "var Blocks                = __t('jp.ngt.mccompat.init.Blocks');",
            "var NGTUtil               = __t('jp.ngt.ngtlib.util.NGTUtil');",
            "var NGTLog                = __t('jp.ngt.ngtlib.io.NGTLog');",
            "var Vec3                  = __t('jp.ngt.ngtlib.math.Vec3');",
            "var EntityTrainBase       = __t('jp.ngt.rtm.entity.train.EntityTrainBase');",
            "var EntityBogie           = __t('jp.ngt.rtm.entity.train.EntityBogie');",
            "var EntityTrain           = __t('jp.ngt.rtm.entity.train.EntityTrain');",
            "var RTMCore               = __t('jp.ngt.rtm.RTMCore');",
            "");

    private InstalledObjectServerScripts() {
    }

    /** リソースリロード時に読み直せるよう捨てる。 */
    public static void clear() {
        ENGINES.clear();
        INVALID.clear();
        LOGGED.clear();
    }

    /**
     * サーバー tick から毎回呼ぶ。スクリプトを持たない設置物は即 return。
     */
    public static void tick(ServerLevel level, InstalledObjectBlockEntity be) {
        InstalledObjectDefinition def = be.getDefinition();
        if (def == null || !def.hasServerScript()) {
            return;
        }
        String id = def.getId();
        if (INVALID.contains(id)) {
            return;
        }
        ScriptEngine se = ENGINES.get(id);
        if (se == null) {
            se = load(def);
            if (se == null) {
                INVALID.add(id);
                return;
            }
            ENGINES.put(id, se);
        }

        //スクリプトは設置物を Entity として扱い、座標・向き・当たり判定を SRG フィールドで読む
        be.refreshScriptFields();
        ScriptExecuter executer = be.getScriptExecuter(level);
        try {
            ((Invocable) se).invokeFunction("onUpdate", be, executer);
            executer.count++;
        } catch (NoSuchMethodException e) {
            //onUpdate を持たないスクリプト (ライブラリだけ等) は対象外
            INVALID.add(id);
        } catch (Throwable t) {
            //本家 ScriptUtil.doScriptIgnoreError と同じく、失敗しても<b>止めない</b>。
            //以前はここで FAILED.add(id) して定義ごと永久停止させていたため、
            //1 個の設置物が 1 tick こけただけで<b>同じ種類の設置物が全部</b>動かなくなっていた。
            //ログだけ定義ごとに 1 回へ絞る (本家の printStackTrace は毎 tick 出て実用にならない)。
            if (LOGGED.add(id)) {
                RealTrainModUnofficial.LOGGER.warn("[serverScript] {} の onUpdate が失敗しました (実行は継続します): {}",
                        id, String.valueOf(t.getCause() != null ? t.getCause() : t));
            }
        }
    }

    private static ScriptEngine load(InstalledObjectDefinition def) {
        String path = def.getServerScriptPath();
        try {
            byte[] bytes = NGTFileLoader.findAsset(path);
            if (bytes == null) {
                RealTrainModUnofficial.LOGGER.warn("[serverScript] スクリプトが見つかりません: {} ({})", def.getId(), path);
                return null;
            }
            //パックのスクリプトは Shift-JIS のことがある。UTF-8 で読むと日本語コメント中の
            //バイトが化けて構文まで壊れるので、既存のパック用デコーダに任せる。
            String source = com.portofino.realtrainmodunofficial.util.PackTextDecoder.decodeText(bytes);
            //SRB 系のスクリプトが設置物として来た場合もブリッジを効かせる
            //(SuperRailBuilderVersion を含まないスクリプトには何も足さない)。
            source = TrainScriptSystem.appendSuperRailBuilderOverrides(source);
            //★ //include の展開と FQN remap は全経路で共通 (PackScriptSource.prepare)。
            //以前この経路だけ prepare() を通しておらず、include を使うレール/架線/設置物パックが
            //「スクリプトはあるのに何も描かない」状態になっていた。
            ScriptEngine se = ScriptUtil.doScript(PRELUDE + com.portofino.realtrainmodunofficial.script.PackScriptSource.prepare(source));
            se.put("__SRB__", new com.portofino.realtrainmodunofficial.script.SrbRailBridge());
            return se;
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("[serverScript] 読み込み失敗: {} ({})", def.getId(), path, t);
            return null;
        }
    }
}
