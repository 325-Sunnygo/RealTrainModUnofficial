package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.npc.NpcDefinition;
import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.io.ScriptUtil;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家 EntityNPC.onUpdate → ScriptExecuter.execScript(this) の移植。
 * NPC の serverScriptPath (NPCConfig extends ModelConfig) を毎 tick
 * onUpdate(npc, scriptExecuter) として呼ぶ。サーバー専用。
 */
public final class NpcServerScripts {

    private static final Map<String, ScriptEngine> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private NpcServerScripts() {
    }

    /** サーバー tick から 1 回呼ぶ。 */
    public static void onUpdate(jp.ngt.rtm.entity.npc.EntityNPC npc) {
        if (npc == null || npc.level().isClientSide()) {
            return;
        }
        NpcDefinition def = npc.getDefinition();
        if (def == null || !def.hasServerScript()) {
            return;
        }
        ScriptEngine engine = get(def);
        if (engine == null) {
            return;
        }
        // サーバースレッドでのみ実行する (クライアントで走らせるとワールドを壊す)。
        net.minecraft.server.MinecraftServer srv =
            net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (srv == null || !srv.isSameThread()) {
            return;
        }
        try {
            ((Invocable) engine).invokeFunction("onUpdate", npc, npc.scriptExecuter);
            npc.scriptExecuter.count++;
        } catch (NoSuchMethodException e) {
            FAILED.add(def.getId());
        } catch (Throwable t) {
            if (FAILED.size() < 256 && FAILED.add(def.getId() + "|" + t)) {
                RealTrainModUnofficial.LOGGER.warn("[RTMU] NPC サーバースクリプト onUpdate 失敗: {} ({})",
                    def.getId(), t.toString());
            }
        }
    }

    private static ScriptEngine get(NpcDefinition def) {
        if (def == null || !def.hasServerScript() || FAILED.contains(def.getId())) {
            return null;
        }
        return CACHE.computeIfAbsent(def.getId(), id -> create(def));
    }

    private static ScriptEngine create(NpcDefinition def) {
        try {
            String path = def.getServerScriptPath();
            byte[] bytes = NGTFileLoader.findAsset(path);
            if (bytes == null) {
                bytes = NGTFileLoader.findAsset("scripts/" + path);
            }
            if (bytes == null) {
                RealTrainModUnofficial.LOGGER.warn("NPC server script not found for {}: {}", def.getId(), path);
                FAILED.add(def.getId());
                return null;
            }
            String source = PackScriptSource.prepare(PackScriptSource.decode(bytes), path);
            ScriptEngine engine = ScriptUtil.doScript(PackScriptSource.PRELUDE + source);
            RealTrainModUnofficial.LOGGER.info("[RTMU] NPC サーバースクリプト読み込み: {} ({})", def.getId(), path);
            return engine;
        } catch (Throwable t) {
            RealTrainModUnofficial.LOGGER.warn("Failed to init NPC server script for {}", def.getId(), t);
            FAILED.add(def.getId());
            return null;
        }
    }

    /** 診断コマンド用。 */
    public static void forget(String id) {
        if (id != null) {
            CACHE.remove(id);
            FAILED.remove(id);
        }
    }
}
