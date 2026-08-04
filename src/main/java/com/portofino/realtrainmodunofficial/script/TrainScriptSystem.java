package com.portofino.realtrainmodunofficial.script;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.model.MqoModelLoader;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import com.portofino.realtrainmodunofficial.util.PackTextDecoder;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.world.entity.player.Player;

import javax.script.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrainScriptSystem {

    private static final String SCRIPT_PATH_KEY = "__ptScriptPath";

    /** ユーザースクリプト先頭にprependするJS。NGTText等を純JSで定義する。 */
    private static final String SCRIPT_MODEL_KEY = "__ptScriptModel";
    private static TrainScriptSystem instance;
    private ScriptEngine engine;
    /** スクリプトに見せるRTMCore.VERSION。実クラスを唯一の出所にする。 */
    private static final String SCRIPT_CORE_VERSION = jp.ngt.rtm.RTMCore.VERSION;
    private static final Set<String> REPORTED_SCRIPT_ERRORS = ConcurrentHashMap.newKeySet();

    // ★JS 再実装の受け皿だった互換クラス群は撤去 (実シム + PRELUDE へ一本化)。

    // ★JS 再実装の受け皿だった互換クラス群は撤去 (実シム + PRELUDE へ一本化)。

    private TrainScriptSystem() {
    }

    public static TrainScriptSystem getInstance() {
        if (instance == null) {
            instance = new TrainScriptSystem();
        }
        return instance;
    }

    public void initialize() {
        engine = createScriptEngine();
        if (engine == null) {
            RealTrainModUnofficial.LOGGER.warn("Nashorn script engine not available (org.openjdk.nashorn).");
        }
    }


    public static Object doScriptFunctionCompat(Object scriptEngine, String functionName, Object args) {
        if (!(scriptEngine instanceof Invocable invocable) || functionName == null || functionName.isBlank()) {
            return null;
        }
        try {
            return invocable.invokeFunction(functionName, toObjectArray(args));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Script exec error : " + functionName, e);
        } catch (ScriptException e) {
            throw new RuntimeException("Script exec error : " + functionName, e);
        }
    }

    public static Object doScriptIgnoreErrorCompat(Object scriptEngine, String functionName, Object args) {
        try {
            return doScriptFunctionCompat(scriptEngine, functionName, args);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object getScriptFieldCompat(Object scriptEngine, String fieldName) {
        if (scriptEngine instanceof ScriptEngine engine && fieldName != null) {
            return engine.get(fieldName);
        }
        return null;
    }

    private static Object[] toObjectArray(Object args) {
        if (args == null) {
            return new Object[0];
        }
        if (args instanceof Object[] array) {
            return array;
        }
        if (args instanceof List<?> list) {
            return list.toArray();
        }
        Class<?> type = args.getClass();
        if (type.isArray()) {
            int length = java.lang.reflect.Array.getLength(args);
            Object[] out = new Object[length];
            for (int i = 0; i < length; i++) {
                out[i] = java.lang.reflect.Array.get(args, i);
            }
            return out;
        }
        return new Object[]{args};
    }

    private static ScriptEngine getScriptEngine() {
        TrainScriptSystem system = getInstance();
        if (system.engine != null) {
            return system.engine;
        }

        return createScriptEngine();
    }

    /**
     * スクリプトエンジンは本家 jp.ngt.ngtlib.io.ScriptUtil の 1 本だけを使う。
     * 本家 (KaizPatchX 1.7.10 ScriptUtil:36-54) は NashornScriptEngineFactory を直接叩き
     * -doe / --language=es6 を渡して毎回新規エンジンを作る。
     */
    private static ScriptEngine createScriptEngine() {
        return jp.ngt.ngtlib.io.ScriptUtil.createEngine();
    }







    public static ScriptEngine loadStandaloneScript(String scriptPath, String script, String modelName) {
        ScriptEngine scriptEngine = createScriptEngine();
        if (scriptEngine == null) {
            return null;
        }
        try {
            ScriptModelRenderer renderer = new ScriptModelRenderer(null, modelName);
            // ★本家の契約と同じく束縛は renderer だけ (KaizPatchX ScriptUtil:36-54)。
            // 以前はここで injectScriptCompatibility が 330 行の JS 再実装を eval しており、
            // NGTUtil / NGTLog / ScriptUtil / ModelPackManager 等が実クラスではなく
            // ダミー JS へ束縛されていた (NGTUtil.isClient が常に true 等)。
            scriptEngine.put("renderer", renderer);
            scriptEngine.put("model", renderer.getModel());
            // SRB3 のネイティブ敷設ブリッジは JS 再実装ではなく実 Java なので残す。
            scriptEngine.put("__SRB__", new SrbRailBridge());
            scriptEngine.put(SCRIPT_PATH_KEY, scriptPath == null ? "" : scriptPath);
            scriptEngine.put(SCRIPT_MODEL_KEY, modelName == null ? "" : modelName);
            // サーバー/サウンドスクリプトは描画しない (GL を触らない) ので、車両描画側と
            // 同じ PackScriptSource の前処理・束縛に一本化する。
            // SRB3 のネイティブ敷設ブリッジだけはこの経路固有なので個別に付ける。
            script = PackScriptSource.PRELUDE
                    + appendSuperRailBuilderOverrides(PackScriptSource.prepare(script, scriptPath));
            scriptEngine.eval(script);
            prepareScriptRuntimeBeforeInit(scriptEngine);
            invokeScriptInit(scriptEngine, renderer);
            prepareScriptRuntimeAfterInit(scriptEngine);
            return scriptEngine;
        } catch (ScriptException e) {
            reportScriptError(scriptEngine, "load(standalone)", e);
            RealTrainModUnofficial.LOGGER.error("Failed to execute standalone script: {}", scriptPath, e);
        } catch (Exception e) {
            reportScriptError(scriptEngine, "load(standalone)", e);
            RealTrainModUnofficial.LOGGER.error("Unexpected error loading standalone script: {}", scriptPath, e);
        }
        return null;
    }

    /**
     * OpList 経路の描画スクリプトだけに要る機能的な回避策。
     * クラス名の置換 (旧方言) ではなく、1.21 で挙動が変わる 2 点への対処:
     */


    /**
     * SRB3 のスクリプトにネイティブ敷設ブリッジの上書き定義を追加する。
     * SRB のビルダーは車 (ModelVehicle) なので、そのサーバースクリプトは
     * CarServerScripts が読む。
     */
    /**
     * かつてここに SuperRailBuilder3 専用の関数差し替えがあった (約 60 行)。
     * getSelectedSlotItem を常に null にし、getPlayerRail /
     * buildNormalRail / buildBranchRail / createRailPosition /
     * getTileEntity / deleteRail を RTMU のブリッジ (__SRB__) へ
     * 差し替えるものだったが、これはパック名で分岐する特別処理であり本家に無い。
     */
    public static String appendSuperRailBuilderOverrides(String script) {
        return script;
    }



    private static void prepareScriptRuntimeBeforeInit(ScriptEngine scriptEngine) {
        try {
            scriptEngine.eval(
                "if (typeof frontSideTrainList === 'undefined') frontSideTrainList = [];\n" +
                "if (typeof rearSideTrainList === 'undefined') rearSideTrainList = [];\n" +
                "if (typeof __ptNoopPart !== 'function') __ptNoopPart = function() { return { render: function() {}, renderLight: function() {}, setOption: function() {}, addEntriesSet: function() {}, addMotionData: function() {}, addState: function() {}, getDoorState: function() { return false; }, getDoorPosZ: function() { return 0; }, getFlashState: function() { return false; } }; };\n" +
                "if (typeof __ptDummyTextureData !== 'function') __ptDummyTextureData = function() { return { images: [{}], size: 1, rate: 1, width: 1, height: 1 }; };\n" +
                "if (typeof playComplessorSound !== 'function') {\n" +
                "  playComplessorSound = function(su, soundDomain, soundName) {\n" +
                "    if (!su) return;\n" +
                "    if (su.isComplessorActive && su.isComplessorActive()) {\n" +
                "      var count = su.complessorCount ? su.complessorCount() : 0;\n" +
                "      var c0 = 50;\n" +
                "      var vol = 1.0;\n" +
                "      if (count < c0) { var c1 = c0 * c0; vol = -((((count - c0) * (count - c0)) + c1) / c1); }\n" +
                "      var pitch = count < c0 ? (vol * 0.5) + 0.5 : 1.0;\n" +
                "      if (su.playSound) su.playSound(soundDomain, soundName, vol, pitch);\n" +
                "    } else if (su.stopSound) {\n" +
                "      su.stopSound(soundDomain, soundName);\n" +
                "    }\n" +
                "  };\n" +
                "}\n" +
                "if (typeof playCompressorSound !== 'function' && typeof playComplessorSound === 'function') playCompressorSound = playComplessorSound;\n" +
                "if (typeof CustomTexture !== 'undefined') {\n" +
                "  CustomTexture._load = function(path) {\n" +
                "    var size = 1, width = 1, height = 1;\n" +
                "    try { if (renderer && typeof renderer.getScriptTextureFrameCount === 'function') size = Math.max(1, renderer.getScriptTextureFrameCount('minecraft', String(path))); } catch (e) { size = path && String(path).toLowerCase().indexOf('.gif') >= 0 ? 64 : 1; }\n" +
                "    try { if (renderer && typeof renderer.getScriptTextureWidth === 'function') width = Math.max(1, renderer.getScriptTextureWidth('minecraft', String(path))); } catch (e) {}\n" +
                "    try { if (renderer && typeof renderer.getScriptTextureHeight === 'function') height = Math.max(1, renderer.getScriptTextureHeight('minecraft', String(path))); } catch (e) {}\n" +
                "    var images = [];\n" +
                "    for (var i = 0; i < size; i++) images.push(path);\n" +
                "    return { images: images, size: size, rate: 8, width: width, height: height };\n" +
                "  };\n" +
                "  if (CustomTexture.prototype) {\n" +
                "    CustomTexture.prototype.bindTexture = function(entity, frameIndex) { if (renderer && typeof renderer.bindScriptTexture === 'function') renderer.bindScriptTexture('minecraft', this.texturePath, frameIndex || 0); };\n" +
                "    CustomTexture.prototype.bindDefaultTexture = function(renderer) { if (renderer && typeof renderer.clearScriptTexture === 'function') renderer.clearScriptTexture(); if (renderer && typeof renderer.clearUvWindow === 'function') renderer.clearUvWindow(); };\n" +
                "    CustomTexture.prototype._uploadTexture = function(entity, bufferedImage) {};\n" +
                "    CustomTexture.prototype._bindTextureChached = function(frameIndex, textureId) {};\n" +
                "  }\n" +
                "}\n"
            );
            scriptEngine.eval(
                "if (typeof CustomAnimator !== 'undefined' && CustomAnimator.prototype && !CustomAnimator.prototype.__ptAnimatorFacesWrapped) {\n" +
                "  CustomAnimator.prototype.__ptAnimatorFacesWrapped = true;\n" +
                "  CustomAnimator.prototype.__ptOldRender = CustomAnimator.prototype.render;\n" +
                "  CustomAnimator.prototype.setFacesFromParts = function(part) {\n" +
                "    this.__ptParts = part;\n" +
                "    this.preVertexList = [];\n" +
                "    try { if (renderer && typeof renderer.markScriptManagedParts === 'function') renderer.markScriptManagedParts(part); } catch (e0) {}\n" +
                "    if (!renderer || typeof renderer.getScriptQuadVertexLists !== 'function') return;\n" +
                "    var faces = Java.from(renderer.getScriptQuadVertexLists(part));\n" +
                "    for (var i = 0; i < faces.length; i++) {\n" +
                "      var face = Java.from(faces[i]);\n" +
                "      var v = [];\n" +
                "      for (var j = 0; j < face.length; j++) {\n" +
                "        var p = Java.from(face[j]);\n" +
                "        v.push([+p[0], +p[1], +p[2]]);\n" +
                "      }\n" +
                "      if (v.length === 4) this.preVertexList.push(v);\n" +
                "    }\n" +
                "  };\n" +
                "  CustomAnimator.prototype.render = function(renderer, entity, pass, isLit) {\n" +
                "    if (renderer && typeof renderer.disableReplayCacheForFrame === 'function') renderer.disableReplayCacheForFrame();\n" +
                "    return this.__ptOldRender ? this.__ptOldRender.apply(this, arguments) : undefined;\n" +
                "  };\n" +
                "}\n"
            );
            scriptEngine.eval(
                "if (typeof CustomMonitor_LCD !== 'undefined') {\n" +
                "  CustomMonitor_LCD = function(modelSet, modelObj, baseParts, texturePath) { this.baseParts = baseParts; this.texturePath = texturePath; this.gif = new CustomTexture(modelObj, texturePath); };\n" +
                "  CustomMonitor_LCD.prototype = { constructor: CustomMonitor_LCD, render: function(renderer, entity, pass, partialTick) { if (!entity || pass > 2 || !this.baseParts) return; var id = 0; try { id = Math.floor(entity.getTrainStateData(8)); } catch (e) {} if (typeof lcdDisplaySet !== 'undefined' && lcdDisplaySet[id]) { var set = lcdDisplaySet[id]; var tick = 0; try { tick = renderer.getTick(entity); } catch (e) {} id = set[Math.floor((tick % (set.length * 200)) / 200)] || set[0] || id; } else { try { var frames = renderer.getScriptTextureFrameCount('minecraft', this.texturePath); var tick2 = renderer.getTick(entity); if (frames > 0) id = Math.floor(tick2 / 2) % frames; } catch (e2) {} } try { if (typeof renderer.setLightmapMaxBrightness === 'function') renderer.setLightmapMaxBrightness(); if (renderer && typeof renderer.renderGifOnParts === 'function') renderer.renderGifOnParts(this.baseParts, 'minecraft', this.texturePath, id); else { this.gif.bindTexture(entity, id); this.baseParts.render(renderer); } } finally { if (typeof renderer.enableLighting === 'function') renderer.enableLighting(); renderer.clearUvWindow(); renderer.clearScriptTexture(); } } };\n" +
                "}\n"
            );
            scriptEngine.eval(
                "if (typeof DoorRenderer !== 'undefined' && DoorRenderer.prototype && !DoorRenderer.prototype.__ptDoorUpdateWrapped) {\n" +
                "  DoorRenderer.prototype.__ptDoorUpdateWrapped = true;\n" +
                "  DoorRenderer.prototype._isUpdateTick = function(entity, pass, renderer) { if (!entity || pass !== 0) return false; var currentTick = renderer.getTick(entity); var key = 'prevTick_' + (entity.getUUID ? entity.getUUID() : 'entity'); var prevTick = this.hashMap.get(key); this.hashMap.put(key, currentTick); return prevTick !== currentTick; };\n" +
                "  DoorRenderer.prototype._calcZPos = function(entity, pass, partialTick) { if (!entity || pass > 2) return 0; var m = 0; try { if (this.dir === DoorRenderer.dir.left && renderer && typeof renderer.getDoorMovementL === 'function') m = renderer.getDoorMovementL(entity); else if (this.dir === DoorRenderer.dir.right && renderer && typeof renderer.getDoorMovementR === 'function') m = renderer.getDoorMovementR(entity); else { var state = Math.floor(entity.getTrainStateData(4)); var open = this.dir === DoorRenderer.dir.left ? ((state & 2) === 2) : ((state & 1) === 1); m = open ? 1 : 0; } } catch (e) { m = 0; } if (m < 0) m = 0; if (m > 1) m = 1; var pos = this.moveMaxZ * m; var map = this.hashMap.get(entity) || new java.util.HashMap(); map.put('posZ', pos); map.put('cachedRenderPos', pos); this.hashMap.put(entity, map); return this.isInvertMove ? -pos : pos; };\n" +
                "}\n"
            );
            scriptEngine.eval(
                "if (typeof CustomMonitor_JRE_1 !== 'undefined') {\n" +
                "  CustomMonitor_JRE_1 = function(modelSet, modelObj, baseParts) { this.baseParts = baseParts; this.hashMap = new java.util.HashMap(); };\n" +
                "  CustomMonitor_JRE_1.prototype = { constructor: CustomMonitor_JRE_1, setOption: function() {}, render: function() {}, getHashMap: function(entity) { return this.hashMap.get(entity) || {}; }, setHashMapData: function(entity, key, value) { var data = this.getHashMap(entity); data[key] = value; this.hashMap.put(entity, data); }, getHashMapData: function(entity, key) { return this.getHashMap(entity)[key]; } };\n" +
                "}\n" +
                "if (typeof CustomMonitor_JRE_2 !== 'undefined') {\n" +
                "  var __ptJre2EntrySet = CustomMonitor_JRE_2.EntrySet || {};\n" +
                "  CustomMonitor_JRE_2 = function(modelSet, modelObj, baseParts) { this.baseParts = baseParts; this.entrySet = {}; this.hashMap = new java.util.HashMap(); };\n" +
                "  CustomMonitor_JRE_2.EntrySet = __ptJre2EntrySet;\n" +
                "  ['tc','mc1','mc2','t','tsd','m1','m2','m3','m4','m5','m6','m7','m8'].forEach(function(k) { if (!CustomMonitor_JRE_2.EntrySet[k]) CustomMonitor_JRE_2.EntrySet[k] = { iconF: 'entry_' + k, iconB: 'entry_' + k }; });\n" +
                "  CustomMonitor_JRE_2.prototype = { constructor: CustomMonitor_JRE_2, addEntrySet: function(trainName, type, options) { this.entrySet[trainName] = { type: type || {}, options: options || {} }; }, addEntriesSet: function(trainNameList, type, options) { if (!trainNameList) return; for (var i = 0; i < trainNameList.length; i++) this.addEntrySet(trainNameList[i], type, options); }, setOption: function(options, entity) { var data = this.getHashMap(entity); data.options = options || {}; this.hashMap.put(entity, data); }, render: function() {}, getHashMap: function(entity) { return this.hashMap.get(entity) || {}; }, setHashMapData: function(entity, key, value) { var data = this.getHashMap(entity); data[key] = value; this.hashMap.put(entity, data); }, getHashMapData: function(entity, key) { return this.getHashMap(entity)[key]; } };\n" +
                "}\n"
            );
            scriptEngine.eval(
                "if (typeof CustomLightParts !== 'undefined' && CustomLightParts.prototype && !CustomLightParts.prototype.__ptLightModeWrapped) {\n" +
                "  CustomLightParts.prototype.__ptLightModeWrapped = true;\n" +
                "  CustomLightParts.prototype.__ptOldRenderLight = CustomLightParts.prototype.renderLight;\n" +
                "  CustomLightParts.prototype.__ptOldRender = CustomLightParts.prototype.render;\n" +
                "  CustomLightParts.prototype.__ptLightAllowed = function(entity) {\n" +
                "    var mode = 0;\n" +
                "    try { mode = Math.floor(entity.getTrainStateData(5)); } catch (e) {}\n" +
                "    if (this.lightTextureSuffix === '_headLight') return mode === 1 || mode === 3;\n" +
                "    if (this.lightTextureSuffix === '_tailLight') return mode === 2 || mode === 3;\n" +
                "    return true;\n" +
                "  };\n" +
                "  CustomLightParts.prototype.render = function(renderer, entity, pass, isObjectGlow) {\n" +
                "    if (entity && isObjectGlow && !this.__ptLightAllowed(entity)) { if (this.parts && typeof this.parts.render === 'function') this.parts.render(renderer); return; }\n" +
                "    return this.__ptOldRender.apply(this, arguments);\n" +
                "  };\n" +
                "  CustomLightParts.prototype.renderLight = function(renderer, entity, pass) {\n" +
                "    if (!this.__ptLightAllowed(entity)) return;\n" +
                "    return this.__ptOldRenderLight.apply(this, arguments);\n" +
                "  };\n" +
                "}\n"
            );
        } catch (ScriptException e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to prepare script runtime before init", e);
        }
    }

    private static void prepareScriptRuntimeAfterInit(ScriptEngine scriptEngine) {
        try {
            scriptEngine.eval(
                "if (typeof frontSideTrainList === 'undefined') frontSideTrainList = [];\n" +
                "if (typeof rearSideTrainList === 'undefined') rearSideTrainList = [];\n" +
                "if (typeof __ptNoopPart === 'function') {\n" +
                "  if (typeof lcd1 === 'undefined') lcd1 = __ptNoopPart();\n" +
                "  if (typeof lcd2 === 'undefined') lcd2 = __ptNoopPart();\n" +
                "  if (typeof monitor1 === 'undefined') monitor1 = __ptNoopPart();\n" +
                "  if (typeof monitor2 === 'undefined') monitor2 = __ptNoopPart();\n" +
                "  if (typeof timsMonitor === 'undefined') timsMonitor = __ptNoopPart();\n" +
                "}\n"
            );
        } catch (ScriptException e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to prepare script runtime after init", e);
        }
    }

    private static String quoteJs(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static void invokeScriptInit(ScriptEngine scriptEngine, ScriptModelRenderer renderer) {
        Object initModel = renderer.getModel();
        // 無音終了 (ネイティブ/メモリ由来) の切り分け用。モデル1つにつき1回だけ出る。
        RealTrainModUnofficial.LOGGER.info("[RTMU] model script init: {}", renderer.getModelName());
        if (scriptEngine instanceof Invocable invocable) {
            try {
                invocable.invokeFunction("init", renderer, initModel);
                RealTrainModUnofficial.LOGGER.info("[RTMU] model script init done: {}", renderer.getModelName());
                return;
            } catch (NoSuchMethodException ignored) {
                try {
                    invocable.invokeFunction("init");
                    return;
                } catch (NoSuchMethodException ignored2) {
                    // no init function with either signature
                } catch (ScriptException e) {
                    RealTrainModUnofficial.LOGGER.error("Failed to invoke init(renderer, model) for model script", e);
                    return;
                } catch (RuntimeException e) {
                    RealTrainModUnofficial.LOGGER.warn("Old model script init failed; keeping script available for render fallback", e);
                    return;
                }
            } catch (ScriptException e) {
                RealTrainModUnofficial.LOGGER.error("Failed to invoke init for model script", e);
                return;
            } catch (RuntimeException e) {
                RealTrainModUnofficial.LOGGER.warn("Old model script init failed; keeping script available for render fallback", e);
                return;
            }
        }

        try {
            scriptEngine.eval("if (typeof init === 'function') init();");
        } catch (ScriptException e) {
            RealTrainModUnofficial.LOGGER.error("Failed to invoke init() fallback for model script", e);
        }
    }


    /**
     * 本家 jp.ngt.ngtlib.io.ScriptUtil#doScriptIgnoreError と同じ意味。
     * エラーが出てもスクリプトは止めない。本家は printStackTrace して次の tick もそのまま呼ぶ。
     */
    private static void reportScriptFailure(ScriptEngine scriptEngine, String phase, Throwable error) {
        if (scriptEngine == null) {
            return;
        }
        String key = System.identityHashCode(scriptEngine) + "\u0000" + phase;
        if (LOGGED_SCRIPT_FAILURES.add(key)) {
            reportScriptError(scriptEngine, phase, error);
        }
        noteScriptFailure(key, scriptEngine, phase, error);
    }

    // ------------------------------------------------------------------
    // 壊れたスクリプトを呼び続けない
    // ------------------------------------------------------------------

    /**
     * 失敗し続けるスクリプトは呼ぶのをやめる。
     * 以前は失敗してもログを 1 回出すだけで毎 tick 呼び続けていた。
     */
    private static final int SCRIPT_FAILURE_LIMIT = 5;

    /** (エンジン, 呼び口) → 連続失敗回数。負値 = 停止済み。 */
    private static final java.util.Map<String, Integer> SCRIPT_FAILURE_COUNT =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static void noteScriptFailure(String key, ScriptEngine scriptEngine, String phase,
                                          Throwable error) {
        boolean fatal = error instanceof StackOverflowError || error instanceof OutOfMemoryError;
        Integer prev = SCRIPT_FAILURE_COUNT.get(key);
        if (prev != null && prev < 0) {
            return;
        }
        int count = fatal ? SCRIPT_FAILURE_LIMIT : (prev == null ? 1 : prev + 1);
        if (count >= SCRIPT_FAILURE_LIMIT) {
            SCRIPT_FAILURE_COUNT.put(key, -1);
            Object rawScriptPath = scriptEngine.get(SCRIPT_PATH_KEY);
            RealTrainModUnofficial.LOGGER.warn(
                "[RTMU Script] {} の {} を呼ぶのをやめました ({})。"
                + "前提パックが足りていないか、スクリプトが壊れています。",
                rawScriptPath == null ? "(unknown script)" : rawScriptPath,
                phase,
                fatal ? error.getClass().getSimpleName() : "失敗が " + count + " 回続いた");
        } else {
            SCRIPT_FAILURE_COUNT.put(key, count);
        }
    }

    /** この呼び口を止めてあるか。 */
    private static boolean isScriptDisabled(ScriptEngine scriptEngine, String phase) {
        if (scriptEngine == null) {
            return false;
        }
        Integer v = SCRIPT_FAILURE_COUNT.get(System.identityHashCode(scriptEngine) + "\u0000" + phase);
        return v != null && v < 0;
    }

    /** 成功したので連続失敗の数を捨てる。 */
    private static void noteScriptSuccess(ScriptEngine scriptEngine, String phase) {
        if (scriptEngine == null) {
            return;
        }
        SCRIPT_FAILURE_COUNT.remove(System.identityHashCode(scriptEngine) + "\u0000" + phase);
    }

    /** 既に 1 度ログした (engine, phase) の組。 */
    private static final java.util.Set<String> LOGGED_SCRIPT_FAILURES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void reportScriptError(ScriptEngine scriptEngine, String phase, Throwable error) {
        if (scriptEngine == null || error == null) {
            return;
        }
        Object rawScriptPath = scriptEngine.get(SCRIPT_PATH_KEY);
        Object rawModelName = scriptEngine.get(SCRIPT_MODEL_KEY);
        String scriptPath = rawScriptPath == null ? "(unknown script)" : rawScriptPath.toString();
        String modelName = rawModelName == null ? "" : rawModelName.toString();
        String detail = error.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = error.getClass().getSimpleName();
        }
        String summary = scriptPath
            + (modelName.isBlank() ? "" : " [" + modelName + "]")
            + " @ " + phase + " : " + detail;
        if (REPORTED_SCRIPT_ERRORS.add(summary)) {
            ClientHooks.showScriptErrorMessage(summary);
        }
    }

    /** サーバスクリプトを毎tick実行する。onUpdate(entity, executer)形式。 */
    /**
     * 本家 ScriptExecuter.execScript (KaizPatchX jp/ngt/rtm/modelpack/ScriptExecuter.java:32-36) の移植。
     * this.callMethod(selector, "onUpdate", selector, this);  // onUpdate(entity, executer)
     * ++this.count;
     */
    public static void invokeServerScriptOnUpdate(ScriptEngine scriptEngine, Object entity,
                                                  jp.ngt.rtm.modelpack.ScriptExecuter executer) {
        if (scriptEngine == null) return;
        if (executer != null) {
            executer.beginScript(entity);   //本家 getEntityWorld 相当: caller から world/座標を取る
        }
        try {
            scriptEngine.put("entity", entity);
            scriptEngine.put("executer", executer);
            scriptEngine.put("executor", executer);
        } catch (Throwable ignored) {
        }
        Invocable invocable = (Invocable) scriptEngine;
        // ★止めてあるものは呼ばない (noteScriptFailure の説明を参照)。
        // サーバースクリプトは編成の全車ぶん毎 tick 走るので、
        // 失敗し続けると tick 時間がそのまま溶ける。
        if (isScriptDisabled(scriptEngine, "onUpdate(entity, executer) [server]")
                || isScriptDisabled(scriptEngine, "onUpdate(entity, executer) [server-runtime]")) {
            return;
        }
        try {
            invocable.invokeFunction("onUpdate", entity, executer);
            if (executer != null) {
                ++executer.count;
            }
            noteScriptSuccess(scriptEngine, "onUpdate(entity, executer) [server]");
            noteScriptSuccess(scriptEngine, "onUpdate(entity, executer) [server-runtime]");
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (ScriptException e) {
            reportScriptFailure(scriptEngine, "onUpdate(entity, executer) [server]", e);
            return;
        } catch (Throwable t) {
            reportScriptFailure(scriptEngine, "onUpdate(entity, executer) [server-runtime]", t);
            return;
        }
        try {
            invocable.invokeFunction("onUpdate", entity);
            if (executer != null) {
                ++executer.count;
            }
        } catch (NoSuchMethodException ignored) {
        } catch (ScriptException e) {
            reportScriptFailure(scriptEngine, "onUpdate(entity) [server]", e);
        } catch (Throwable t) {
            reportScriptFailure(scriptEngine, "onUpdate(entity) [server-runtime]", t);
        }
    }

    public static void invokeScriptTick(ScriptEngine scriptEngine, Object entity) {
        if (scriptEngine == null) return;
        LegacyScriptExecutor compat = entity instanceof TrainEntity train ? new LegacyScriptExecutor(train) : null;
        try {
            scriptEngine.put("executer", compat);
            scriptEngine.put("executor", compat);
        } catch (Throwable ignored) {
        }
        if (entity instanceof TrainEntity train) {
            try {
                List<LegacyScriptExecutor> frontList = new ArrayList<>();
                TrainEntity cur = train.getCoupledLeader();
                while (cur != null && frontList.size() < 64) {
                    frontList.add(new LegacyScriptExecutor(cur));
                    cur = cur.getCoupledLeader();
                }
                List<LegacyScriptExecutor> rearList = new ArrayList<>();
                cur = train.getCoupledFollower();
                while (cur != null && rearList.size() < 64) {
                    rearList.add(new LegacyScriptExecutor(cur));
                    cur = cur.getCoupledFollower();
                }
                scriptEngine.put("__rtmFrontArr", frontList.toArray(new LegacyScriptExecutor[0]));
                scriptEngine.put("__rtmRearArr", rearList.toArray(new LegacyScriptExecutor[0]));
                scriptEngine.eval("frontSideTrainList = Array.from(__rtmFrontArr); rearSideTrainList = Array.from(__rtmRearArr);");
            } catch (Throwable ignored) {
            }
        }
        if (scriptEngine instanceof Invocable invocable) {
            try {
                invocable.invokeFunction("tick", entity);
                return;
            } catch (NoSuchMethodException ignored) {
                // no tick function
            } catch (ScriptException e) {
                reportScriptFailure(scriptEngine, "tick(entity)", e);
                return;
            }
            try {
                invocable.invokeFunction("tick");
                return;
            } catch (NoSuchMethodException ignored) {
                // no zero-arg tick function
            } catch (ScriptException e) {
                reportScriptFailure(scriptEngine, "tick()", e);
                return;
            }
            if (compat != null) {
                try {
                    invocable.invokeFunction("onUpdate", compat);
                    return;
                } catch (NoSuchMethodException ignored) {
                    // no one-argument compat onUpdate
                } catch (ScriptException e) {
                    reportScriptFailure(scriptEngine, "onUpdate(compat)", e);
                    return;
                }
                try {
                    invocable.invokeFunction("onUpdate", entity, compat);
                    return;
                } catch (NoSuchMethodException ignored) {
                    // no two-argument onUpdate
                    } catch (ScriptException e) {
                        reportScriptFailure(scriptEngine, "onUpdate(entity, compat)", e);
                        return;
                    }
                }
            try {
                invocable.invokeFunction("onUpdate", entity);
                return;
            } catch (NoSuchMethodException ignored) {
                // no one-argument entity onUpdate
            } catch (ScriptException e) {
                reportScriptFailure(scriptEngine, "onUpdate(entity)", e);
                return;
            }
        }
        try {
            scriptEngine.put("__ptTickEntity", entity);
            scriptEngine.put("__ptCompat", compat);
            scriptEngine.eval(
                "if (typeof tick === 'function') tick(__ptTickEntity);" +
                " else if (typeof onUpdate === 'function') {" +
                "   if (__ptCompat != null) {" +
                "     try { onUpdate(__ptCompat); } catch (e1) {" +
                "       try { onUpdate(__ptTickEntity, __ptCompat); } catch (e2) { onUpdate(__ptTickEntity); }" +
                "     }" +
                "   } else { onUpdate(__ptTickEntity); }" +
                " }"
            );
        } catch (ScriptException e) {
            reportScriptFailure(scriptEngine, "tick/onUpdate fallback", e);
        }
    }

    /** サウンドスクリプトを1tick回す。SoundScriptExecutorを必ず渡す。 */
    public static void invokeSoundScript(ScriptEngine scriptEngine, net.minecraft.world.entity.Entity train) {
        if (scriptEngine == null || train == null) {
            return;
        }
        SoundScriptExecutor su = new SoundScriptExecutor(train);
        try {
            scriptEngine.put("executer", su);
            scriptEngine.put("executor", su);
            // 本家では su がグローバルにも見える。onUpdate を引数なしで定義したり、
            // ヘルパー関数が引数で受けずグローバル su を参照するスクリプト
            // (ReferenceError: "su" is not defined) のために必ず束縛する。
            scriptEngine.put("su", su);
        } catch (Throwable ignored) {
        }
        if (!(scriptEngine instanceof Invocable invocable)) {
            return;
        }
        // 本家 SoundUpdaterVehicle.update:45 は 1 本だけ:
        // ScriptUtil.doScriptIgnoreError(modelset.se, "onUpdate", this);
        // update(su,1.0F) / tick(su) というフックは本家に存在しないため持たない。
        // ★止めてあるものは呼ばない。
        if (isScriptDisabled(scriptEngine, "onUpdate(su) [sound]")) {
            return;
        }
        try {
            invocable.invokeFunction("onUpdate", su);
            noteScriptSuccess(scriptEngine, "onUpdate(su) [sound]");
        } catch (NoSuchMethodException ignored) {
            // onUpdate を持たないサウンドスクリプト = 本家では何も鳴らない
        } catch (Throwable e) {
            reportScriptFailure(scriptEngine, "onUpdate(su) [sound]", e);
        }
    }

    /**
     * RS ブロック (RTMU 独自のスクリプトブロック) 用。本家 RTM には無い機能。
     * 関数呼び出しではなくソース全体を毎回 eval する。
     */
    public boolean executeBlockScript(ServerLevel level, BlockPos pos, String script, boolean powered, TrainEntity train) {
        if (engine == null || script == null || script.isBlank()) {
            return false;
        }
        try {
            Bindings bindings = engine.createBindings();
            bindings.put("level", level);
            bindings.put("world", level);
            bindings.put("pos", pos.immutable());
            bindings.put("x", pos.getX());
            bindings.put("y", pos.getY());
            bindings.put("z", pos.getZ());
            bindings.put("powered", powered);
            bindings.put("redstone", level.getBestNeighborSignal(pos));
            bindings.put("train", train);
            bindings.put("currentTrain", train);
            bindings.put("logger", RealTrainModUnofficial.LOGGER);
            engine.eval(script, bindings);
            return true;
        } catch (ScriptException e) {
            RealTrainModUnofficial.LOGGER.error("Script block execution error at {}", pos, e);
            return false;
        }
    }

    public static final class BogieCompat {
        public float field_70177_z;
        public float field_70125_A;

        BogieCompat(float yRot, float xRot) {
            this.field_70177_z = yRot;
            this.field_70125_A = xRot;
        }
    }

    public static final class LegacyScriptExecutor {
        private final TrainEntity train;
        public final long count;
        // RTM 1.12.2 obfuscated field names used by legacy render scripts
        public final float field_70177_z; // yRot
        public final float field_70125_A; // xRot
        public final int field_70173_aa; // tickCount
        public final net.minecraft.world.entity.Entity field_70153_n; // riding/driver compat
        // Door animation state (accessed as properties by legacy scripts)
        public final float doorMoveL;
        public final float doorMoveR;
        public final float seatRotation;
        public final float pantograph_F;
        public final float pantograph_B;
        // Brake pressure fields used by sd8200-style scripts for gauge animation
        public final float brakeCount;
        public final float brakeAirCount;
        // RTM 1.7/1.12.2 legacy coordinate fields
        public final double xCoord;
        public final double yCoord;
        public final double zCoord;
        // Wheel rotation in degrees (accessed as entity.wheelRotationR in scripts)
        public final float wheelRotationR;
        // RTM 1.7.10 obfuscated world field (entity.field_70170_p)。
        // entity.field_70170_p.field_72995_K(world.isRemote) を参照するため公開する。
        // ★本物のシム (jp.ngt.mccompat.WorldCompat) を渡す。
        public final jp.ngt.mccompat.WorldCompat field_70170_p;

        public LegacyScriptExecutor(TrainEntity train) {
            this.train = train;
            this.field_70170_p = train != null && train.level() != null
                ? new jp.ngt.mccompat.WorldCompat(train.level()) : null;
            this.count = train == null ? 0L : Math.max(0L, (long) train.tickCount);
            this.field_70177_z = train == null ? 0.0F : train.getYRot();
            this.field_70125_A = train == null ? 0.0F : train.getXRot();
            this.field_70173_aa = train == null ? 0 : train.tickCount;
            this.field_70153_n = resolvePrimaryPassenger(train);
            this.doorMoveL = train == null ? 0.0F : train.doorMoveL;
            this.doorMoveR = train == null ? 0.0F : train.doorMoveR;
            this.seatRotation = train == null ? 0.0F : train.getSeatRotation();
            this.pantograph_F = train == null ? 0.0F : train.pantograph_F;
            this.pantograph_B = train == null ? 0.0F : train.pantograph_B;
            // brakeCount: 0–8 equivalent brake notch position for gauge display
            this.brakeCount = train == null ? 0.0F : Math.max(0, -train.getNotch());
            // brakeAirCount: simulated MR pressure (starts at max 800, decreases with braking)
            this.brakeAirCount = train == null ? 800.0F : 800.0F - Math.max(0, -train.getNotch()) * 30.0F;
            this.xCoord = train == null ? 0.0 : train.getX();
            this.yCoord = train == null ? 0.0 : train.getY();
            this.zCoord = train == null ? 0.0 : train.getZ();
            // 走行距離ベースの累積回転角(TrainEntity.tick で毎tick加算)。
            // 旧 tickCount×速度 は速度変化で巨大ジャンプ→空転の原因だったので使わない。
            this.wheelRotationR = train == null ? 0.0F : train.getWheelRotationDegrees();
        }

        public long getCount() {
            return count;
        }

        public boolean once() {
            return count <= 0L;
        }

        public boolean every(long interval) {
            return interval > 0L && Math.floorMod(count, interval) == 0L;
        }

        public boolean between(long start, long endExclusive) {
            return count >= start && (endExclusive < 0L || count < endExclusive);
        }

        public boolean times(long maxCount) {
            return maxCount < 0L || count < maxCount;
        }

        public Object suggestState(Object value) {
            return value;
        }

        public Object suggestState(Object value, Object fallback) {
            return value == null ? fallback : value;
        }

        public float getSpeed() {
            return train == null ? 0.0F : train.getSpeed() * 72.0F;
        }

        public TrainEntity getEntity() {
            return train;
        }

        public TrainEntity getTrain() {
            return train;
        }

        public TrainEntity getVehicle() {
            return train;
        }

        public int getNotch() {
            return train == null ? 0 : train.getNotch();
        }

        public int getReverser() {
            return train == null ? 0 : train.getReverser();
        }

        public int getSound() {
            return train == null ? 0 : train.getSoundIndex();
        }

        public int getRollsign() {
            return train == null ? 0 : train.getDestinationIndex();
        }

        public boolean inTunnel() {
            return false;
        }

        public boolean isComplessorActive() {
            if (train == null) {
                return false;
            }
            float speedKmh = Math.abs(getSpeed());
            if (speedKmh > 2.0F || train.getNotch() > 0) {
                return false;
            }
            int cycle = Math.floorMod(train.tickCount, 240);
            return cycle < 55;
        }

        public int complessorCount() {
            if (!isComplessorActive()) {
                return 0;
            }
            return Math.floorMod(train.tickCount, 240);
        }

        public boolean isCompressorActive() {
            return isComplessorActive();
        }

        public int compressorCount() {
            return complessorCount();
        }

        public void playSound(String namespace, String soundName, double volume, double pitch) {
            invokeLegacySoundManager("play", namespace, soundName, (float) volume, (float) pitch, true);
        }

        public void playSound(String namespace, String soundName, double volume, double pitch, boolean looping) {
            invokeLegacySoundManager("play", namespace, soundName, (float) volume, (float) pitch, looping);
        }

        public void stopSound(String namespace, String soundName) {
            invokeLegacySoundManager("stop", namespace, soundName, 0.0F, 0.0F, false);
        }

        private void invokeLegacySoundManager(String method, String namespace, String soundName, float volume, float pitch, boolean looping) {
            if (train == null || !train.level().isClientSide()) {
                return;
            }
            try {
                Class<?> managerClass = Class.forName("com.portofino.realtrainmodunofficial.client.sound.LegacyScriptSoundManager");
                if ("play".equals(method)) {
                    managerClass.getMethod("play", net.minecraft.world.entity.Entity.class, String.class, String.class, float.class, float.class, boolean.class)
                        .invoke(null, train, namespace, soundName, volume, pitch, looping);
                } else {
                    managerClass.getMethod("stop", net.minecraft.world.entity.Entity.class, String.class, String.class)
                        .invoke(null, train, namespace, soundName);
                }
            } catch (Exception e) {
            }
        }

        // ---- TrainStateData (RTM 2.x API) ----

        public float getTrainStateData(int stateType) {
            return train == null ? 0.0F : train.getTrainStateData(stateType);
        }

        public float getVehicleState(int stateType) {
            return getTrainStateData(stateType);
        }

        public void setTrainStateData(int stateType, float value) {
            if (train == null) return;
            train.syncVehicleState(stateType, value);
        }

        public Object getData(Object key) {
            if (train == null || key == null) {
                return 0;
            }
            String value = train.getScriptDataValue(String.valueOf(key));
            if (value == null || value.isBlank()) {
                return 0;
            }
            try {
                if (value.indexOf('.') >= 0) {
                    return Double.parseDouble(value);
                }
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }

        public Object getData(long key) {
            return getData(Long.toString(key));
        }

        public Object getData(int key) {
            return getData(Integer.toString(key));
        }

        public void setData(Object key, Object value) {
            if (train == null || key == null) {
                return;
            }
            train.setScriptDataValue(String.valueOf(key), value == null ? "" : String.valueOf(value));
        }

        public void setData(long key, Object value) {
            setData(Long.toString(key), value);
        }

        public void setData(int key, Object value) {
            setData(Integer.toString(key), value);
        }

        // ---- RTM compat fields / methods ----

        public float getSeatRotation() {
            return train == null ? 0.0F : train.getSeatRotation();
        }

        /** 転換クロスシートの向き。0=前向き/1=後ろ向き。 */
        public float getSeatDirection() {
            return train == null ? 0.0F : train.getSeatDirection();
        }

        public Object getFormation() {
            return train == null ? null : train.getFormation();
        }

        public int func_145782_y() {
            return train == null ? 0 : train.getId();
        }

        public int func_70070_b() {
            return train == null ? 0x00F000F0 : train.func_70070_b();
        }

        public int func_70070_b(int ignored) {
            return func_70070_b();
        }

        // ---- Door state (RTM-compatible) ----
        // Returns: 0=all closed, 1=right open, 2=left open, 3=both open

        public int getDoorState() {
            if (train == null) return 0;
            return (train.isDoorRightOpen() ? 1 : 0) | (train.isDoorLeftOpen() ? 2 : 0);
        }

        public int getDoorState(int side) {
            if (train == null) return 0;
            return side == 0 ? (train.isDoorRightOpen() ? 1 : 0) : (train.isDoorLeftOpen() ? 1 : 0);
        }

        public boolean isDoorOpen() {
            return train != null && (train.isDoorRightOpen() || train.isDoorLeftOpen());
        }

        public boolean isDoorRightOpen() {
            return train != null && train.isDoorRightOpen();
        }

        public boolean isDoorLeftOpen() {
            return train != null && train.isDoorLeftOpen();
        }

        // ---- Custom buttons (RTM-compatible) ----

        public boolean getCustomButton(int index) {
            return train != null && train.isCustomButtonOn(index);
        }

        public int getCustomButtonValue(int index) {
            return train == null ? 0 : train.getCustomButtonValue(index);
        }

        public void setCustomButton(int index, boolean on) {
            if (train != null) train.setCustomButton(index, on);
        }

        public void setCustomButton(int index, int value) {
            if (train != null) train.setCustomButton(index, value != 0);
        }

        public void toggleCustomButton(int index) {
            if (train != null) train.toggleCustomButton(index);
        }

        // ---- Passengers / interior ----

        public boolean isInsideTrain() {
            return train != null && !train.getPassengers().isEmpty();
        }

        public boolean hasPassenger() {
            return isInsideTrain();
        }

        public int getPassengerCount() {
            return train == null ? 0 : train.getPassengers().size();
        }

        public net.minecraft.world.entity.Entity func_184207_aI() {
            return resolvePrimaryPassenger(train);
        }

        public java.util.List<net.minecraft.world.entity.Entity> func_184188_bt() {
            return train == null ? java.util.List.of() : train.getPassengers();
        }

        public net.minecraft.world.entity.Entity func_184187_bx() {
            return func_184207_aI();
        }

        private static net.minecraft.world.entity.Entity resolvePrimaryPassenger(TrainEntity train) {
            if (train == null) {
                return null;
            }
            return train.getPassengers().isEmpty() ? null : train.getPassengers().get(0);
        }

        // ---- Train dimensions ----

        public double getTrainLength() {
            return train == null ? 0.0 : train.getTrainDistance();
        }

        public double getTrainDistance() {
            return getTrainLength();
        }

        // ---- Bogie compat (RTM 1.12.2 entity.getBogie(n)) ----
        // Returns a BogieCompat with field_70177_z/field_70125_A so legacy scripts can read bogie yaw/pitch.

        public BogieCompat getBogie(int index) {
            if (train == null) return null;
            int mapped = train.scriptBogieIndexToDefinitionIndex(index);
            float yaw = train.getBogieWorldYaw(mapped);
            float pitch = train.getBogiePitch(mapped);
            return new BogieCompat(yaw, pitch);
        }

        // ---- Connected trains (formation) ----

        public TrainEntity getFrontTrain() {
            return train == null ? null : train.getCoupledLeader();
        }

        public TrainEntity getRearTrain() {
            return train == null ? null : train.getCoupledFollower();
        }

        public boolean hasFrontTrain() {
            return getFrontTrain() != null;
        }

        public boolean hasRearTrain() {
            return getRearTrain() != null;
        }

        // ---- Pantograph ----

        public boolean isPantographUp() {
            return train != null && train.isPantographUp();
        }

        public void setPantographUp(boolean up) {
            if (train != null) train.setPantographUp(up);
        }

        // ---- Light mode ----

        public int getLightMode() {
            return train == null ? 0 : train.getLightMode();
        }

        public void setLightMode(int mode) {
            if (train != null) train.setLightMode(mode);
        }

        // ---- Destination (rollsign) ----

        public int getDestinationIndex() {
            return train == null ? 0 : train.getDestinationIndex();
        }

        public void setDestinationIndex(int index) {
            if (train != null) train.setDestinationIndex(index);
        }

        // ---- Speed (raw m/tick) ----

        public float getRawSpeed() {
            return train == null ? 0.0F : train.getSpeed();
        }

        // ---- World access ----

        public Object getWorld() {
            return train == null ? null : train.level();
        }

        public double getX() { return train == null ? 0 : train.getX(); }
        public double getY() { return train == null ? 0 : train.getY(); }
        public double getZ() { return train == null ? 0 : train.getZ(); }
        public float getYaw() { return train == null ? 0 : train.getYRot(); }
        public float getPitch() { return train == null ? 0 : train.getXRot(); }
        // 旧 RTM Render スクリプトは entity.getRotation で yaw を取得する。
        public float getRotation() { return getYaw(); }

        // ---- Speed (km/h, RTM compat alias) ----

        public float getSpeedKmh() { return train == null ? 0.0F : Math.abs(train.getSpeed()) * 72.0F; }

        // ---- Formation passenger count ----

        public int getFormationPassengerCount() {
            if (train == null) return 0;
            int total = 0;
            for (TrainEntity t : train.getFormationTrainsForDisplay()) {
                total += t.getPassengers().size();
                // also count passengers on seat entities attached to this train
                for (net.minecraft.world.entity.Entity e : t.level().getEntitiesOfClass(
                        com.portofino.realtrainmodunofficial.entity.TrainSeatEntity.class,
                        t.getBoundingBox().inflate(20.0))) {
                    if (e instanceof com.portofino.realtrainmodunofficial.entity.TrainSeatEntity seat
                            && seat.getTrain() == t && !seat.getPassengers().isEmpty()) {
                        total += seat.getPassengers().size();
                    }
                }
            }
            return total;
        }

        // ---- Heading / direction ----

        public float getHeadingAngle() {
            return train == null ? 0.0F : train.getYRot();
        }

        // ---- RTM setTrainStateData (write from script) ----

        public void setSound(int index) {
            if (train != null) train.setSoundIndex(index);
        }

        public void setRollsign(int index) {
            if (train != null) train.setDestinationIndex(index);
        }

        // ---- hasIndirectPassenger guard (passenger count > 0) ----

        public boolean hasDriver() {
            if (train == null) return false;
            for (net.minecraft.world.entity.Entity e : train.getPassengers()) {
                if (e instanceof net.minecraft.world.entity.player.Player) return true;
            }
            return false;
        }

        // ---- Tick count helpers ----

        public long getTick() { return count; }
        public long getTime() { return count; }

        // ---- Resource state (RTM 1.12.2 entity.getResourceState.getDataMap) ----

        public com.portofino.realtrainmodunofficial.entity.TrainEntity.ResourceStateCompat getResourceState() {
            return train == null ? null : train.getResourceState();
        }

        // ---- Formation / coupler (RTM 1.12.2 entity.getTrainDirection etc.) ----

        public float getTrainDirection() {
            return train == null ? 0.0F : train.getTrainDirection();
        }

        public TrainEntity getConnectedTrain(int dir) {
            return train == null ? null : train.getConnectedTrain(dir);
        }

        public float getCouplerYaw(int index) {
            return train == null ? 0.0F : train.getCouplerYaw(index);
        }

        public int getRollsignAnimation() {
            return train == null ? 0 : train.getRollsignAnimation();
        }

        // ---- Notch sync (called from cab controller scripts) ----

        public void syncNotch(int notch) {
            if (train != null) train.syncNotch(notch);
        }

        public int getDir() {
            // For train entity: direction as 0/1. Use heading bucket for 4-way.
            if (train == null) return 0;
            float yaw = ((train.getYRot() % 360.0F) + 360.0F) % 360.0F;
            return Math.round(yaw / 90.0F) % 4;
        }

        public float getMoveDir() {
            return train == null ? 0.0F : Math.signum(train.getSpeed());
        }

        public float getAccelerationForward() { return 0.0F; }
        public float getAccelerationStrafe() { return 0.0F; }

        // ---- Ground check ----

        public boolean isOnGround() {
            return train != null && train.onGround();
        }

        // ---- 1.7.10 / 1.12.2 obfuscated Minecraft entity method stubs ----
        // These are called from RTM render/tick scripts that were written against old MC versions.

        // func_70089_S = isEntityAlive (1.7.10/1.12.2)
        public boolean func_70089_S() {
            return train != null && !train.isRemoved();
        }

        // func_70027_ad = isDead / isRemoved (1.7.10/1.12.2)
        public boolean func_70027_ad() {
            return train == null || train.isRemoved();
        }

        // func_70075_an = isInWater (1.7.10)
        public boolean func_70075_an() {
            return train != null && train.isInWater();
        }

        // func_70093_af = isSneaking (1.7.10/1.12.2)
        public boolean func_70093_af() {
            return false;
        }

        // func_70661_as = isSprinting (1.7.10/1.12.2)
        public boolean func_70661_as() {
            return false;
        }

        // func_70617_f_ = isInWater (1.12.2 variant)
        public boolean func_70617_f_() {
            return func_70075_an();
        }

        // func_70086_ai = isWet (1.7.10)
        public boolean func_70086_ai() {
            return train != null && train.isInWaterOrRain();
        }

        // func_70023_ah = isBurning (1.7.10)
        public boolean func_70023_ah() {
            return train != null && train.isOnFire();
        }

        // func_70040_Z = isSneaking variant (1.7.10)
        public boolean func_70040_Z() {
            return false;
        }

        // func_70003_b(int, String) = canUseCommand / hasPermissionLevel (1.12.2)
        public boolean func_70003_b(int level, String command) {
            return false;
        }

        // func_110140_aT = getAttributeMap (1.12.2) — return stub
        public Object func_110140_aT() {
            return null;
        }

        // func_70012_b(double,double,double,float,float) = setPositionAndRotation (1.7.10/1.12.2)
        public void func_70012_b(double x, double y, double z, float yaw, float pitch) {
            // no-op in render context
        }

        // func_70107_b(double,double,double) = setPosition (1.7.10)
        public void func_70107_b(double x, double y, double z) {
            // no-op in render context
        }

        // func_70030_z = preparePlayerToSpawn / onUpdate stub (1.7.10)
        public void func_70030_z() {}

        // func_70021_al = getPassengerList (1.7.10) — returns empty
        public Object[] func_70021_al() {
            return new Object[0];
        }

        // func_70678_g(float) = getEyeHeight variant (1.7.10)
        public float func_70678_g(float partialTick) {
            return 1.5F;
        }

        // func_70047_e = getEyeHeight (1.7.10/1.12.2)
        public float func_70047_e() {
            return 1.5F;
        }

        // func_70057_ab = getAir (1.7.10 variant)
        public int func_70057_ab() {
            return 300;
        }

        // func_70020_e = setAir (1.7.10)
        public void func_70020_e(int air) {}

        // Field equivalents often read as properties in JS
        // field_70128_N = isDead (1.7.10)
        public final boolean field_70128_N = false;

        // field_70179_y = motionX (1.7.10)
        public double field_70179_y() { return train == null ? 0 : train.getDeltaMovement().x; }
        // field_70181_x = motionY (1.7.10)
        public double field_70181_x() { return train == null ? 0 : train.getDeltaMovement().y; }
        // field_70178_ae = motionZ (1.7.10)
        public double field_70178_ae() { return train == null ? 0 : train.getDeltaMovement().z; }

        // func_145782_y is already implemented (getId / entityId)

        // ---- Weapon entity stubs (RenderTank, RenderPhalanx, etc.) ----

        public float getBarrelYaw() { return 0.0F; }
        public float getBarrelPitch() { return 0.0F; }
        public float getRecoil() { return 0.0F; }

        // ---- Installed-object random scale (RenderPalm etc.) ----

        public float getRandomScale() { return 1.0F; }

    }

    public static final class ScriptModelRenderer {
        private final Object model;
        private final MqoModelLoader.MqoModel mqoModel;
        private final String defaultModelName;
        private PoseStack poseStack;
        private MultiBufferSource buffer;
        private int packedLight;
        private int basePackedLight;
        private boolean lightmapMaxForced;
        private int overlay;
        private int currentPass;
        private Object currentEntity;
        private net.minecraft.resources.ResourceLocation boundTexture;
        private float colorRed = 1.0F;
        private float colorGreen = 1.0F;
        private float colorBlue = 1.0F;
        private float colorAlpha = 1.0F;
        private boolean uvWindowActive;
        private float uvU0;
        private float uvV0;
        private float uvU1 = 1.0F;
        private float uvV1 = 1.0F;
        private boolean uvOffsetActive;
        private float uvOffsetU;
        private float uvOffsetV;
        private int matrixDepth = 0;
        private Vec3 scriptLocalOrigin = Vec3.ZERO;
        private final ArrayDeque<Vec3> scriptLocalStack = new ArrayDeque<>();
        private int renderPartsCalls = 0;
        private int renderedBatchCount = 0;
        private final Set<String> scriptedOpaqueGroups = new LinkedHashSet<>();
        private final Set<String> scriptedTranslucentGroups = new LinkedHashSet<>();
        // emissive pass (pass>=2) の発光描画はライトON状態のときに行う別系統。
        // translucent pass (pass=1) の半透明描画とは独立に 1 フレーム 1 回制限する。
        private final Set<String> scriptedEmissiveGroups = new LinkedHashSet<>();
        // translucentを最初に描画したパスだけに限定するための記録(-1=未描画)
        private int firstTranslucentPass = -1;
        // Groups registered via registerParts during init — script "owns" these, baked render skips them
        private final Set<String> scriptRegisteredGroups = new LinkedHashSet<>();
        private final Map<Long, Object> scriptData = new HashMap<>();
        private TrainEntity cachedExecutorTrain;
        private LegacyScriptExecutor cachedExecutor;
        private boolean replayCacheDisabledForFrame = false;
        private final List<TessVertex> tessellatorVertices = new ArrayList<>();
        private float tessColorRed = 1.0F;
        private float tessColorGreen = 1.0F;
        private float tessColorBlue = 1.0F;
        private float tessColorAlpha = 1.0F;
        private float tessNormalX = 0.0F;
        private float tessNormalY = 1.0F;
        private float tessNormalZ = 0.0F;
        private boolean tessNormalSet = false;

        private static final class TessVertex {
            final float x;
            final float y;
            final float z;
            final float u;
            final float v;

            TessVertex(float x, float y, float z, float u, float v) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.u = u;
                this.v = v;
            }
        }

        // 半透明遅延描画: 半透明をキューに溜め、全parts終了後に一括描画して順序を正す
        private boolean deferTranslucent = false;
        private final List<DeferredTranslucent> deferredTranslucents = new ArrayList<>();

        private static final class DeferredTranslucent {
            final Set<String> groups;
            final int pass;
            final int packedLight;
            final int overlay;
            final float r, g, b, a;
            final net.minecraft.resources.ResourceLocation boundTexture;
            final org.joml.Matrix4f pose;
            final org.joml.Matrix3f normal;
            // UV ウィンドウ状態(方向幕など CustomAnimator が setUvWindow したまま renderParts する場合に保存)
            final boolean uvWindowActive;
            final float uvU0, uvV0, uvU1, uvV1;
            DeferredTranslucent(Set<String> groups, int pass, int packedLight, int overlay,
                                float r, float g, float b, float a,
                                net.minecraft.resources.ResourceLocation boundTexture,
                                org.joml.Matrix4f pose, org.joml.Matrix3f normal,
                                boolean uvWindowActive, float uvU0, float uvV0, float uvU1, float uvV1) {
                this.groups = groups; this.pass = pass; this.packedLight = packedLight; this.overlay = overlay;
                this.r = r; this.g = g; this.b = b; this.a = a; this.boundTexture = boundTexture;
                this.pose = pose; this.normal = normal;
                this.uvWindowActive = uvWindowActive;
                this.uvU0 = uvU0; this.uvV0 = uvV0; this.uvU1 = uvU1; this.uvV1 = uvV1;
            }
        }

        public void setDeferTranslucent(boolean v) {
            this.deferTranslucent = v;
            if (!v) this.deferredTranslucents.clear();
        }

        /** 溜めた半透明描画を「全不透明の後」に一括描画する。 */
        public void flushDeferredTranslucent(PoseStack poseStack, MultiBufferSource buffer) {
            if (deferredTranslucents.isEmpty() || mqoModel == null) return;
            boolean prevDefer = this.deferTranslucent;
            this.deferTranslucent = false; // flush 中は即描画
            // 現在のコンテキストを退避
            int sPass = this.currentPass, sLight = this.packedLight, sOverlay = this.overlay;
            float sr = this.colorRed, sg = this.colorGreen, sb = this.colorBlue, sa = this.colorAlpha;
            net.minecraft.resources.ResourceLocation sTex = this.boundTexture;
            boolean sUvActive = this.uvWindowActive;
            float sUu0 = this.uvU0, sUv0 = this.uvV0, sUu1 = this.uvU1, sUv1 = this.uvV1;
            try {
                for (DeferredTranslucent d : deferredTranslucents) {
                    this.currentPass = d.pass;
                    this.packedLight = d.packedLight;
                    this.overlay = d.overlay;
                    this.colorRed = d.r; this.colorGreen = d.g; this.colorBlue = d.b; this.colorAlpha = d.a;
                    this.boundTexture = d.boundTexture;
                    // UV ウィンドウを復元(方向幕など setUvWindow した状態でキューに入ったエントリのため)
                    this.uvWindowActive = d.uvWindowActive;
                    this.uvU0 = d.uvU0; this.uvV0 = d.uvV0; this.uvU1 = d.uvU1; this.uvV1 = d.uvV1;
                    poseStack.pushPose();
                    try {
                        poseStack.last().pose().set(d.pose);
                        poseStack.last().normal().set(d.normal);
                        mqoModel.renderNamedGroups(poseStack, buffer, d.packedLight, d.overlay, true, d.groups, this);
                    } finally {
                        poseStack.popPose();
                    }
                }
            } finally {
                // コンテキスト復元
                this.currentPass = sPass; this.packedLight = sLight; this.overlay = sOverlay;
                this.colorRed = sr; this.colorGreen = sg; this.colorBlue = sb; this.colorAlpha = sa;
                this.boundTexture = sTex;
                this.uvWindowActive = sUvActive;
                this.uvU0 = sUu0; this.uvV0 = sUv0; this.uvU1 = sUu1; this.uvV1 = sUv1;
                this.deferTranslucent = prevDefer;
                deferredTranslucents.clear();
            }
        }

        /** 半透明描画を即時 or 遅延キューへ。戻り値 true=遅延した(即描画しない)。 */
        private boolean enqueueOrDrawTranslucent(PoseStack poseStack, MultiBufferSource buffer,
                                                 int pass, Set<String> groups) {
            if (!deferTranslucent) return false;
            org.joml.Matrix4f pose = new org.joml.Matrix4f(poseStack.last().pose());
            org.joml.Matrix3f normal = new org.joml.Matrix3f(poseStack.last().normal());
            deferredTranslucents.add(new DeferredTranslucent(
                new LinkedHashSet<>(groups), pass, this.packedLight, this.overlay,
                this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha,
                this.boundTexture, pose, normal,
                this.uvWindowActive, this.uvU0, this.uvV0, this.uvU1, this.uvV1));
            return true;
        }

        // Script Replay Cache: 状態が同じフレームは記録したOp列を再生しJS実行を省く
        public static final int OP_TRANSLATE = 1;
        public static final int OP_ROTATE_AXIS = 2;   // rotate(angle, axis, x, y, z)
        public static final int OP_ROTATE_FREE = 3;   // rotate(angle, x, y, z)
        public static final int OP_PUSH = 4;
        public static final int OP_POP = 5;
        public static final int OP_SCALE = 6;
        public static final int OP_RENDER_PARTS = 7;
        public static final int OP_SET_COLOR = 8;
        public static final int OP_RESET_COLOR = 9;
        public static final int OP_BIND_TEX = 10;
        public static final int OP_CLEAR_TEX = 11;
        public static final int OP_SET_UV_WINDOW = 12;
        public static final int OP_CLEAR_UV_WINDOW = 13;
        public static final int OP_SET_LIGHTMAP_MAX = 14;
        public static final int OP_BIND_LEGACY_ROLLSIGN = 15;
        public static final int OP_RENDER_TEXTURE_WINDOW = 16;
        public static final int OP_ENABLE_LIGHTING = 17;
        public static final int OP_SET_BRIGHTNESS = 18;

        public static final class OpList {
            int[] kinds = new int[32];
            float[] floats = new float[32 * 5];
            String[] strings = new String[32];
            char[] chars = new char[32];
            int size = 0;
            void add(int kind, float f0, float f1, float f2, float f3, float f4, String s, char c) {
                if (size == kinds.length) {
                    int n = size * 2;
                    kinds = java.util.Arrays.copyOf(kinds, n);
                    floats = java.util.Arrays.copyOf(floats, n * 5);
                    strings = java.util.Arrays.copyOf(strings, n);
                    chars = java.util.Arrays.copyOf(chars, n);
                }
                kinds[size] = kind;
                int b = size * 5;
                floats[b] = f0; floats[b+1] = f1; floats[b+2] = f2; floats[b+3] = f3; floats[b+4] = f4;
                strings[size] = s;
                chars[size] = c;
                size++;
            }
            void clear() { size = 0; }
        }

        // (passKey, stateHash) → 録画。LinkedHashMap で access order LRU 制限 (メモリ上限)。
        private static final int REPLAY_CACHE_MAX = 256;
        private final Map<Long, OpList> replayCache = new java.util.LinkedHashMap<>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, OpList> e) {
                return size() > REPLAY_CACHE_MAX;
            }
        };
        private OpList currentRecording;  // null = recording off
        private boolean replaying = false;
        private long currentSignature = 0L;

        public boolean isReplaying() { return replaying; }

        private void recordOp(int kind, float f0, float f1, float f2, float f3, float f4, String s, char c) {
            if (currentRecording != null) {
                currentRecording.add(kind, f0, f1, f2, f3, f4, s, c);
            }
        }

        /** pass+entity状態をlongに圧縮。分岐に影響する値のみ含める。 */
        public long computeReplaySignature(int pass, Object entity) {
            if (!(entity instanceof TrainEntity t)) return 0L;
            if (Math.abs(t.getSpeed()) > 0.001F) {
                return 0L;
            }
            int doorL = Math.round(t.doorMoveL * 32.0F);
            int doorR = Math.round(t.doorMoveR * 32.0F);
            int lightMode = t.getLightMode();
            int notch = t.getNotch();
            int rev = t.getReverser();
            int dest = t.getDestinationIndex();
            int interior = t.isInteriorLightOn() ? 1 : 0;
            long h = pass;
            h = h * 31 + doorL;
            h = h * 31 + doorR;
            h = h * 31 + lightMode;
            h = h * 31 + notch;
            h = h * 31 + rev;
            h = h * 31 + dest;
            h = h * 31 + interior;
            return h == 0L ? 1L : h;
        }

        public boolean tryReplayCachedScript(long signature) {
            if (signature == 0L) return false;
            OpList list = replayCache.get(signature);
            if (list == null) return false;
            replaying = true;
            try {
                executeOpList(list);
            } finally {
                replaying = false;
            }
            return true;
        }

        public void beginRecording(long signature) {
            replayCacheDisabledForFrame = false;
            if (signature == 0L) {
                currentRecording = null;
                currentSignature = 0L;
                return;
            }
            currentSignature = signature;
            OpList existing = replayCache.get(signature);
            if (existing == null) {
                existing = new OpList();
            } else {
                existing.clear();
            }
            currentRecording = existing;
        }

        public void endRecording(boolean keep) {
            if (currentRecording != null && keep && currentSignature != 0L && !replayCacheDisabledForFrame) {
                replayCache.put(currentSignature, currentRecording);
            }
            currentRecording = null;
            currentSignature = 0L;
            replayCacheDisabledForFrame = false;
        }

        public void disableReplayCacheForFrame() {
            replayCacheDisabledForFrame = true;
            currentRecording = null;
        }

        private void executeOpList(OpList list) {
            for (int i = 0; i < list.size; i++) {
                int k = list.kinds[i];
                int b = i * 5;
                float f0 = list.floats[b], f1 = list.floats[b+1], f2 = list.floats[b+2], f3 = list.floats[b+3], f4 = list.floats[b+4];
                String s = list.strings[i];
                switch (k) {
                    case OP_TRANSLATE -> translate(f0, f1, f2);
                    case OP_ROTATE_AXIS -> rotate(f0, String.valueOf(list.chars[i]), f1, f2, f3);
                    case OP_ROTATE_FREE -> rotate(f0, f1, f2, f3);
                    case OP_PUSH -> pushMatrix();
                    case OP_POP -> popMatrix();
                    case OP_SCALE -> scale(f0, f1, f2);
                    case OP_RENDER_PARTS -> renderParts(s);
                    case OP_SET_COLOR -> setColor(f0, f1, f2, f3);
                    case OP_RESET_COLOR -> resetColor();
                    case OP_BIND_TEX -> bindScriptTextureFromRecord(s, Math.round(f0));
                    case OP_CLEAR_TEX -> clearScriptTexture();
                    case OP_SET_UV_WINDOW -> setUvWindow(f0, f1, f2, f3);
                    case OP_CLEAR_UV_WINDOW -> clearUvWindow();
                    case OP_SET_LIGHTMAP_MAX -> setLightmapMaxBrightness();
                    case OP_SET_BRIGHTNESS -> setBrightness(Math.round(f0));
                    case OP_ENABLE_LIGHTING -> enableLighting();
                    case OP_RENDER_TEXTURE_WINDOW -> renderTextureWindowFromRecord(s, Math.round(f0), f1, f2, f3, f4);
                    default -> { /* skip */ }
                }
            }
        }

        private void bindScriptTextureFromRecord(String record, int frameIndex) {
            if (record == null || record.isBlank()) {
                clearScriptTexture();
                return;
            }
            String[] parts = record.split("\\n", 2);
            if (parts.length < 2 || parts[1].isBlank()) {
                clearScriptTexture();
                return;
            }
            bindScriptTexture(parts[0], parts[1], frameIndex);
        }

        // renderParts入力→解析結果のキャッシュ。同一String前提でヒット率ほぼ100%
        private final Map<String, ParsedGroupSet> renderPartsParseCache = new HashMap<>();

        // emissive pass で、lightMode ごとの presentGroupNames をキャッシュ。
        // pass 2 の renderParts ごとに 3 コレクション確保していたのを排除する。

        // isEmissiveGroup のコンパイル済み regex (毎回コンパイルするコストを排除)
        private static final java.util.regex.Pattern DEST_N_PATTERN = java.util.regex.Pattern.compile("dest\\d+");
        private static final java.util.regex.Pattern TYPE_N_PATTERN = java.util.regex.Pattern.compile("type\\d+");

        private static final class ParsedGroupSet {
            final List<String> filteredGroupNames;   // shadow/guide 除外後の元名前列
            final Set<String> normalizedNames;       // 正規化(lowercase trim) 後
            final Set<String> presentGroupNames;     // モデルに実在する正規化名
            final boolean legacyDisplaySelection;
            final boolean empty;
            final boolean hasAnyEmissiveGroup;       // filteredGroupNames に emissive が 1 つでもあるか
            ParsedGroupSet(List<String> filtered, Set<String> normalized, Set<String> present, boolean legacy, boolean hasEmissive) {
                this.filteredGroupNames = filtered;
                this.normalizedNames = normalized;
                this.presentGroupNames = present;
                this.legacyDisplaySelection = legacy;
                this.empty = filtered.isEmpty();
                this.hasAnyEmissiveGroup = hasEmissive;
            }
        }
        public final ScriptModelRenderer renderer = this;
        // RTM NGTRenderer/GLHelper aliases — scripts import these as classes then call directly
        public final ScriptModelRenderer NGTRenderer = this;
        // NPC biped animation angles (set by setRotationAngles, read by rotateAndRender)
        public float headAngleX, headAngleY, headAngleZ;
        public float bodyAngleX, bodyAngleY, bodyAngleZ;
        public float rightArmAngleX, rightArmAngleY, rightArmAngleZ;
        public float leftArmAngleX, leftArmAngleY, leftArmAngleZ;
        public float rightLegAngleX, rightLegAngleY, rightLegAngleZ;
        public float leftLegAngleX, leftLegAngleY, leftLegAngleZ;

        public ScriptModelRenderer(Object model, String defaultModelName) {
            this.model = model;
            this.mqoModel = model instanceof MqoModelLoader.MqoModel m ? m : null;
            this.defaultModelName = defaultModelName == null ? "" : defaultModelName;
        }

        // ---- 実 PartsRenderer と同じ API (経路によって欠けないようにする) ----
        // 車両描画は VehicleScriptRenderers (実 jp.ngt.rtm.render.PartsRenderer) を通るが、
        // モデル添付スクリプトはこちらを通る。

        /** 本家 preRender: 既定は何もしない。 */
        public void preRender(Object t, boolean smoothing, boolean culling, float partialTick) {
        }

        /** 本家 postRender: 既定は何もしない。 */
        public void postRender(Object t, boolean smoothing, boolean culling, float partialTick) {
        }

        /** 本家 getViewerVec: 対象座標から視点へのベクトル。 */
        public static jp.ngt.ngtlib.math.Vec3 getViewerVec(double x, double y, double z) {
            return jp.ngt.rtm.render.PartsRenderer.getViewerVec(x, y, z);
        }

        /** 本家 validPath: そのアセットが存在するか。 */
        public boolean validPath(String path) {
            return path != null && !path.isBlank()
                    && jp.ngt.ngtlib.io.NGTFileLoader.findAsset(path) != null;
        }

        /** 本家 renderLightEffectS: ボリュームライトは未対応 (安全に無視)。 */
        public static void renderLightEffectS(Object normal, double x, double y, double z,
                                              float rL, float rS, float length,
                                              int color, int type, boolean reverse) {
        }

        /** 本家 spawnParticle。 */
        public void spawnParticle(Object entity, String name, double posX, double posY, double posZ,
                                  double speedX, double speedY, double speedZ) {
            net.minecraft.world.entity.Entity e =
                    jp.ngt.mccompat.EntityCompatUtil.unwrapEntity(entity);
            if (e != null && e.level().isClientSide()) {
                e.level().addParticle(jp.ngt.mccompat.EnumParticleTypes.particleByLegacyName(name),
                        posX, posY, posZ, speedX, speedY, speedZ);
            }
        }

        public void debug(String msg) {
            jp.ngt.ngtlib.io.NGTLog.debug(msg);
        }

        /** 本家 getModelObj (フィールド名アクセス用の getter)。 */
        public Object getModelObj() {
            return this.getModelObject();
        }

        public Object getModel() {
            if (mqoModel != null) {
                return mqoModel.getScriptModel();
            }
            return model;
        }

        /** Returns the model object expected by legacy render scripts. */
        public Object getModelObject() {
            return getModel();
        }

        /** Returns the model object as a legacy model-set placeholder. */
        public Object getModelSet() {
            return getModel();
        }

        public Object registerParts(Object parts) {
            List<String> names = extractGroupNames(parts);
            List<String> usable = new ArrayList<>();
            int rejected = 0;
            if (names != null) {
                for (String name : names) {
                    String normalized = normalizeLegacyGroupName(name);
                    if (!normalized.isEmpty() && mqoModel != null && mqoModel.hasGroupNamed(normalized)) {
                        scriptRegisteredGroups.add(normalized);
                        usable.add(name);
                    } else {
                        rejected++;
                    }
                }
            }
            // RTM 原作の Parts は .render(renderer) で対応グループを描画する。
            // ここで実用版 ScriptParts を返し、bogieF.render(renderer) 等が機能するようにする。
            return new ScriptParts(this, usable);
        }

        /** スクリプトが bogieF.render(renderer) を呼ぶと現在の poseStack で描画する。 */
        public static final class ScriptParts {
            private final ScriptModelRenderer renderer;
            private final List<String> groupNames;

            public ScriptParts(ScriptModelRenderer renderer, List<String> groupNames) {
                this.renderer = renderer;
                this.groupNames = groupNames == null ? List.of() : List.copyOf(groupNames);
            }

            public List<String> getGroupNames() {
                return groupNames;
            }

            /** RTM 原作互換: 与えた renderer の現在の poseStack でグループを描画する。 */
            public void render(Object rendererArg) {
                ScriptModelRenderer target = (rendererArg instanceof ScriptModelRenderer smr) ? smr : renderer;
                if (target != null) {
                    target.renderRegisteredGroups(groupNames);
                }
            }

            /** 引数なし変種 (一部 RTM スクリプトで使われる) */
            public void render() {
                if (renderer != null) {
                    renderer.renderRegisteredGroups(groupNames);
                }
            }

            /** 本家Parts.getObjects互換。PolygonModelからグループ名一致のGroupObjectを返す。 */
            public jp.ngt.ngtlib.renderer.model.GroupObject[] getObjects(Object model) {
                if (!(model instanceof jp.ngt.ngtlib.renderer.model.PolygonModel pm)) {
                    return new jp.ngt.ngtlib.renderer.model.GroupObject[0];
                }
                java.util.List<jp.ngt.ngtlib.renderer.model.GroupObject> found = new java.util.ArrayList<>();
                for (String name : this.groupNames) {
                    if (name == null) {
                        continue;
                    }
                    for (jp.ngt.ngtlib.renderer.model.GroupObject obj : pm.groupObjects) {
                        if (name.equals(obj.name) || name.equalsIgnoreCase(obj.name)) {
                            found.add(obj);
                            break;
                        }
                    }
                }
                return found.toArray(new jp.ngt.ngtlib.renderer.model.GroupObject[0]);
            }
        }

        /** 指定グループ名のリストを現在の poseStack で描画する。 */
        /** 台車・車輪(走り装置)グループ名か。.class台車車両でスクリプト描画を抑制する判定用。 */
        private static boolean isBogieGroupName(String x) {
            if (x == null) return false;
            return x.contains("bogie") || x.contains("truck") || x.contains("wheel")
                || x.contains("daisya") || x.contains("sharin") || x.contains("台車") || x.contains("車輪");
        }

        public void renderRegisteredGroups(List<String> rawNames) {
            if (rawNames == null || rawNames.isEmpty() || mqoModel == null
                || poseStack == null || buffer == null) {
                return;
            }
            renderPartsCalls++;
            java.util.Set<String> normalized = new java.util.LinkedHashSet<>();
            for (String n : rawNames) {
                String x = normalizeLegacyGroupName(n);
                if (x.isEmpty()) continue;
                // 擬似シャドウ(黒平板)を無効化。shadow完全一致のみ
                if (x.equalsIgnoreCase("shadow")) continue;
                if (currentPass >= 2 && isLightOffGroup(x)) continue;
                normalized.add(x);
            }
            if (normalized.isEmpty()) return;
            if (currentPass >= 2) {
                normalized = filterLegacyScriptEmissiveGroups(normalized);
                if (normalized.isEmpty()) return;
            }
            // 連結曲げ用の角度変種フィルタ。0°本体があるときだけ曲げ変種を除外
            if (!shouldKeepNumberedVariantGroups(normalized)) {
                normalized = filterAngleVariantGroups(normalized);
            }
            if (normalized.isEmpty()) return;
            try {
                int renderPackedLight = effectivePackedLightForScriptParts(normalized);
                // RTM 本家の vehicle script は pass0=不透明(alpha==255)、pass1=半透明(alpha<255)。
                // ここで pass1 でも不透明側を描いてしまうと、KQ のような AlphaBlend 車両で
                // 窓/ガラス用グループの不透明マスクが床下に黒板のように残る。
                if (currentPass != 1) {
                    mqoModel.renderNamedGroups(poseStack, buffer, renderPackedLight, overlay, false, normalized, this);
                    scriptedOpaqueGroups.addAll(normalized);
                }

                if (currentPass >= 2) {
                    // emissive pass: renderSelectedBatches が「emissiveTexture を持たない batch」を
                    // スキップするので、 ここでは発光マテリアル (Light) のみ描画される (前照灯/室内灯)。
                    // 半透明発光も「全不透明の後」に描くため遅延キューへ。
                    if (!enqueueOrDrawTranslucent(poseStack, buffer, currentPass, normalized)) {
                        mqoModel.renderNamedGroups(poseStack, buffer, renderPackedLight, overlay, true, normalized, this);
                    }
                    scriptedTranslucentGroups.addAll(normalized);
                } else {
                    // 通常 translucent (AlphaBlend = 車体/窓/内装) は本家RTMと同様に pass1 でだけ描く。
                    // pass0 では opaqueTexture 側だけ、pass1 では windowTexture 側だけ出す。
                    if (currentPass == 1) {
                        if (!enqueueOrDrawTranslucent(poseStack, buffer, currentPass, normalized)) {
                            mqoModel.renderNamedGroups(poseStack, buffer, renderPackedLight, overlay, true, normalized, this);
                        }
                        scriptedTranslucentGroups.addAll(normalized);
                    }
                }
                // replay キャッシュに記録する。記録しないと2フレーム目以降は JS を skip した結果
                // この描画呼び出しも再現されず、scriptedOpaqueGroups が空のままになり
                // baked filter が動かず全 body 変形が重なって描画される。
                if (!replaying) {
                    String joined = String.join(",", normalized);
                    recordOp(OP_RENDER_PARTS, 0, 0, 0, 0, 0, joined, ' ');
                }
            } catch (Throwable ignored) {
                // 個別グループの描画失敗で他の処理を巻き込まない
            }
        }

        // 角度サフィックスのしきい値。これ以上の「-数字」は連結曲げ用の角度バリアント(度数)、
        // これ未満 (1〜9) は車体のセクション分割 ( の body-1/2/3 等) とみなす。
        private static final int ANGLE_SUFFIX_THRESHOLD = 10;

        /** 連結曲げ用の角度/ミラー変種を除外する。角度数字付きだけを落とし、(mx)鏡像とセクションは残す。 */
        private java.util.Set<String> filterAngleVariantGroups(java.util.Set<String> names) {
            // 曲げ変種(角度数字付き)は描かない。素/(mx)鏡像/セクションのみ残す
            java.util.Set<String> out = new java.util.LinkedHashSet<>(names.size());
            for (String n : names) {
                if (!isBendVariant(n)) {
                    out.add(n);
                }
            }
            return out;
        }

        private static String stripMirrorSuffix(String n) {
            // 末尾括弧が鏡像/向きトークンなら全て剥がしてから角度判定する
            String s = n;
            while (s.length() >= 3 && s.endsWith(")")) {
                int open = s.lastIndexOf('(');
                if (open <= 0 || open >= s.length() - 1) break;
                String inside = s.substring(open + 1, s.length() - 1);
                if (inside.isEmpty()) break;
                boolean mirrorToken = true;
                for (int i = 0; i < inside.length(); i++) {
                    char c = inside.charAt(i);
                    if (c != 'm' && c != 'x' && c != 'y' && c != 'z' && c != 'r') {
                        mirrorToken = false;
                        break;
                    }
                }
                if (!mirrorToken) break;
                s = s.substring(0, open);
            }
            return s;
        }

        /** 末尾「-数字」の数値を返す ((mx) は先に剥がす)。無ければ -1。 */
        private static int angleSuffixValue(String n) {
            String s = stripMirrorSuffix(n);
            int dash = s.lastIndexOf('-');
            if (dash <= 0 || dash == s.length() - 1) return -1;
            for (int i = dash + 1; i < s.length(); i++) {
                if (!Character.isDigit(s.charAt(i))) return -1;
            }
            try {
                return Integer.parseInt(s.substring(dash + 1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        /** 角度サフィックス(-NN, NN≥THRESHOLD)を持つ → 連結曲げ用変種。角度の無い (mx) 鏡像は対象外。 */
        private static boolean isBendVariant(String n) {
            return angleSuffixValue(n) >= ANGLE_SUFFIX_THRESHOLD;
        }

        private boolean shouldKeepNumberedVariantGroups(java.util.Set<String> names) {
            if (!(currentEntity instanceof TrainEntity train)) {
                return false;
            }
            String id = train.getVehicleId();
            if (id == null) {
                return false;
            }
            String lowerId = id.toLowerCase(Locale.ROOT);
            // TTP/TkmTP は body-35/body-90/common-35/bogie-35 等を曲線用の捨てパーツではなく
            // 実モデルの分割部品として使う車両が多い。ここで落とすと車体の大半が消えて
            // スクリプト部品だけが残るため、TTP 全体では番号付きグループをそのまま描く。
            return lowerId.startsWith("ttp_");
        }

        public boolean hasAlphaPassContent() {
            if (mqoModel == null) {
                return true;
            }
            if (!scriptRegisteredGroups.isEmpty()) {
                return true;
            }
            return mqoModel.hasTranslucentBatches()
                || mqoModel.hasGroupNamed("alpha")
                || mqoModel.hasGroupNamed("doorFL1")
                || mqoModel.hasGroupNamed("doorFR1")
                || mqoModel.hasGroupNamed("doorBL1")
                || mqoModel.hasGroupNamed("doorBR1");
        }

        public boolean hasEmissivePassContent() {
            if (mqoModel == null) {
                return true;
            }
            if (!scriptRegisteredGroups.isEmpty()) {
                return true;
            }
            return mqoModel.hasGroupNamed("light")
                || mqoModel.hasGroupNamed("lightF")
                || mqoModel.hasGroupNamed("lightB")
                || mqoModel.hasGroupNamed("ExLightF")
                || mqoModel.hasGroupNamed("ExLightB")
                || mqoModel.hasGroupNamed("ElightF")
                || mqoModel.hasGroupNamed("ElightB")
                || mqoModel.hasGroupNamed("dest")
                || mqoModel.hasGroupNamed("type")
                || mqoModel.hasLegacyLightTextures();
        }

        public TrainEntity.ConfigCompat getConfig() {
            if (currentEntity instanceof TrainEntity train) {
                return train.getResourceState().getResourceSet().getConfig();
            }
            return new TrainEntity.ConfigCompat(defaultModelName);
        }

        public String getResourceName() {
            if (currentEntity instanceof TrainEntity train) {
                return train.getVehicleId();
            }
            return defaultModelName.isBlank() ? "train" : defaultModelName;
        }

        public String getModelName() {
            if (currentEntity instanceof TrainEntity train) {
                return train.getVehicleId();
            }
            if (currentEntity instanceof InstalledObjectBlockEntity blockEntity) {
                return blockEntity.getModelName();
            }
            return defaultModelName;
        }

        public void setRenderContext(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int overlay, int pass, Object entity) {
            restoreMatrixDepth(0);
            this.poseStack = poseStack;
            this.buffer = buffer;
            // pass2+でもパス全体はフルブライトにしない(明示指定と発光バッチのみ)
            this.packedLight = packedLight;
            this.basePackedLight = packedLight;
            this.lightmapMaxForced = false;
            this.overlay = overlay;
            this.currentPass = pass;
            this.currentEntity = entity;
            this.boundTexture = null;
            resetColor();
            clearUvWindow();
            clearUvOffset();
            this.matrixDepth = 0;
            this.scriptLocalOrigin = Vec3.ZERO;
            this.scriptLocalStack.clear();
        }

        public void clearRenderContext() {
            restoreMatrixDepth(0);
            this.poseStack = null;
            this.buffer = null;
            this.currentEntity = null;
            this.boundTexture = null;
            this.currentPass = 0;
            resetColor();
            clearUvWindow();
            clearUvOffset();
            this.matrixDepth = 0;
            this.scriptLocalOrigin = Vec3.ZERO;
            this.scriptLocalStack.clear();
        }

        public Object scriptEntityFor(Object entity) {
            if (entity instanceof TrainEntity train) {
                // Legacy scripts read many values as plain fields (count, doorMoveL,
                // field_70173_aa, seatRotation, etc.). A cached wrapper freezes those
                // fields and stops tick-driven displays likerollsign/LCD.
                cachedExecutorTrain = train;
                cachedExecutor = new LegacyScriptExecutor(train);
                return cachedExecutor;
            }
            return entity;
        }

        public void setColor(double red, double green, double blue, double alpha) {
            this.colorRed = Mth.clamp((float) red, 0.0F, 1.0F);
            this.colorGreen = Mth.clamp((float) green, 0.0F, 1.0F);
            this.colorBlue = Mth.clamp((float) blue, 0.0F, 1.0F);
            this.colorAlpha = Mth.clamp((float) alpha, 0.0F, 1.0F);
            recordOp(OP_SET_COLOR, (float)red, (float)green, (float)blue, (float)alpha, 0, null, ' ');
        }

        public void resetColor() {
            this.colorRed = 1.0F;
            this.colorGreen = 1.0F;
            this.colorBlue = 1.0F;
            this.colorAlpha = 1.0F;
            recordOp(OP_RESET_COLOR, 0, 0, 0, 0, 0, null, ' ');
        }

        public int getColorRed255() {
            return Mth.clamp(Math.round(colorRed * 255.0F), 0, 255);
        }

        public int getColorGreen255() {
            return Mth.clamp(Math.round(colorGreen * 255.0F), 0, 255);
        }

        public int getColorBlue255() {
            return Mth.clamp(Math.round(colorBlue * 255.0F), 0, 255);
        }

        public int applyAlpha255(int alpha) {
            return Mth.clamp(Math.round(Mth.clamp(alpha, 0, 255) * colorAlpha), 0, 255);
        }

        public void resetRenderStatistics() {
            this.renderPartsCalls = 0;
            this.renderedBatchCount = 0;
            this.scriptedOpaqueGroups.clear();
            this.scriptedTranslucentGroups.clear();
            this.scriptedEmissiveGroups.clear();
            this.firstTranslucentPass = -1;
        }

        public int getRenderPartsCalls() {
            return renderPartsCalls;
        }

        public int getRenderedBatchCount() {
            return renderedBatchCount;
        }

        public int getCurrentPass() {
            return currentPass;
        }

        public Object getCurrentEntity() {
            return currentEntity;
        }

        public void onBatchRendered() {
            renderedBatchCount++;
        }

        public int currentMatId;

        public net.minecraft.resources.ResourceLocation getBoundTexture() {
            return boundTexture;
        }

        public void clearScriptTexture() {
            boundTexture = null;
            recordOp(OP_CLEAR_TEX, 0, 0, 0, 0, 0, null, ' ');
        }

        public void setUvWindow(double u0, double v0, double u1, double v1) {
            disableReplayCacheForFrame();
            uvWindowActive = true;
            uvU0 = (float) u0;
            uvV0 = (float) v0;
            uvU1 = (float) u1;
            uvV1 = (float) v1;
            recordOp(OP_SET_UV_WINDOW, uvU0, uvV0, uvU1, uvV1, 0, null, ' ');
        }

        public void clearUvWindow() {
            uvWindowActive = false;
            uvU0 = 0.0F;
            uvV0 = 0.0F;
            uvU1 = 1.0F;
            uvV1 = 1.0F;
            recordOp(OP_CLEAR_UV_WINDOW, 0, 0, 0, 0, 0, null, ' ');
        }

        /** UVウィンドウ/オフセットが有効か。有効フレームはCPU経路を使う。 */
        public boolean hasUvWindow() {
            return uvWindowActive || uvOffsetActive;
        }

        public void setUvOffset(double u, double v) {
            disableReplayCacheForFrame();
            uvOffsetActive = true;
            uvOffsetU = (float) u;
            uvOffsetV = (float) v;
        }

        public void clearUvOffset() {
            uvOffsetActive = false;
            uvOffsetU = 0.0F;
            uvOffsetV = 0.0F;
        }

        public float mapU(float u) {
            float result = uvOffsetActive ? u + uvOffsetU : u;
            return uvWindowActive ? uvU0 + (uvU1 - uvU0) * result : result;
        }

        public float mapV(float v) {
            float result = uvOffsetActive ? v + uvOffsetV : v;
            return uvWindowActive ? uvV0 + (uvV1 - uvV0) * result : result;
        }

        public float mapU(float u, float sourceMin, float sourceMax) {
            float result = uvOffsetActive ? u + uvOffsetU : u;
            if (!uvWindowActive) {
                return result;
            }
            float width = sourceMax - sourceMin;
            float normalized = Math.abs(width) < 1.0E-6F ? 0.5F : (result - sourceMin) / width;
            return uvU0 + (uvU1 - uvU0) * normalized;
        }

        public float mapV(float v, float sourceMin, float sourceMax) {
            float result = uvOffsetActive ? v + uvOffsetV : v;
            if (!uvWindowActive) {
                return result;
            }
            float height = sourceMax - sourceMin;
            float normalized = Math.abs(height) < 1.0E-6F ? 0.5F : (result - sourceMin) / height;
            return uvV0 + (uvV1 - uvV0) * normalized;
        }

        public void bindScriptTexture(String domain, String path, int frameIndex) {
            String safeDomain = domain == null || domain.isBlank() ? "minecraft" : domain;
            boundTexture = MqoModelLoader.getScriptTexture(safeDomain, path, frameIndex);
            if (path != null && !path.isBlank()) {
                recordOp(OP_BIND_TEX, frameIndex, 0, 0, 0, 0, safeDomain + "\n" + path, ' ');
            }
        }

        public List<Object> getScriptModelObjects(String groupsCsv) {
            return Collections.emptyList();
        }

        public void markScriptManagedParts(Object parts) {
            if (parts == null) {
                return;
            }
            List<String> groupNames = extractGroupNames(parts);
            for (String name : groupNames) {
                String normalized = normalizeLegacyGroupName(name);
                if (!normalized.isEmpty() && mqoModel != null && mqoModel.hasGroupNamed(normalized)) {
                    scriptRegisteredGroups.add(normalized);
                }
            }
        }

        public List<List<List<Double>>> getScriptQuadVertexLists(Object parts) {
            List<List<List<Double>>> out = new ArrayList<>();
            if (mqoModel == null || parts == null) return out;
            List<String> groupNames = extractGroupNames(parts);
            if (groupNames.isEmpty()) return out;
            Set<String> groups = new LinkedHashSet<>();
            for (String name : groupNames) {
                String normalized = normalizeLegacyGroupName(name);
                if (!normalized.isEmpty()) {
                    groups.add(normalized);
                }
            }
            if (groups.isEmpty()) return out;
            for (float[] q : mqoModel.getGroupQuadCorners(groups)) {
                if (q == null || q.length < 12) continue;
                q = sortQuadCornersForLegacyOverlay(q);
                List<List<Double>> face = new ArrayList<>(4);
                for (int i = 0; i < 4; i++) {
                    List<Double> vertex = new ArrayList<>(3);
                    vertex.add((double) q[i * 3]);
                    vertex.add((double) q[i * 3 + 1]);
                    vertex.add((double) q[i * 3 + 2]);
                    face.add(vertex);
                }
                out.add(face);
            }
            return out;
        }

        private static float[] sortQuadCornersForLegacyOverlay(float[] q) {
            float cx = 0.0F;
            float cy = 0.0F;
            float cz = 0.0F;
            for (int i = 0; i < 4; i++) {
                cx += q[i * 3];
                cy += q[i * 3 + 1];
                cz += q[i * 3 + 2];
            }
            cx *= 0.25F;
            cy *= 0.25F;
            cz *= 0.25F;

            float e1x = q[3] - q[0];
            float e1y = q[4] - q[1];
            float e1z = q[5] - q[2];
            float e2x = q[9] - q[0];
            float e2y = q[10] - q[1];
            float e2z = q[11] - q[2];
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1.0E-6F) {
                return q;
            }
            nx /= nl;
            ny /= nl;
            nz /= nl;

            float upRefX = 0.0F;
            float upRefY = 1.0F;
            float upRefZ = 0.0F;
            if (Math.abs(ny) > 0.98F) {
                upRefX = 0.0F;
                upRefY = 0.0F;
                upRefZ = 1.0F;
            }
            float rx = upRefY * nz - upRefZ * ny;
            float ry = upRefZ * nx - upRefX * nz;
            float rz = upRefX * ny - upRefY * nx;
            float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (rl < 1.0E-6F) {
                return q;
            }
            rx /= rl;
            ry /= rl;
            rz /= rl;
            float ux = ny * rz - nz * ry;
            float uy = nz * rx - nx * rz;
            float uz = nx * ry - ny * rx;

            java.util.List<float[]> vertices = new java.util.ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                float vx = q[i * 3];
                float vy = q[i * 3 + 1];
                float vz = q[i * 3 + 2];
                float dx = vx - cx;
                float dy = vy - cy;
                float dz = vz - cz;
                float localX = dx * rx + dy * ry + dz * rz;
                float localY = dx * ux + dy * uy + dz * uz;
                vertices.add(new float[] { vx, vy, vz, localX, localY });
            }
            vertices.sort((a, b) -> {
                int byY = Float.compare(b[4], a[4]);
                return byY != 0 ? byY : Float.compare(a[3], b[3]);
            });
            java.util.List<float[]> top = new java.util.ArrayList<>(vertices.subList(0, 2));
            java.util.List<float[]> bottom = new java.util.ArrayList<>(vertices.subList(2, 4));
            top.sort(java.util.Comparator.comparingDouble(v -> v[3]));
            bottom.sort(java.util.Comparator.comparingDouble(v -> v[3]));

            float[] out = new float[12];
            float[][] ordered = new float[][] { top.get(0), bottom.get(0), bottom.get(1), top.get(1) };
            for (int i = 0; i < 4; i++) {
                out[i * 3] = ordered[i][0];
                out[i * 3 + 1] = ordered[i][1];
                out[i * 3 + 2] = ordered[i][2];
            }
            return out;
        }

        public void tessellatorStart() {
            disableReplayCacheForFrame();
            tessellatorVertices.clear();
            tessColorRed = colorRed;
            tessColorGreen = colorGreen;
            tessColorBlue = colorBlue;
            tessColorAlpha = colorAlpha;
            tessNormalX = 0.0F;
            tessNormalY = 1.0F;
            tessNormalZ = 0.0F;
            tessNormalSet = false;
        }

        public void tessellatorAddVertex(double x, double y, double z) {
            tessellatorAddVertexWithUV(x, y, z, 0.0D, 0.0D);
        }

        public void tessellatorAddVertexWithUV(double x, double y, double z, double u, double v) {
            tessellatorVertices.add(new TessVertex((float) x, (float) y, (float) z, mapU((float) u, 0.0F, 1.0F), mapV((float) v, 0.0F, 1.0F)));
        }

        public void tessellatorSetColor(double r, double g, double b, double a) {
            tessColorRed = Mth.clamp((float) r, 0.0F, 1.0F);
            tessColorGreen = Mth.clamp((float) g, 0.0F, 1.0F);
            tessColorBlue = Mth.clamp((float) b, 0.0F, 1.0F);
            tessColorAlpha = Mth.clamp((float) a, 0.0F, 1.0F);
        }

        public void tessellatorSetNormal(double x, double y, double z) {
            tessNormalX = (float) x;
            tessNormalY = (float) y;
            tessNormalZ = (float) z;
            tessNormalSet = true;
        }

        public void tessellatorDraw() {
            if (tessellatorVertices.isEmpty() || poseStack == null || buffer == null) {
                tessellatorVertices.clear();
                return;
            }
            onBatchRendered();
            net.minecraft.resources.ResourceLocation texture = boundTexture;
            if (texture == null) {
                texture = MqoModelLoader.getScriptTexture("minecraft", "textures/block/white_wool.png", 0);
            }
            com.mojang.blaze3d.vertex.VertexConsumer vc =
                buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(texture));
            org.joml.Matrix4f mat = poseStack.last().pose();
            int r = Math.round(Mth.clamp(tessColorRed, 0.0F, 1.0F) * 255.0F);
            int g = Math.round(Mth.clamp(tessColorGreen, 0.0F, 1.0F) * 255.0F);
            int b = Math.round(Mth.clamp(tessColorBlue, 0.0F, 1.0F) * 255.0F);
            int a = Math.round(Mth.clamp(tessColorAlpha, 0.0F, 1.0F) * 255.0F);
            final float overlayBias = 0.0035F;
            for (int i = 0; i + 3 < tessellatorVertices.size(); i += 4) {
                TessVertex v0 = tessellatorVertices.get(i);
                TessVertex v1 = tessellatorVertices.get(i + 1);
                TessVertex v3 = tessellatorVertices.get(i + 3);
                float nx = tessNormalX;
                float ny = tessNormalY;
                float nz = tessNormalZ;
                if (!tessNormalSet) {
                    float e1x = v1.x - v0.x, e1y = v1.y - v0.y, e1z = v1.z - v0.z;
                    float e2x = v3.x - v0.x, e2y = v3.y - v0.y, e2z = v3.z - v0.z;
                    nx = e1y * e2z - e1z * e2y;
                    ny = e1z * e2x - e1x * e2z;
                    nz = e1x * e2y - e1y * e2x;
                    float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (nl > 1.0E-6F) {
                        nx /= nl;
                        ny /= nl;
                        nz /= nl;
                    } else {
                        nx = 0.0F;
                        ny = 1.0F;
                        nz = 0.0F;
                    }
                }
                for (int c = 0; c < 4; c++) {
                    TessVertex vtx = tessellatorVertices.get(i + c);
                    // addVertex(Matrix4f,..) はバニラ実装が頂点ごとに new Vector3f を確保するため、
                    // 同じ変換を確保なしで行う (見た目は不変)。
                    com.portofino.realtrainmodunofficial.client.render.VertexWriter.addVertex(vc, mat,
                            vtx.x + nx * overlayBias,
                            vtx.y + ny * overlayBias,
                            vtx.z + nz * overlayBias)
                        .setColor(r, g, b, a)
                        .setUv(vtx.u, vtx.v)
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(packedLight)
                        .setNormal(nx, ny, nz);
                }
            }
            tessellatorVertices.clear();
        }

        /** 指定グループの面にgif/画像テクスチャを貼って描画する。 */
        public void renderGifOnGroup(String groupsCsv, String domain, String path, int frameIndex) {
            if (mqoModel == null || poseStack == null || buffer == null || groupsCsv == null) return;
            java.util.Set<String> groups = new java.util.HashSet<>();
            for (String g : groupsCsv.split("[ ,]+")) {
                if (!g.isBlank()) groups.add(g.trim());
            }
            renderTextureWindowOnNormalizedGroupsWithRecord(groups, domain, path, frameIndex,
                0.0F, 0.0F, 1.0F, 1.0F);
        }

        public void renderGifOnParts(Object parts, String domain, String path, int frameIndex) {
            if (parts == null) return;
            java.util.List<String> groupNames = extractGroupNames(parts);
            if (groupNames.isEmpty()) return;
            java.util.Set<String> groups = new java.util.LinkedHashSet<>();
            for (String name : groupNames) {
                String normalized = normalizeLegacyGroupName(name);
                if (!normalized.isEmpty()) {
                    groups.add(normalized);
                }
            }
            renderTextureWindowOnNormalizedGroupsWithRecord(groups, domain, path, frameIndex,
                0.0F, 0.0F, 1.0F, 1.0F);
        }

        public void renderTextureWindowOnParts(Object parts, String domain, String path, int frameIndex,
                                               double u0, double v0, double u1, double v1) {
            if (parts == null || path == null || path.isBlank()) return;
            java.util.List<String> groupNames = extractGroupNames(parts);
            if (groupNames.isEmpty()) return;
            java.util.Set<String> groups = new java.util.LinkedHashSet<>();
            for (String name : groupNames) {
                String normalized = normalizeLegacyGroupName(name);
                if (!normalized.isEmpty()) {
                    groups.add(normalized);
                }
            }
            if (groups.isEmpty()) return;
            renderTextureWindowOnNormalizedGroupsWithRecord(groups, domain, path, frameIndex,
                (float) u0, (float) v0, (float) u1, (float) v1);
        }

        private void renderTextureWindowOnNormalizedGroupsWithRecord(java.util.Set<String> groups, String domain, String path,
                                                                     int frameIndex, float u0, float v0, float u1, float v1) {
            if (groups == null || groups.isEmpty() || path == null || path.isBlank()) return;
            disableReplayCacheForFrame();
            if (!replaying) {
                String record = String.join(",", groups) + "\n"
                    + (domain == null || domain.isBlank() ? "minecraft" : domain) + "\n"
                    + path;
                recordOp(OP_RENDER_TEXTURE_WINDOW, frameIndex, u0, v0, u1, v1, record, ' ');
            }
            renderTextureWindowOnNormalizedGroups(groups, domain, path, frameIndex, u0, v0, u1, v1);
        }

        private void renderTextureWindowFromRecord(String record, int frameIndex,
                                                   float u0, float v0, float u1, float v1) {
            if (record == null || record.isBlank()) return;
            String[] parts = record.split("\\n", 3);
            if (parts.length < 3) return;
            java.util.Set<String> groups = new java.util.LinkedHashSet<>();
            for (String group : parts[0].split(",")) {
                if (!group.isBlank()) {
                    groups.add(group.trim());
                }
            }
            renderTextureWindowOnNormalizedGroups(groups, parts[1], parts[2], frameIndex, u0, v0, u1, v1);
        }

        private void renderTextureWindowOnNormalizedGroups(java.util.Set<String> groups, String domain, String path,
                                                           int frameIndex, float u0, float v0, float u1, float v1) {
            renderTexturedQuadsOnNormalizedGroups(groups, domain, path, frameIndex, u0, v0, u1, v1);
        }

        private void renderGifOnNormalizedGroups(java.util.Set<String> groups, String domain, String path, int frameIndex) {
            renderTexturedQuadsOnNormalizedGroups(groups, domain, path, frameIndex, 0.0F, 0.0F, 1.0F, 1.0F);
        }

        private void renderTexturedQuadsOnNormalizedGroups(java.util.Set<String> groups, String domain, String path,
                                                           int frameIndex, float u0, float v0, float u1, float v1) {
            if (mqoModel == null || poseStack == null || buffer == null || groups == null || groups.isEmpty()) return;
            java.util.List<float[]> quads = mqoModel.getGroupQuadCorners(groups);
            if (quads.isEmpty()) return;
            onBatchRendered();
            net.minecraft.resources.ResourceLocation tex =
                MqoModelLoader.getScriptTexture(domain == null || domain.isBlank() ? "minecraft" : domain, path, frameIndex);
            com.mojang.blaze3d.vertex.VertexConsumer vc =
                buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(tex));
            org.joml.Matrix4f mat = poseStack.last().pose();
            int light = packedLight;
            for (float[] q : quads) {
                q = orientOverlayQuad(q);
                // 面法線(隅0→1, 0→3 の外積)方向へ微小オフセットして z-fight 回避
                float e1x = q[3] - q[0], e1y = q[4] - q[1], e1z = q[5] - q[2];
                float e2x = q[9] - q[0], e2y = q[10] - q[1], e2z = q[11] - q[2];
                float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl > 1.0E-6F) { nx /= nl; ny /= nl; nz /= nl; } else { nx = ny = nz = 0; }
                float off = 0.003F;
                float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
                for (int c = 0; c < 4; c++) {
                    float vx = q[c * 3] + nx * off, vy = q[c * 3 + 1] + ny * off, vz = q[c * 3 + 2] + nz * off;
                    com.portofino.realtrainmodunofficial.client.render.VertexWriter.addVertex(vc, mat, vx, vy, vz)
                        .setColor(255, 255, 255, 255)
                        .setUv(uv[c][0], uv[c][1])
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(nx, ny, nz);
                }
            }
        }

        private static float[] orientOverlayQuad(float[] q) {
            if (q == null || q.length < 12) {
                return q;
            }
            float e1x = q[3] - q[0], e1y = q[4] - q[1], e1z = q[5] - q[2];
            float e2x = q[9] - q[0], e2y = q[10] - q[1], e2z = q[11] - q[2];
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl > 1.0E-6F) {
                nx /= nl; ny /= nl; nz /= nl;
            } else {
                nx = 0; ny = 1; nz = 0;
            }

            float upx = 0.0F, upy = 1.0F, upz = 0.0F;
            if (Math.abs(ny) > 0.65F) {
                upx = 0.0F; upy = 0.0F; upz = -1.0F;
            }
            float dot = upx * nx + upy * ny + upz * nz;
            upx -= nx * dot; upy -= ny * dot; upz -= nz * dot;
            float ul = (float) Math.sqrt(upx * upx + upy * upy + upz * upz);
            if (ul <= 1.0E-6F) {
                upx = 0.0F; upy = 0.0F; upz = -1.0F;
                dot = upx * nx + upy * ny + upz * nz;
                upx -= nx * dot; upy -= ny * dot; upz -= nz * dot;
                ul = (float) Math.sqrt(upx * upx + upy * upy + upz * upz);
            }
            if (ul > 1.0E-6F) {
                upx /= ul; upy /= ul; upz /= ul;
            }
            float rx = upy * nz - upz * ny;
            float ry = upz * nx - upx * nz;
            float rz = upx * ny - upy * nx;
            float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (rl > 1.0E-6F) {
                rx /= rl; ry /= rl; rz /= rl;
            }

            float cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < 4; i++) {
                cx += q[i * 3]; cy += q[i * 3 + 1]; cz += q[i * 3 + 2];
            }
            cx *= 0.25F; cy *= 0.25F; cz *= 0.25F;

            int tl = -1, bl = -1, br = -1, tr = -1;
            float bestTl = -Float.MAX_VALUE, bestBl = -Float.MAX_VALUE;
            float bestBr = -Float.MAX_VALUE, bestTr = -Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                float dx = q[i * 3] - cx, dy = q[i * 3 + 1] - cy, dz = q[i * 3 + 2] - cz;
                float su = dx * rx + dy * ry + dz * rz;
                float sv = dx * upx + dy * upy + dz * upz;
                float sTl = -su + sv, sBl = -su - sv, sBr = su - sv, sTr = su + sv;
                if (sTl > bestTl) { bestTl = sTl; tl = i; }
                if (sBl > bestBl) { bestBl = sBl; bl = i; }
                if (sBr > bestBr) { bestBr = sBr; br = i; }
                if (sTr > bestTr) { bestTr = sTr; tr = i; }
            }
            if (tl < 0 || bl < 0 || br < 0 || tr < 0) {
                return q;
            }
            float[] out = new float[12];
            int[] order = {tl, bl, br, tr};
            for (int i = 0; i < 4; i++) {
                int src = order[i] * 3;
                out[i * 3] = q[src];
                out[i * 3 + 1] = q[src + 1];
                out[i * 3 + 2] = q[src + 2];
            }
            return out;
        }

        public void bindAnimatedScriptTexture(String domain, String path, double tick, double fps) {
            disableReplayCacheForFrame();
            boundTexture = MqoModelLoader.getScriptTextureByTick(domain, path, tick, fps);
        }

        public int getScriptTextureFrameCount(String domain, String path) {
            return MqoModelLoader.getScriptTextureData(domain, path).frames().size();
        }

        public int getScriptTextureWidth(String domain, String path) {
            return MqoModelLoader.getScriptTextureData(domain, path).width();
        }

        public int getScriptTextureHeight(String domain, String path) {
            return MqoModelLoader.getScriptTextureData(domain, path).height();
        }

        public void bindTexture(Object texture) {
            if (texture == null) {
                clearScriptTexture();
                return;
            }
            try {
                String domain = readTextureComponent(texture, "func_110624_b", "namespace", "domain");
                String path = readTextureComponent(texture, "func_110623_a", "path", "resourcePath");
                if (path == null || path.isBlank()) {
                    clearScriptTexture();
                    return;
                }
                bindScriptTexture(domain == null || domain.isBlank() ? "minecraft" : domain, path, 0);
            } catch (Exception e) {
                clearScriptTexture();
            }
        }

        private static String readTextureComponent(Object texture, String methodName, String... fieldNames) {
            try {
                java.lang.reflect.Method hasMember = texture.getClass().getMethod("hasMember", String.class);
                java.lang.reflect.Method getMember = texture.getClass().getMethod("getMember", String.class);
                if (Boolean.TRUE.equals(hasMember.invoke(texture, methodName))) {
                    Object fn = getMember.invoke(texture, methodName);
                    if (fn != null) {
                        try {
                            Object value = fn.getClass().getMethod("execute", Object[].class).invoke(fn, (Object) new Object[0]);
                            if (value != null) return String.valueOf(value);
                        } catch (Exception ignored) {
                        }
                    }
                }
                for (String fieldName : fieldNames) {
                    if (Boolean.TRUE.equals(hasMember.invoke(texture, fieldName))) {
                        Object value = getMember.invoke(texture, fieldName);
                        if (value != null) {
                            return String.valueOf(value);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                Object value = texture.getClass().getMethod(methodName).invoke(texture);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (Exception ignored) {
            }
            for (String fieldName : fieldNames) {
                try {
                    Object value = texture.getClass().getField(fieldName).get(texture);
                    if (value != null) {
                        return String.valueOf(value);
                    }
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        /** Disables lighting for old model scripts. */
        public void disableLighting() {
            // Modern rendering keeps lighting in the packed light value.
        }

        /** Enables lighting for old model scripts. */
        public void enableLighting() {
            this.packedLight = basePackedLight;
            this.lightmapMaxForced = false;
            recordOp(OP_ENABLE_LIGHTING, 0, 0, 0, 0, 0, null, ' ');
        }

        /** Applies a packed light value requested by old model scripts. */
        public void setBrightness(Object value) {
            if (value instanceof Number number) {
                this.packedLight = number.intValue();
                this.lightmapMaxForced = false;
                recordOp(OP_SET_BRIGHTNESS, this.packedLight, 0, 0, 0, 0, null, ' ');
            } else {
                this.packedLight = basePackedLight;
                this.lightmapMaxForced = false;
                recordOp(OP_SET_BRIGHTNESS, this.packedLight, 0, 0, 0, 0, null, ' ');
            }
        }

        /** Forces full brightness for emissive script parts. */
        public void setLightmapMaxBrightness() {
            this.packedLight = 0x00F000F0;
            this.lightmapMaxForced = true;
            recordOp(OP_SET_LIGHTMAP_MAX, 0, 0, 0, 0, 0, null, ' ');
        }

        @SuppressWarnings("unchecked")
        public void renderParts(Object groups) {
            if (mqoModel == null || poseStack == null || buffer == null) {
                return;
            }
            renderPartsCalls++;
            int baseDepth = matrixDepth;
            int savedPackedLight = packedLight;
            boolean savedLightmapMaxForced = lightmapMaxForced;
            try {
                // 文字列入力時はキャッシュ参照。SL の動軸スクリプト等で
                // 毎フレーム同じ groupsStr が来るため、解析処理を 1 回に削減。
                List<String> groupNames;
                Set<String> normalizedNames;
                Set<String> presentGroupNames;
                boolean legacyDisplaySelection;
                if (groups instanceof String s) {
                    String parseCacheKey = currentPass + "\u0000" + s;
                    ParsedGroupSet cached = renderPartsParseCache.get(parseCacheKey);
                    if (cached == null) {
                        List<String> raw = expandSerializedGroupNames(s);
                        raw = stripLegacyPlaceholderGroups(raw);
                        List<String> filtered = new ArrayList<>(raw.size());
                        for (String g : raw) {
                            String n = normalizeLegacyGroupName(g);
                            if (n.equals("shadow") || n.startsWith("shadow_") || n.endsWith("_shadow")) continue;
                            if (n.endsWith("_guide") || n.endsWith("[obj]") || n.endsWith("_atari") || n.endsWith(" atari")) continue;
                            if (currentPass >= 2 && isLightOffGroup(n)) continue;
                            filtered.add(g);
                        }
                        boolean legacy = isLegacyDisplaySelection(filtered);
                        Set<String> norm = new LinkedHashSet<>(filtered.size());
                        for (String g : filtered) {
                            String n = normalizeLegacyGroupName(g);
                            if (!n.isEmpty()) norm.add(n);
                        }
                        Set<String> present = new LinkedHashSet<>(norm.size());
                        for (String n : norm) {
                            if (mqoModel.hasGroupNamed(n)) present.add(n);
                        }
                        boolean hasEmissive = false;
                        for (String g : filtered) { if (isEmissiveGroup(g)) { hasEmissive = true; break; } }
                        cached = new ParsedGroupSet(filtered, norm, present, legacy, hasEmissive);
                        renderPartsParseCache.put(parseCacheKey, cached);
                    }
                    if (cached.empty) {
                        return;
                    }
                    // pass 2 に録画する OP_RENDER_PARTS は emissive グループを持つものだけ。
                    // これにより pass 2 の op-list が SL の ~5 ops に圧縮され replay が超高速化。
                    if (currentRecording != null) {
                        recordOp(OP_RENDER_PARTS, 0, 0, 0, 0, 0, s, ' ');
                    }
                    groupNames = cached.filteredGroupNames;
                    legacyDisplaySelection = cached.legacyDisplaySelection;
                    if (legacyDisplaySelection && currentPass != 2) {
                        return;
                    }
                    if (currentEntity instanceof TrainEntity train) {
                        if (currentPass >= 2) {
                            normalizedNames = cached.normalizedNames;
                            presentGroupNames = cached.presentGroupNames;
                        } else {
                            normalizedNames = cached.normalizedNames;
                            presentGroupNames = cached.presentGroupNames;
                        }
                    } else {
                        normalizedNames = cached.normalizedNames;
                        presentGroupNames = cached.presentGroupNames;
                    }
                } else {
                    // Collection/配列入力はキャッシュ非対応
                    groupNames = extractGroupNames(groups);
                    groupNames = stripLegacyPlaceholderGroups(groupNames);
                    groupNames = groupNames.stream()
                        .filter(g -> {
                            String n = normalizeLegacyGroupName(g);
                            if (n.equals("shadow") || n.startsWith("shadow_") || n.endsWith("_shadow")) return false;
                            if (n.endsWith("_guide") || n.endsWith("[obj]") || n.endsWith("_atari") || n.endsWith(" atari")) return false;
                            if (currentPass >= 2 && isLightOffGroup(n)) return false;
                            return true;
                        })
                        .collect(Collectors.toList());
                    legacyDisplaySelection = isLegacyDisplaySelection(groupNames);
                    if (legacyDisplaySelection && currentPass != 2) {
                        return;
                    }
                    if (currentEntity instanceof TrainEntity train) {
                        if (currentPass >= 2) {
                            groupNames = groupNames.stream().collect(Collectors.toList());
                        }
                        if (groupNames.isEmpty()) {
                            return;
                        }
                    }
                    normalizedNames = groupNames.stream()
                        .map(ScriptModelRenderer::normalizeLegacyGroupName)
                        .filter(name -> !name.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                    presentGroupNames = normalizedNames.stream()
                        .filter(mqoModel::hasGroupNamed)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                }
                if (currentPass >= 2) {
                    normalizedNames = filterLegacyScriptEmissiveGroups(normalizedNames);
                    presentGroupNames = normalizedNames.stream()
                        .filter(mqoModel::hasGroupNamed)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                    if (normalizedNames.isEmpty() || presentGroupNames.isEmpty()) {
                        return;
                    }
                }
                if (currentPass == 1) {
                    scriptedTranslucentGroups.addAll(presentGroupNames);
                } else if (currentPass < 2) {
                    scriptedOpaqueGroups.addAll(presentGroupNames);
                }
                int renderPackedLight = effectivePackedLightForScriptParts(presentGroupNames);
                currentMatId = 0;
                if (legacyDisplaySelection) {
                    boolean boundRollsign = false;
                    try {
                        mqoModel.renderNamedGroups(
                            poseStack,
                            buffer,
                            renderPackedLight,
                            overlay,
                            false,
                            normalizedNames,
                            this
                        );
                    } finally {
                        if (boundRollsign) {
                            clearUvWindow();
                            clearScriptTexture();
                        }
                    }
                } else {
                    if (currentPass >= 2) {
                        // Emissive pass: RTM light groups (lightF, lightB etc.) may be either
                        // opaque or translucent batches. Render both so nothing is skipped.
                        mqoModel.renderNamedGroups(poseStack, buffer, renderPackedLight, overlay, false, normalizedNames, this);
                        mqoModel.renderNamedGroups(poseStack, buffer, renderPackedLight, overlay, true, normalizedNames, this);
                    } else {
                        if (currentPass <= 0) {
                            mqoModel.renderNamedGroups(poseStack, buffer, renderPackedLight, overlay, false, normalizedNames, this);
                        } else {
                            mqoModel.renderNamedGroups(
                                poseStack,
                                buffer,
                                renderPackedLight,
                                overlay,
                                true,
                                normalizedNames,
                                this
                            );
                        }
                    }
                }
            } finally {
                packedLight = savedPackedLight;
                lightmapMaxForced = savedLightmapMaxForced;
                // このrenderParts呼び出し内で増えた分だけ戻す
                while (matrixDepth > baseDepth) {
                    poseStack.popPose();
                    matrixDepth--;
                }
            }
        }

        private int effectivePackedLightForScriptParts(Set<String> presentGroupNames) {
            if (!(currentEntity instanceof TrainEntity train)) {
                return packedLight;
            }
            // pass 3/4 = 前照灯/尾灯の発光パス。
            // filterLegacyScriptEmissiveGroups でランプ系だけに絞られている。
            if (currentPass >= 3) {
                return 0xF000F0;
            }
            if (lightmapMaxForced) {
                return packedLight;
            }
            if (packedLight == basePackedLight) {
                return packedLight;
            }
            if (presentGroupNames == null || presentGroupNames.isEmpty()) {
                return basePackedLight;
            }
            for (String name : presentGroupNames) {
                String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
                if (!isInteriorEmissionGroup(lower) && !isLegacyDisplayGroup(lower)) {
                    return basePackedLight;
                }
            }
            if (!train.isInteriorLightOn()) {
                for (String name : presentGroupNames) {
                    String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
                    if (isInteriorEmissionGroup(lower)) {
                        return basePackedLight;
                    }
                }
            }
            return packedLight;
        }

        private static String normalizeLegacyGroupName(String groupName) {
            return groupName == null ? "" : groupName.trim().toLowerCase(Locale.ROOT);
        }

        private static boolean isLightOffGroup(String lowerGroupName) {
            if (lowerGroupName == null || lowerGroupName.isBlank()) {
                return false;
            }
            boolean lightLike = lowerGroupName.contains("light") || lowerGroupName.contains("lamp");
            if (!lightLike) {
                return false;
            }
            return lowerGroupName.endsWith("_off")
                || lowerGroupName.endsWith("-off")
                || lowerGroupName.endsWith("off")
                || lowerGroupName.contains("_off_")
                || lowerGroupName.contains("-off-");
        }

        private TrainEntity resolveCurrentTrainEntity() {
            if (currentEntity instanceof TrainEntity train) {
                return train;
            }
            if (currentEntity instanceof LegacyScriptExecutor exec) {
                return exec.getTrain();
            }
            if (currentEntity != null) {
                try {
                    Object train = currentEntity.getClass().getMethod("getTrain").invoke(currentEntity);
                    if (train instanceof TrainEntity resolved) {
                        return resolved;
                    }
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        private boolean isFormationMiddle(TrainEntity train) {
            if (train == null) {
                return false;
            }
            try {
                java.util.List<TrainEntity> trains = train.getFormationTrainsForDisplay();
                int index = trains.indexOf(train);
                return trains.size() > 1 && index > 0 && index < trains.size() - 1;
            } catch (Exception ignored) {
                return false;
            }
        }

        private List<String> stripLegacyPlaceholderGroups(List<String> groupNames) {
            if (groupNames == null || groupNames.isEmpty() || mqoModel == null) {
                return groupNames;
            }
            boolean hasIndexedDest = mqoModel.hasGroupNamed("dest0");
            boolean hasIndexedType = mqoModel.hasGroupNamed("type0");
            boolean hasAnimatedDoors = mqoModel.hasGroupNamed("doorFL")
                || mqoModel.hasGroupNamed("doorFR")
                || mqoModel.hasGroupNamed("doorBL")
                || mqoModel.hasGroupNamed("doorBR");
            boolean hasCabLeverStates = mqoModel.hasGroupNamed("L_F")
                || mqoModel.hasGroupNamed("L_M")
                || mqoModel.hasGroupNamed("L_B");
            return groupNames.stream()
                .filter(group -> {
                    if (group == null) {
                        return false;
                    }
                    String trimmed = group.trim();
                    String lower = trimmed.toLowerCase(Locale.ROOT);
                    if (hasIndexedDest && lower.equals("dest")) {
                        return false;
                    }
                    if (hasIndexedType && lower.equals("type")) {
                        return false;
                    }
                    if (hasCabLeverStates && lower.equals("lever")) {
                        return false;
                    }
                    if (hasAnimatedDoors && lower.equals("door")) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
        }

        private boolean isLegacyDisplaySelection(List<String> groupNames) {
            if (groupNames == null || groupNames.isEmpty()) {
                return false;
            }
            for (String groupName : groupNames) {
                if (!isLegacyDisplayGroup(groupName)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isLegacyDisplayGroup(String groupName) {
            if (groupName == null) {
                return false;
            }
            String lower = groupName.trim().toLowerCase(Locale.ROOT);
            return lower.equals("dest")
                || lower.equals("type")
                || lower.startsWith("dest") && lower.length() > 4 && lower.substring(4).chars().allMatch(Character::isDigit)
                || lower.startsWith("type") && lower.length() > 4 && lower.substring(4).chars().allMatch(Character::isDigit);
        }

        private boolean shouldRenderLightGroup(TrainEntity train, String groupName) {
            if (train == null || groupName == null) {
                return true;
            }
            String lower = groupName.toLowerCase(Locale.ROOT);
            if (lower.equals("lightf") || lower.equals("lightb")) {
                // 旧RTM系 script は lightF/lightB の選択自体を JS 側で行うことがあるので、
                // ここで消してしまわず script の描画意図を優先する。
                return true;
            }
            boolean head = lower.contains("hlight") || lower.contains("headlight") || lower.contains("head_light") || lower.equals("lightf");
            boolean tail = lower.contains("tlight") || lower.contains("taillight") || lower.contains("tail_light") || lower.equals("lightb");
            boolean auxiliary = lower.contains("elight");
            if (!head && !tail && !auxiliary) {
                return true;
            }
            int mode = train.getLightMode();
            if (mode <= 0) {
                return false;
            }
            if (head) {
                return mode == 1 || mode == 3 || mode == 2;
            }
            if (tail) {
                return mode >= 2;
            }
            return true;
        }

        private boolean shouldRenderTrainInterior(TrainEntity train) {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                return (mc.player != null && mc.player.getVehicle() == train)
                    || (mc.cameraEntity != null && mc.cameraEntity.distanceToSqr(train) < 8.0D * 8.0D);
            } catch (Throwable ignored) {
                return true;
            }
        }

        private boolean shouldRenderInteriorGroup(String groupName, boolean renderInterior) {
            return true;
        }

        private Set<String> filterLegacyScriptEmissiveGroups(Set<String> groupNames) {
            if (groupNames == null || groupNames.isEmpty()) {
                return groupNames;
            }
            TrainEntity train = currentEntity instanceof TrainEntity t ? t : null;
            boolean interiorOn = train != null && train.isInteriorLightOn();
            return groupNames.stream()
                .filter(name -> shouldRenderLegacyScriptEmissiveGroup(train, name, interiorOn))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        private boolean shouldRenderLegacyScriptEmissiveGroup(TrainEntity train, String groupName, boolean interiorOn) {
            if (groupName == null || groupName.isBlank()) {
                return false;
            }
            String lower = groupName.toLowerCase(Locale.ROOT);
            if (train != null) {
                // 発光パスはマテリアルのLightフラグだけで絞る(グループ名では絞らない)
                if (currentPass >= 3) {
                    return true;
                }
                // pass 2 = 室内灯。車体を明るくしないよう内装グループだけ。
                return interiorOn && isInteriorEmissionGroup(lower);
            }
            if (isLegacyDisplayGroup(lower)) {
                return true;
            }
            if (isInteriorEmissionGroup(lower)) {
                return interiorOn;
            }
            // 自動改札の通行可/不可の表示 (sign_F / sign_B)。本家は pass 2 で描く。
            if (lower.startsWith("sign_") || lower.equals("sign")) {
                return true;
            }
            if (lower.matches("lamp_\\d+")) {
                return false;
            }
            if (lower.contains("doorlamp")) {
                return false;
            }
            // ★名前で絞るのは本家に無い仕組みだが、RTMU はグループ単位で描くため必要。
            // 本家はマテリアル単位で「Light テクスチャを持つものだけ」を発光パスに出すので、
            // スクリプトが同じ Parts にまとめた非発光パーツ (踏切の dirL/dirR = 矢印の板) は
            // 光らない。ここを撤廃すると板まで赤く光る (ユーザー報告)。
            if (lower.contains("light") || lower.contains("lamp") || lower.contains("marker")) {
                return shouldRenderLightGroup(train, lower) && !isLightOffGroup(lower);
            }
            return false;
        }

        private boolean isInteriorEmissionGroup(String lowerGroupName) {
            return lowerGroupName.contains("light")
                || lowerGroupName.contains("lamp")
                || lowerGroupName.contains("_ceil")
                || lowerGroupName.contains("led_box")
                || lowerGroupName.contains("led");
        }

        private boolean bindLegacyRollsignTextureIfPresent(List<String> groupNames) {
            if (!(currentEntity instanceof TrainEntity train)) {
                return false;
            }
            var definition = com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry.getById(train.getVehicleId());
            if (definition == null) {
                return false;
            }
            String rollsignTexture = definition.getRollsignTexture();
            if (rollsignTexture == null || rollsignTexture.isBlank()) {
                return false;
            }
            int count = Math.max(1, definition.getRollsignNames().isEmpty() ? 1 : definition.getRollsignNames().size());
            int index = Math.floorMod(train.getDestinationIndex(), count);
            float v0 = index / (float) count;
            float v1 = (index + 1.0F) / (float) count;
            bindScriptTexture("minecraft", rollsignTexture, 0);
            setUvWindow(0.0, v0, 1.0, v1);
            return true;
        }

        private boolean isEmissiveGroup(String groupName) {
            if (groupName == null) {
                return false;
            }
            String lower = groupName.toLowerCase(Locale.ROOT);
            return lower.contains("light")
                || lower.contains("lamp")
                || lower.contains("marker")
                || lower.contains("led")
                || lower.contains("幕")
                || lower.contains("roll")
                || lower.equals("dest")
                || lower.equals("type")
                || DEST_N_PATTERN.matcher(lower).matches()
                || TYPE_N_PATTERN.matcher(lower).matches()
                || lower.contains("destination");
        }

        public void renderPart(String group) {
            renderParts(group);
        }

        public void render(Object groups) {
            renderParts(groups);
        }

        public boolean hasScriptRenderedGroups() {
            // RTM scripts own every group registered via registerParts.
            // Even when a script intentionally skips a registered group this frame,
            // baked rendering must not draw it back in (MSE hood/end parts, car-type variants).
            return !scriptRegisteredGroups.isEmpty()
                || !scriptedOpaqueGroups.isEmpty()
                || !scriptedTranslucentGroups.isEmpty();
        }

        /** スクリプトが恒久無効化されたときにグループ所有権をクリアする。 */
        public void clearScriptRegisteredGroups() {
            scriptRegisteredGroups.clear();
            scriptedOpaqueGroups.clear();
            scriptedTranslucentGroups.clear();
            scriptedEmissiveGroups.clear();
        }

        public boolean shouldRenderBakedGroup(String groupName, boolean translucent) {
            String normalized = normalizeLegacyGroupName(groupName);
            if (normalized.isEmpty()) {
                return true;
            }
            // 擬似シャドウ group (完全一致のみ) は常に skip (ユーザー要望で車両の影は無効化)。
            if (normalized.equalsIgnoreCase("shadow")) {
                return false;
            }
            // 角度曲げ変種は描かない(原点姿勢だと散乱する)
            if (isBendVariant(normalized)) {
                return false;
            }
            // 台車・車輪グループは常にベイクド描画もする(消えるより重なる方を優先)
            boolean isBogieLike = isBogieLikeGroup(normalized);
            if (isBogieLike) {
                return !scriptedOpaqueGroups.contains(normalized)
                    && !scriptedTranslucentGroups.contains(normalized);
            }
            // Groups registered by the script in init are fully managed by it —
            // skip them in baked render even if they weren't rendered this frame
            // (e.g. body02 when CarType="01": script skips it, baked must too).
            if (scriptRegisteredGroups.contains(normalized)) {
                return false;
            }
            // 切替ファミリーを登録済みなら未登録の兄弟もbakedから落とす
            if (scriptRegisteredAnyInSelectorFamily(normalized)) {
                return false;
            }
            // type*を1つでも登録済みなら未登録のtype*は描かない
            if (normalized.startsWith("type") && scriptRegisteredAnyWithPrefix("type")) {
                return false;
            }
            return !scriptedOpaqueGroups.contains(normalized)
                && !scriptedTranslucentGroups.contains(normalized);
        }

        /** scriptRegisteredGroups に指定 prefix で始まるグループが 1 つでもあるか。 */
        private boolean scriptRegisteredAnyWithPrefix(String prefix) {
            for (String g : scriptRegisteredGroups) {
                if (g.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        private boolean currentTrainHasSeparateBogieModel() {
            if (!(currentEntity instanceof TrainEntity train)) {
                return false;
            }
            VehicleDefinition def = VehicleRegistry.getById(train.getVehicleId());
            return def != null && def.getBogies().stream()
                .anyMatch(b -> b.modelFile() != null && !b.modelFile().isBlank());
        }

        private static boolean isBogieLikeGroup(String normalized) {
            if (normalized == null || normalized.isBlank()) {
                return false;
            }
            return normalized.contains("bogie")
                || normalized.contains("wheel")
                || normalized.contains("truck")
                || normalized.contains("daisya")
                || normalized.contains("daisha")
                || normalized.contains("sharin")
                || normalized.startsWith("台車");
        }

        private boolean scriptRegisteredAnyInSelectorFamily(String normalizedGroupName) {
            if (scriptRegisteredGroups.isEmpty()) {
                return false;
            }
            String family = selectorFamilyKey(normalizedGroupName);
            if (family == null || family.length() < 3) {
                return false;
            }
            for (String registered : scriptRegisteredGroups) {
                String registeredFamily = selectorFamilyKey(registered);
                if (family.equals(registeredFamily)) {
                    return true;
                }
            }
            return false;
        }

        private static String selectorFamilyKey(String groupName) {
            if (groupName == null || groupName.isBlank()) {
                return null;
            }
            String lower = groupName.trim().toLowerCase(Locale.ROOT);

            // Pack-specific but stable RTM selector families.
            if (lower.startsWith("cp6_hood")) return "cp6_hood";
            if (lower.startsWith("cp7_hood")) return "cp7_hood";
            if (lower.startsWith("ex16_light_f_")) return "ex16_light_f";
            if (lower.startsWith("ex16_light_r_")) return "ex16_light_r";
            if (lower.startsWith("doorlamp_")) return stripStateSuffix(lower);
            if (lower.startsWith("mark_number")) return "mark_number";
            if (lower.startsWith("mark_old")) return "mark_old";
            if (lower.startsWith("mark_new")) return "mark_new";
            if (lower.startsWith("under_panel")) return "under_panel";
            if (lower.matches("under\\d+.*")) return "under";
            if (lower.matches("notch\\d+.*")) return "notch";
            if (lower.matches("brake\\d+.*")) return "brake";
            if (lower.matches("lv[_-]?[fnb]")) return "lv";

            // Generic state meshes used by many RTM scripts.
            if (lower.contains("light") || lower.contains("lamp") || lower.contains("marker")) {
                String stripped = stripStateSuffix(lower);
                stripped = stripped.replaceAll("[_-]?\\d+$", "");
                return stripped.length() >= 3 ? stripped : null;
            }
            return null;
        }

        private static String stripStateSuffix(String lowerGroupName) {
            String stripped = lowerGroupName
                .replaceAll("(?i)([_-]?(on|off))\\d*$", "")
                .replaceAll("(?i)([_-]?(on|off))([_-].*)$", "$2")
                .replaceAll("[_-]+$", "");
            return stripped.isEmpty() ? lowerGroupName : stripped;
        }

        public int getMatrixDepth() {
            return matrixDepth;
        }

        public void restoreMatrixDepth(int targetDepth) {
            if (poseStack == null) {
                matrixDepth = Math.max(0, targetDepth);
                return;
            }
            int safeTarget = Math.max(0, targetDepth);
            while (matrixDepth > safeTarget) {
                poseStack.popPose();
                matrixDepth--;
            }
        }

        /** レガシースクリプト用の車輪回転角(度)を返す。 */
        public float getWheelRotationR(Object entity) {
            if (entity instanceof TrainEntity train) {
                // 走行距離ベースの累積回転角(TrainEntity.tick で毎tick加算)を使う。
                // 旧実装(tickCount × 現在速度)は速度変化のたびに巨大な回転ジャンプを起こし
                // 「速度10でも空転しまくる」原因だった。
                return train.getWheelRotationDegrees();
            }
            if (entity instanceof LegacyScriptExecutor exec && exec.getTrain() != null) {
                return getWheelRotationR(exec.getTrain());
            }
            return 0.0F;
        }

        public int getTick(Object entity) {
            jp.ngt.rtm.render.PartsRenderer.markTimeAccessed();
            try {
                if (entity instanceof LegacyScriptExecutor exec) {
                    return (int) exec.getTick();
                }
                if (entity instanceof Entity e) {
                    java.lang.reflect.Field field = Entity.class.getDeclaredField("tickCount");
                    field.setAccessible(true);
                    return field.getInt(e);
                }
                if (entity instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                    return be.getLevel() == null ? 0 : (int) be.getLevel().getGameTime();
                }
            } catch (Exception ignored) {
            }
            return 0;
        }

        public long getSystemTime() {
            jp.ngt.rtm.render.PartsRenderer.markTimeAccessed();
            return System.currentTimeMillis() / 1000L;
        }

        /** Returns legacy renderer scratch data addressed by an integer key. */
        public Object getData(long key) {
            return scriptData.getOrDefault(key, 0);
        }

        /** Stores legacy renderer scratch data addressed by an integer key. */
        public void setData(long key, Object value) {
            scriptData.put(key, value == null ? 0 : value);
        }

        public int getMCTime(Object entity) {
            return (int) (getWorldDayTime(entity) % 24000L);
        }

        public int getMCTime() {
            return getMCTime(currentEntity);
        }

        public int getMCHour(Object entity) {
            int time = getMCTime(entity);
            return (time / 1000 + 6) % 24;
        }

        /** Returns the current world hour for old scripts that omit the entity argument. */
        public int getMCHour() {
            return getMCHour(currentEntity);
        }

        public int getMCMinute(Object entity) {
            int time = getMCTime(entity);
            return (int) ((time % 1000) * 0.06F);
        }

        /** Returns the current world minute for old scripts that omit the entity argument. */
        public int getMCMinute() {
            return getMCMinute(currentEntity);
        }

        public float getMovingCount(Object entity) {
            if (entity instanceof InstalledObjectBlockEntity blockEntity) {
                return blockEntity.getBarMoveCount() / 90.0F;
            }
            return 0.0F;
        }

        public int getLightState(Object entity) {
            if (entity instanceof InstalledObjectBlockEntity blockEntity) {
                // 本家getLightState準拠: 照明=RS電力で±1、踏切=点滅カウンタ、信号=現示
                if (blockEntity.getCategory()
                        == com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory.LIGHT) {
                    return blockEntity.isPowered() ? 1 : -1;
                }
                return blockEntity.getLightCount();
            }
            return -1;
        }

        private long getWorldDayTime(Object entity) {
            jp.ngt.rtm.render.PartsRenderer.markTimeAccessed();
            try {
                if (entity instanceof Entity e) {
                    return e.level() == null ? 0 : e.level().dayTime();
                }
                if (entity instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                    return be.getLevel() == null ? 0 : be.getLevel().dayTime();
                }
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level != null) {
                    return mc.level.dayTime();
                }
            } catch (Exception ignored) {
            }
            return 0;
        }

        public float sigmoid(double x) {
            if (x <= 0.0D) {
                return 0.0F;
            }
            if (x >= 1.0D) {
                return 1.0F;
            }
            double centered = (x - 0.5D) * 5.0D;
            double curved = centered / Math.sqrt(1.0D + centered * centered);
            return (float) ((curved + 1.0D) * 0.5D);
        }

        public boolean isRenderingTrain() {
            return currentEntity instanceof TrainEntity;
        }

        public boolean isRenderingInteriorLitTrain() {
            return currentEntity instanceof TrainEntity t && t.isInteriorLightOn();
        }

        private TrainEntity extractTrain(Object entity) {
            if (entity instanceof TrainEntity t) return t;
            if (entity instanceof LegacyScriptExecutor e) return e.getTrain();
            return currentEntity instanceof TrainEntity t ? t : null;
        }

        public float getDoorMovementL(Object entity) {
            TrainEntity t = extractTrain(entity);
            return t == null ? 0.0F : Math.min(1.0F, t.doorMoveL / 60.0F);
        }

        public float getDoorMovementR(Object entity) {
            TrainEntity t = extractTrain(entity);
            return t == null ? 0.0F : Math.min(1.0F, t.doorMoveR / 60.0F);
        }

        public float getDoorMovementL() { return getDoorMovementL(currentEntity); }
        public float getDoorMovementR() { return getDoorMovementR(currentEntity); }

        public float getPantographMovementBack(Object entity) {
            TrainEntity t = extractTrain(entity);
            return t == null ? 1.0F : t.pantograph_B / 40.0F;
        }

        public float getPantographMovementFront(Object entity) {
            TrainEntity t = extractTrain(entity);
            return t == null ? 1.0F : t.pantograph_F / 40.0F;
        }

        public float getPantographMovementBack() { return getPantographMovementBack(currentEntity); }
        public float getPantographMovementFront() { return getPantographMovementFront(currentEntity); }

        public void pushMatrix() {
            if (poseStack != null) {
                poseStack.pushPose();
                matrixDepth++;
            }
            scriptLocalStack.push(scriptLocalOrigin);
            recordOp(OP_PUSH, 0, 0, 0, 0, 0, null, ' ');
        }

        public void popMatrix() {
            if (poseStack != null && matrixDepth > 0) {
                poseStack.popPose();
                matrixDepth--;
            }
            scriptLocalOrigin = scriptLocalStack.isEmpty() ? Vec3.ZERO : scriptLocalStack.pop();
            recordOp(OP_POP, 0, 0, 0, 0, 0, null, ' ');
        }

        public void translate(float x, float y, float z) {
            // NaN/Infinite ガード: スクリプトが undefined を渡すと NaN になり poseStack 全体が破壊される。
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
            Vec3 applied = adjustLegacyScriptBogieTranslate(x, y, z);
            if (poseStack != null) {
                poseStack.translate(applied.x, applied.y, applied.z);
            }
            scriptLocalOrigin = scriptLocalOrigin.add(applied);
            recordOp(OP_TRANSLATE, x, y, z, 0, 0, null, ' ');
        }

        private Vec3 adjustLegacyScriptBogieTranslate(float x, float y, float z) {
            Vec3 requested = new Vec3(x, y, z);
            if (!(currentEntity instanceof TrainEntity train)) {
                return requested;
            }
            VehicleDefinition def = VehicleRegistry.getById(train.getVehicleId());
            if (def == null || def.getBogies().isEmpty()) {
                return requested;
            }

            Vec3 targetLocal = scriptLocalOrigin.add(requested);
            int bestIndex = -1;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int i = 0; i < def.getBogies().size(); i++) {
                Vec3 bogiePos = def.getBogies().get(i).position();
                double dx = bogiePos.x - targetLocal.x;
                double dy = bogiePos.y - targetLocal.y;
                double dz = bogiePos.z - targetLocal.z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0 || bestDistance > 0.35D * 0.35D) {
                return requested;
            }

            VehicleDefinition.BogieDefinition bogie = def.getBogies().get(bestIndex);
            Vec3 corrected = train.getBogieRenderOffset(bestIndex, bogie, train.getYRot(), 1.0F);
            return corrected.subtract(scriptLocalOrigin);
        }

        public void rotate(float angle, float x, float y, float z) {
            // NaN/Infinite ガード
            if (!Float.isFinite(angle) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
            recordOp(OP_ROTATE_FREE, angle, x, y, z, 0, null, ' ');
            if (poseStack == null) {
                return;
            }
            // 角度 0 はノーオペ - SL の rod 計算が 0 度になるフレームが多い
            if (angle == 0.0f) return;
            if (x == 1.0f && y == 0.0f && z == 0.0f) {
                poseStack.mulPose(Axis.XP.rotationDegrees(angle));
            } else if (x == 0.0f && y == 1.0f && z == 0.0f) {
                poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            } else if (x == 0.0f && y == 0.0f && z == 1.0f) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
            }
            // 任意軸は未サポート (RTM スクリプトでは使われない)
        }

        public void rotate(double angle, String axis, double originX, double originY, double originZ) {
            if (poseStack == null || axis == null || axis.isBlank()) {
                return;
            }
            // NaN/Infinite ガード
            if (!Double.isFinite(angle) || !Double.isFinite(originX)
                || !Double.isFinite(originY) || !Double.isFinite(originZ)) return;

            float a = (float) angle;
            float x = (float) originX;
            float y = (float) originY;
            float z = (float) originZ;
            // 内部で translate + rotate(float, ...) + translate を呼ぶ。それぞれ各メソッドが
            // recordOp するため、ここでは追加 record しない (二重録画回避)。

            translate(x, y, z);
            switch (axis.trim().toUpperCase()) {
                case "X" -> rotate(a, 1.0f, 0.0f, 0.0f);
                case "Y" -> rotate(a, 0.0f, 1.0f, 0.0f);
                case "Z" -> rotate(a, 0.0f, 0.0f, 1.0f);
                default -> {
                    RealTrainModUnofficial.LOGGER.warn("Unsupported rotate axis in script: {}", axis);
                }
            }
            translate(-x, -y, -z);
        }

        public void scale(float x, float y, float z) {
            // NaN/Infinite/Zero ガード: スケール 0 や NaN は matrix を壊す
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
            if (x == 0.0f || y == 0.0f || z == 0.0f) return;
            if (poseStack != null) {
                poseStack.scale(x, y, z);
            }
            recordOp(OP_SCALE, x, y, z, 0, 0, null, ' ');
        }

        private static List<String> extractGroupNames(Object groups) {
            if (groups == null) {
                return Collections.emptyList();
            }
            if (groups instanceof String s) {
                return expandSerializedGroupNames(s);
            }
            if (groups instanceof java.util.Collection<?> collection) {
                return collection.stream()
                    .flatMap(value -> expandSerializedGroupNames(String.valueOf(value)).stream())
                    .collect(Collectors.toList());
            }
            if (groups.getClass().isArray()) {
                Object[] arr = (Object[]) groups;
                return java.util.Arrays.stream(arr)
                    .flatMap(value -> expandSerializedGroupNames(String.valueOf(value)).stream())
                    .collect(Collectors.toList());
            }
            if (groups instanceof Map<?, ?> map) {
                Object lengthValue = map.get("length");
                if (lengthValue instanceof Number lengthNumber) {
                    int length = lengthNumber.intValue();
                    List<String> result = new ArrayList<>();
                    for (int i = 0; i < length; i++) {
                        Object value = map.get(String.valueOf(i));
                        if (value != null) {
                            result.addAll(expandSerializedGroupNames(String.valueOf(value)));
                        }
                    }
                    return result;
                }
                return map.values().stream()
                    .flatMap(value -> expandSerializedGroupNames(String.valueOf(value)).stream())
                    .collect(Collectors.toList());
            }
            List<String> scriptObjectGroups = extractScriptObjectGroupNames(groups);
            if (!scriptObjectGroups.isEmpty()) {
                return scriptObjectGroups;
            }
            return expandSerializedGroupNames(groups.toString());
        }

        private static List<String> expandSerializedGroupNames(String raw) {
            if (raw == null) {
                return Collections.emptyList();
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                return Collections.emptyList();
            }
            if (!trimmed.contains(",") && !(trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                return List.of(trimmed);
            }
            String normalized = trimmed;
            if (normalized.startsWith("[") && normalized.endsWith("]")) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            List<String> result = new ArrayList<>();
            for (String token : normalized.split("\\s*,\\s*")) {
                String candidate = token.trim();
                if ((candidate.startsWith("\"") && candidate.endsWith("\""))
                    || (candidate.startsWith("'") && candidate.endsWith("'"))) {
                    candidate = candidate.substring(1, candidate.length() - 1).trim();
                }
                if (!candidate.isEmpty()) {
                    result.add(candidate);
                }
            }
            return result.isEmpty() ? List.of(trimmed) : result;
        }

        private static List<String> extractScriptObjectGroupNames(Object groups) {
            try {
                Class<?> type = groups.getClass();
                // First: try to read groupsStr from a Parts JS object.
                // Parts stores a pre-joined comma string as this.groupsStr so Java can read it
                // reliably via getMember even when the object is opaque to normal reflection.
                try {
                    java.lang.reflect.Method hasMember = type.getMethod("hasMember", String.class);
                    java.lang.reflect.Method getMember = type.getMethod("getMember", String.class);
                    if (Boolean.TRUE.equals(hasMember.invoke(groups, "groupsStr"))) {
                        Object strVal = getMember.invoke(groups, "groupsStr");
                        if (strVal instanceof String s && !s.isBlank()) {
                            List<String> result = expandSerializedGroupNames(s);
                            if (!result.isEmpty()) {
                                return result;
                            }
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                } catch (Exception ignored) {
                }

                java.lang.reflect.Method isArrayMethod = null;
                try {
                    isArrayMethod = type.getMethod("isArray");
                } catch (NoSuchMethodException ignored) {
                }
                if (isArrayMethod != null && Boolean.TRUE.equals(isArrayMethod.invoke(groups))) {
                    Integer length = getScriptObjectLength(groups);
                    if (length != null) {
                        return getScriptObjectElements(groups, length);
                    }
                }

                Integer length = getScriptObjectLength(groups);
                if (length != null) {
                    return getScriptObjectElements(groups, length);
                }
            } catch (Exception ignored) {
            }
            return Collections.emptyList();
        }

        private static Integer getScriptObjectLength(Object groups) {
            try {
                Class<?> type = groups.getClass();
                java.lang.reflect.Method hasMember = type.getMethod("hasMember", String.class);
                java.lang.reflect.Method getMember = type.getMethod("getMember", String.class);
                if (Boolean.TRUE.equals(hasMember.invoke(groups, "length"))) {
                    Object lengthValue = getMember.invoke(groups, "length");
                    if (lengthValue instanceof Number number) {
                        return number.intValue();
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
            }

            try {
                Class<?> type = groups.getClass();
                java.lang.reflect.Method get = type.getMethod("get", Object.class);
                java.lang.reflect.Method lengthMethod = type.getMethod("length");
                Object lengthValue = lengthMethod.invoke(groups);
                if (lengthValue instanceof Number number) {
                    return number.intValue();
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
            }

            return null;
        }

        private static List<String> getScriptObjectElements(Object groups, int length) {
            List<String> result = new ArrayList<>();
            try {
                Class<?> type = groups.getClass();
                java.lang.reflect.Method getMember = type.getMethod("getMember", String.class);
                for (int i = 0; i < length; i++) {
                    Object value = getMember.invoke(groups, String.valueOf(i));
                    if (value != null) {
                        result.add(value.toString());
                    }
                }
                return result;
            } catch (NoSuchMethodException e) {
                try {
                    Class<?> type = groups.getClass();
                    java.lang.reflect.Method get = type.getMethod("get", Object.class);
                    for (int i = 0; i < length; i++) {
                        Object value = get.invoke(groups, i);
                        if (value != null) {
                            result.add(value.toString());
                        }
                    }
                    return result;
                } catch (Exception ignored) {
                }
            } catch (Exception ignored) {
            }
            return Collections.emptyList();
        }

        // ---- NPC biped animation ----

        public void setRotationAngles(Object entity, Object partialTick) {
            float pt = partialTick instanceof Number n ? n.floatValue() : 0.0F;
            float speed = 0.0F;
            boolean onGround = true;
            if (entity instanceof LegacyScriptExecutor exec) {
                speed = Math.abs(exec.getSpeed()) / 72.0F;
                onGround = exec.isOnGround();
            } else if (entity instanceof TrainEntity train) {
                speed = Math.abs(train.getSpeed());
            }
            float swingProgress = Math.min(1.0F, speed * 10.0F);
            float swing = Mth.cos(swingProgress * (float) Math.PI * 0.6662F) * 2.0F;
            headAngleX = 0.0F;
            headAngleY = 0.0F;
            headAngleZ = 0.0F;
            bodyAngleX = 0.0F;
            bodyAngleY = 0.0F;
            bodyAngleZ = 0.0F;
            rightArmAngleX = swing * 0.5F;
            rightArmAngleY = 0.0F;
            rightArmAngleZ = 0.0F;
            leftArmAngleX = -swing * 0.5F;
            leftArmAngleY = 0.0F;
            leftArmAngleZ = 0.0F;
            rightLegAngleX = -swing * 0.5F;
            rightLegAngleY = 0.0F;
            rightLegAngleZ = 0.0F;
            leftLegAngleX = swing * 0.5F;
            leftLegAngleY = 0.0F;
            leftLegAngleZ = 0.0F;
        }

        public void rotateAndRender(Object parts, double pivotX, double pivotY, double pivotZ,
                                    double angleX, double angleY, double angleZ) {
            if (poseStack == null) return;
            pushMatrix();
            translate((float) pivotX, (float) pivotY, (float) pivotZ);
            if (angleZ != 0) poseStack.mulPose(Axis.ZP.rotationDegrees((float) angleZ));
            if (angleY != 0) poseStack.mulPose(Axis.YP.rotationDegrees((float) angleY));
            if (angleX != 0) poseStack.mulPose(Axis.XP.rotationDegrees((float) angleX));
            translate(-(float) pivotX, -(float) pivotY, -(float) pivotZ);
            renderParts(parts);
            popMatrix();
        }

        // ---- Entity position/orientation getters (renderer.getX(entity) etc.) ----

        public double getX(Object entity) {
            if (entity instanceof net.minecraft.world.entity.Entity e) return e.getX();
            if (entity instanceof net.minecraft.world.level.block.entity.BlockEntity be)
                return be.getBlockPos().getX();
            if (entity instanceof LegacyScriptExecutor exec) return exec.getX();
            return 0.0;
        }

        public double getY(Object entity) {
            if (entity instanceof net.minecraft.world.entity.Entity e) return e.getY();
            if (entity instanceof net.minecraft.world.level.block.entity.BlockEntity be)
                return be.getBlockPos().getY();
            if (entity instanceof LegacyScriptExecutor exec) return exec.getY();
            return 0.0;
        }

        public double getZ(Object entity) {
            if (entity instanceof net.minecraft.world.entity.Entity e) return e.getZ();
            if (entity instanceof net.minecraft.world.level.block.entity.BlockEntity be)
                return be.getBlockPos().getZ();
            if (entity instanceof LegacyScriptExecutor exec) return exec.getZ();
            return 0.0;
        }

        public float getYaw(Object entity) {
            if (entity instanceof net.minecraft.world.entity.Entity e) return e.getYRot();
            if (entity instanceof LegacyScriptExecutor exec) return exec.getYaw();
            return 0.0F;
        }

        public float getPitch(Object entity) {
            if (entity instanceof net.minecraft.world.entity.Entity e) return e.getXRot();
            if (entity instanceof LegacyScriptExecutor exec) return exec.getPitch();
            return 0.0F;
        }

        public Object getWorld(Object entity) {
            if (entity instanceof net.minecraft.world.entity.Entity e) return e.level();
            if (entity instanceof net.minecraft.world.level.block.entity.BlockEntity be) return be.getLevel();
            if (entity instanceof LegacyScriptExecutor exec) return exec.getWorld();
            return null;
        }

        // ---- Block/entity state queries ----

        public boolean isPowered(Object entity) {
            if (entity instanceof InstalledObjectBlockEntity be) return be.isPowered();
            if (entity instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                net.minecraft.world.level.Level lvl = be.getLevel();
                if (lvl != null) return lvl.hasNeighborSignal(be.getBlockPos());
            }
            return false;
        }

        public boolean isOpaqueCube(Object entity) { return false; }

        public boolean isRidden(Object entity) {
            if (entity instanceof net.minecraft.world.entity.Entity e)
                return !e.getPassengers().isEmpty();
            return false;
        }

        public int getLodState(Object entity) { return 0; }

        /**
         * 本家の設置物 meta = クリックした面 (0-5)。RenderSearchLight.js 等が
         * 壁付け時に樽の傾きを打ち消すのに使う (0 固定だと二重に傾く)。
         */
        public int getMetadata(Object entity) {
            if (entity instanceof InstalledObjectBlockEntity be) {
                int face = be.getMountFace();
                return face >= 0 ? face : 1;
            }
            return 0;
        }

        public boolean isSwitchRail(Object entity) {
            if (entity instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                net.minecraft.world.level.Level lvl = be.getLevel();
                if (lvl != null) {
                    net.minecraft.world.level.block.state.BlockState state = lvl.getBlockState(be.getBlockPos());
                    String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    return blockId.contains("switch") || blockId.contains("point");
                }
            }
            return false;
        }

        // ---- Rail/wire rendering helpers (stubs for scripts that call these) ----

        public void renderStaticParts(Object entity, double posX, double posY, double posZ) {
            // renderParts with null renders all groups — serves as "render static (non-moving) parts"
            if (mqoModel != null && poseStack != null && buffer != null) {
                renderParts(scriptRegisteredGroups.isEmpty() ? "base" : scriptRegisteredGroups.iterator().next());
            }
        }

        public void renderLightEffect(Object entity, double x, double y, double z,
                                      double sizeX, double sizeY, double sizeZ,
                                      Object normal, int color, float alpha) {}
        public void renderLightEffect(Object entity, double x, double y, double z,
                                      double sizeX, double sizeZ, Object normal, int color, float alpha) {}
        public void renderLightEffect(Object... args) {}

        public void renderRailMapStatic(Object... args) {}

        public void renderWireDeflection(Object... args) {}

        // ---- Brightness / lighting helpers ----

        public float getBrightness(Object world, double x, double y, double z) {
            return 1.0F;
        }

        public float getBrightness(Object world, int x, int y, int z) {
            return 1.0F;
        }

        // ---- Light position and surface normal helpers (stubs for catenary scripts) ----

        public float[] getLightPos(Object entity, double offX, double offY, double offZ,
                                   double offYaw, double yaw) {
            double ex = getX(entity) + offX;
            double ey = getY(entity) + offY;
            double ez = getZ(entity) + offZ;
            return new float[]{ (float) ex, (float) ey, (float) ez };
        }

        public float[] getNormal(Object entity, double nx, double ny, double nz,
                                 double pitch, double yaw) {
            return new float[]{ (float) nx, (float) ny, (float) nz };
        }

        // ---- Rotation by Axis enum value ----

        public float getRotation(Object entity, int axisId) {
            if (entity instanceof InstalledObjectBlockEntity be) {
                switch (axisId) {
                    case 3, 4 -> { return be.getYaw(); } // POSITIVE_Y / NEGATIVE_Y
                    default   -> { return 0.0F; }
                }
            }
            if (entity instanceof LegacyScriptExecutor exec) {
                switch (axisId) {
                    case 3, 4 -> { return exec.getYaw(); }
                    case 1, 2 -> { return exec.getPitch(); }
                    default   -> { return 0.0F; }
                }
            }
            return 0.0F;
        }

        // ---- Inventory helpers (stubs) ----

        public Object getInventoryItem(Object entity, int slot) { return null; }

        public int getStackSize(Object stack) { return stack == null ? 0 : 1; }

        public void renderItem(Object entity, Object item) {}

        // ---- Player camera / view ----

        public float getPlayerYaw() {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) return mc.player.getYRot();
            } catch (Exception ignored) {}
            return 0.0F;
        }

        // ---- System clock helpers ----

        public long getSystemTimeMillis() {
            jp.ngt.rtm.render.PartsRenderer.markTimeAccessed();
            return System.currentTimeMillis();
        }

        public int getSystemHour() {
            return java.time.LocalTime.now().getHour();
        }

        public int getSystemMinute() {
            return java.time.LocalTime.now().getMinute();
        }

        public int getSystemSecond() {
            return java.time.LocalTime.now().getSecond();
        }

        public int getSystemMillisecond() {
            return (int) (System.currentTimeMillis() % 1000L);
        }

        // ---- Color as int (returned by renderer.getColor(entity)) ----

        public int getColor(Object entity) { return 0xFFFFFF; }
    }

    /** GL11即時モード関数の最小シム。poseStack操作へブリッジする。 */
    // ★JS 再実装の受け皿だった互換クラス群は撤去 (実シム + PRELUDE へ一本化)。

    /** new Parts(...)用ビルダー。registerParts時にグループ名を抽出する。 */
    // ★JS 再実装の受け皿だった互換クラス群は撤去 (実シム + PRELUDE へ一本化)。

    /** NGTTextスタブ。各メソッドを1個の定義に統一する。 */
    // ★JS 再実装の受け皿だった互換クラス群は撤去 (実シム + PRELUDE へ一本化)。

    /** RTM 原作の jp.ngt.ngtlib.io.NGTLog スタブ。debug/info/warn/error を Java の logger に橋渡し。 */
    // ★JS 再実装の受け皿だった互換クラス群は撤去 (実シム + PRELUDE へ一本化)。

    /** RTM 原作の jp.ngt.ngtlib.util.NGTUtil スタブ。 */
    // ★JS 再実装の受け皿だった互換クラス群は撤去 (実シム + PRELUDE へ一本化)。

    /** RTM 原作の jp.ngt.ngtlib.math.NGTMath スタブ。 */
    // ★JS 再実装の受け皿だった互換クラス群は撤去 (実シム + PRELUDE へ一本化)。
}
