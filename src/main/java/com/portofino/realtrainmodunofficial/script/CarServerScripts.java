package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.io.ScriptUtil;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 車両 (ModelVehicle) の serverScriptPath を本家と同じ Nashorn で実行する。
 * SRB3 / NGTO Builder のサーバースクリプト (onUpdate(entity, executer)) 用。
 * エンジンは定義ごとに 1 つ (本家 ScriptExecuter と同じ形; スクリプト側は
 * WeakHashMap 等でエンティティごとの状態を持つ)。
 */
public final class CarServerScripts {

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Entry INVALID = new Entry(null);

    private CarServerScripts() {
    }

    public static Entry get(VehicleDefinition def) {
        if (def == null || !def.hasServerScript()) {
            return null;
        }
        Entry e = CACHE.computeIfAbsent(def.getId(), id -> create(def));
        return e == INVALID ? null : e;
    }

    private static Entry create(VehicleDefinition def) {
        try {
            String path = def.getServerScriptPath();
            byte[] bytes = NGTFileLoader.findAsset(path);
            if (bytes == null) {
                //"scripts/..." 前置の揺れを吸収
                bytes = NGTFileLoader.findAsset("scripts/" + path);
            }
            if (bytes == null) {
                RealTrainModUnofficial.LOGGER.warn("Server script not found for {}: {}", def.getId(), path);
                return INVALID;
            }
            String source = PackScriptSource.decode(bytes);
            source = PackScriptSource.prepare(source);
            //★SuperRailBuilder3 のビルダーは車として登録されているので、その敷設処理は
            //この経路で読まれる。ここに差し込まないとブリッジが一切効かず、SRB 本来の
            //1.12.2 向けコードがそのまま走って分岐敷設で失敗する。
            source = TrainScriptSystem.appendSuperRailBuilderOverrides(source);
            ScriptEngine engine = ScriptUtil.doScript(PackScriptSource.PRELUDE + source);
            //差し込んだ差し替えが呼ぶブリッジ。この経路では束縛されていなかったため、
            //差し替えても __SRB__ が未定義で必ず失敗していた。
            engine.put("__SRB__", new SrbRailBridge());
            return new Entry(engine);
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to init server script for {}", def.getId(), t);
            return INVALID;
        }
    }

    public static final class Entry {
        private final ScriptEngine engine;
        private boolean broken;
        private boolean warned;
        //エンティティごとの ScriptExecuter (NGTO Builder の Measure が executer.count % 20 を使う。
        //null を渡すと毎 tick TypeError で onUpdate が中断していた)。
        private final java.util.Map<Object, jp.ngt.rtm.modelpack.ScriptExecuter> executers =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

        private boolean warnedClientSide;

        Entry(ScriptEngine engine) {
            this.engine = engine;
        }

        /**
         * onUpdate(entity, executer) を 1tick 分実行。
         */
        public void onUpdate(Object entity) {
            if (engine == null || broken) {
                return;
            }
            //★サーバースクリプトはサーバーでしか実行してはならない。
            //クライアントで走ると、SRB の buildNormalRail がクライアントのワールドへ
            //ブロックを置いてしまい (サーバーには存在しないので即消える)、
            //「敷設したのに何も残らない」状態になる。実測でこの経路が確認されたため、
            //入口でも塞ぐ (呼び出し側の tick ガードだけに頼らない)。
            //サイドの判定は<b>スレッド</b>で行う。entity.level().isClientSide() は
            //実測で当てにならなかった (ClientLevel.tickEntities から呼ばれているのに false を返す)。
            net.minecraft.server.MinecraftServer srv =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            boolean onServerThread = srv != null && srv.isSameThread();
            if (!onServerThread) {
                if (!warnedClientSide) {
                    warnedClientSide = true;
                    String lv = "?";
                    boolean cs = false;
                    if (entity instanceof net.minecraft.world.entity.Entity e) {
                        lv = e.level().getClass().getSimpleName();
                        cs = e.level().isClientSide();
                    }
                    RealTrainModUnofficial.LOGGER.warn(
                        "[RTMU] サーバースクリプトがサーバースレッド外で呼ばれました (実行を中止): "
                        + "thread={} level={} isClientSide={}",
                        Thread.currentThread().getName(), lv, cs);
                }
                return;
            }
            try {
                jp.ngt.rtm.modelpack.ScriptExecuter executer = executerFor(entity);
                ((Invocable) engine).invokeFunction("onUpdate", entity, executer);
                if (executer != null) {
                    executer.count++;
                }
            } catch (NoSuchMethodException e) {
                broken = true;
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    RealTrainModUnofficial.LOGGER.warn("[ScriptUtil] server onUpdate failed: {}", t.toString());
                }
            }
        }

        private jp.ngt.rtm.modelpack.ScriptExecuter executerFor(Object entity) {
            if (!(entity instanceof net.minecraft.world.entity.Entity e)
                    || !(e.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
                return null;
            }
            return executers.computeIfAbsent(entity, k ->
                new jp.ngt.rtm.modelpack.ScriptExecuter(serverLevel, e.position(), e.getName().getString()));
        }
    }
}
