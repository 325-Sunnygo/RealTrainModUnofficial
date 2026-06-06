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
    private static final String[] PREFERRED_ECMA_VERSIONS = {"2024", "2023", "2022"};
    private static final String SCRIPT_PATH_KEY = "__ptScriptPath";

    /**
     * ユーザースクリプトの先頭に prepend する JS。
     * Nashorn の Java overloaded method 解決が不安定で `NGTText.readText is not a function`
     * になる事例が頻発するため、純 JS で NGTText 等をオブジェクトとして定義する。
     * readText は List<String> を期待されるので空 ArrayList を返す（no-op として）。
     * importPackage も併せて no-op に上書きしておく（ユーザースクリプトが書き換える前に確立）。
     */
    private static final String LEGACY_API_PREPEND =
        "importPackage = function(p) {};\n" +
        "importClass = function(c) {};\n" +
        // RTM 1.7.10 互換: スクリプトで instanceof チェックされる旧クラス名を、1.21 NeoForge の対応クラスに alias する。
        "try { EntityPlayer = Java.type('net.minecraft.world.entity.player.Player'); } catch(e) { EntityPlayer = function() {}; }\n" +
        "try { Entity = Java.type('net.minecraft.world.entity.Entity'); } catch(e) {}\n" +
        "try { EntityLivingBase = Java.type('net.minecraft.world.entity.LivingEntity'); } catch(e) { EntityLivingBase = function() {}; }\n" +
        "try { World = Java.type('net.minecraft.world.level.Level'); } catch(e) { World = function() {}; }\n" +
        "try { ItemStack = Java.type('net.minecraft.world.item.ItemStack'); } catch(e) { ItemStack = function() {}; }\n" +
        "try { Block = Java.type('net.minecraft.world.level.block.Block'); } catch(e) { Block = function() {}; }\n" +
        "try { Item = Java.type('net.minecraft.world.item.Item'); } catch(e) { Item = function() {}; }\n" +
        // RTM 系の旧クラス名は no-op コンストラクタにする (instanceof は常に false になる)
        "if (typeof EntityVehiclePart === 'undefined') EntityVehiclePart = function() {};\n" +
        "if (typeof EntityTrainBase === 'undefined') EntityTrainBase = function() {};\n" +
        "if (typeof EntityBogie === 'undefined') EntityBogie = function() {};\n" +
        // Packages.jp.ngt.ngtlib.math.Vec3 のシム実装。
        // 原作の Vec3 は sub/add/rotateAroundY/getX/getY/getZ/length 等のメソッドを持つ。
        // Packages プロキシ経由だと no-op になるので、JS で独自実装する。
        "(function() {\n" +
        "  if (typeof Packages === 'undefined') return;\n" +
        "  if (!Packages.jp) Packages.jp = {};\n" +
        "  if (!Packages.jp.ngt) Packages.jp.ngt = {};\n" +
        "  if (!Packages.jp.ngt.ngtlib) Packages.jp.ngt.ngtlib = {};\n" +
        "  if (!Packages.jp.ngt.ngtlib.math) Packages.jp.ngt.ngtlib.math = {};\n" +
        "  var Vec3Impl = function(x, y, z) {\n" +
        "    this.x = +x || 0; this.y = +y || 0; this.z = +z || 0;\n" +
        "  };\n" +
        "  Vec3Impl.prototype.getX = function() { return this.x; };\n" +
        "  Vec3Impl.prototype.getY = function() { return this.y; };\n" +
        "  Vec3Impl.prototype.getZ = function() { return this.z; };\n" +
        "  Vec3Impl.prototype.add = function(o) { return new Vec3Impl(this.x + o.x, this.y + o.y, this.z + o.z); };\n" +
        "  Vec3Impl.prototype.sub = function(o) { return new Vec3Impl(this.x - o.x, this.y - o.y, this.z - o.z); };\n" +
        "  Vec3Impl.prototype.subtract = function(o) { return this.sub(o); };\n" +
        "  Vec3Impl.prototype.scale = function(s) { return new Vec3Impl(this.x * s, this.y * s, this.z * s); };\n" +
        "  Vec3Impl.prototype.multiply = function(s) { return this.scale(s); };\n" +
        "  Vec3Impl.prototype.dot = function(o) { return this.x*o.x + this.y*o.y + this.z*o.z; };\n" +
        "  Vec3Impl.prototype.length = function() { return Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z); };\n" +
        "  Vec3Impl.prototype.lengthSquared = function() { return this.x*this.x + this.y*this.y + this.z*this.z; };\n" +
        "  Vec3Impl.prototype.normalize = function() { var l = this.length(); return l > 0 ? new Vec3Impl(this.x/l, this.y/l, this.z/l) : new Vec3Impl(0,0,0); };\n" +
        "  Vec3Impl.prototype.distanceTo = function(o) { return this.sub(o).length(); };\n" +
        // NGT Vec3.getYaw/getPitch 互換(SRB の help 表示やマーカー方向計算で使用)。
        "  Vec3Impl.prototype.getYaw = function() { return Math.atan2(this.x, this.z) * 180 / Math.PI; };\n" +
        "  Vec3Impl.prototype.getPitch = function() { return Math.atan2(this.y, Math.sqrt(this.x*this.x + this.z*this.z)) * 180 / Math.PI; };\n" +
        "  Vec3Impl.prototype.rotateAroundX = function(deg) {\n" +
        "    var r = deg * Math.PI / 180; var c = Math.cos(r), s = Math.sin(r);\n" +
        "    return new Vec3Impl(this.x, c*this.y - s*this.z, s*this.y + c*this.z);\n" +
        "  };\n" +
        "  Vec3Impl.prototype.rotateAroundY = function(deg) {\n" +
        "    var r = deg * Math.PI / 180; var c = Math.cos(r), s = Math.sin(r);\n" +
        "    return new Vec3Impl(c*this.x + s*this.z, this.y, -s*this.x + c*this.z);\n" +
        "  };\n" +
        "  Vec3Impl.prototype.rotateAroundZ = function(deg) {\n" +
        "    var r = deg * Math.PI / 180; var c = Math.cos(r), s = Math.sin(r);\n" +
        "    return new Vec3Impl(c*this.x - s*this.y, s*this.x + c*this.y, this.z);\n" +
        "  };\n" +
        // Packages 経由の代入は JavaPackage プロキシだと throw する可能性があるので try で隔離。
        "  try { Packages.jp.ngt.ngtlib.math.Vec3 = Vec3Impl; } catch (e) {}\n" +
        // 無条件で Vec3 グローバルを上書きする。
        "  try { Vec3 = Vec3Impl; } catch (e) {}\n" +
        "  try { this.Vec3 = Vec3Impl; } catch (e) {}\n" +
        "  try { (function() { Vec3 = Vec3Impl; }).call(this); } catch (e) {}\n" +  // this = global object in non-strict mode
        "})();\n" +
        // 念のため IIFE 外でも Vec3 をグローバルに割り当てる(IIFE 内の代入が global に届かない実装に備える)。
        "var __Vec3Impl = (function(){\n" +
        "  var V = function(x, y, z) { this.x = +x || 0; this.y = +y || 0; this.z = +z || 0; };\n" +
        "  V.prototype.getX = function() { return this.x; };\n" +
        "  V.prototype.getY = function() { return this.y; };\n" +
        "  V.prototype.getZ = function() { return this.z; };\n" +
        "  V.prototype.add = function(o) { return new V(this.x+o.x, this.y+o.y, this.z+o.z); };\n" +
        "  V.prototype.sub = function(o) { return new V(this.x-o.x, this.y-o.y, this.z-o.z); };\n" +
        "  V.prototype.subtract = function(o) { return this.sub(o); };\n" +
        "  V.prototype.scale = function(s) { return new V(this.x*s, this.y*s, this.z*s); };\n" +
        "  V.prototype.multiply = function(s) { return this.scale(s); };\n" +
        // NGT Vec3.multi(d): スカラー倍。Vec3 を渡された場合は成分積。
        "  V.prototype.multi = function(s) { if (s && typeof s === 'object') return new V(this.x*(s.x||0), this.y*(s.y||0), this.z*(s.z||0)); return new V(this.x*s, this.y*s, this.z*s); };\n" +
        "  V.prototype.copy = function() { return new V(this.x, this.y, this.z); };\n" +
        "  V.prototype.dot = function(o) { return this.x*o.x + this.y*o.y + this.z*o.z; };\n" +
        "  V.prototype.length = function() { return Math.sqrt(this.x*this.x + this.y*this.y + this.z*this.z); };\n" +
        "  V.prototype.lengthSquared = function() { return this.x*this.x + this.y*this.y + this.z*this.z; };\n" +
        "  V.prototype.normalize = function() { var l = this.length(); return l > 0 ? new V(this.x/l, this.y/l, this.z/l) : new V(0,0,0); };\n" +
        "  V.prototype.distanceTo = function(o) { return this.sub(o).length(); };\n" +
        "  V.prototype.getYaw = function() { return Math.atan2(this.x, this.z) * 180 / Math.PI; };\n" +
        "  V.prototype.getPitch = function() { return Math.atan2(this.y, Math.sqrt(this.x*this.x + this.z*this.z)) * 180 / Math.PI; };\n" +
        "  V.prototype.rotateAroundX = function(deg) { var r = deg*Math.PI/180, c = Math.cos(r), s = Math.sin(r); return new V(this.x, c*this.y - s*this.z, s*this.y + c*this.z); };\n" +
        "  V.prototype.rotateAroundY = function(deg) { var r = deg*Math.PI/180, c = Math.cos(r), s = Math.sin(r); return new V(c*this.x + s*this.z, this.y, -s*this.x + c*this.z); };\n" +
        "  V.prototype.rotateAroundZ = function(deg) { var r = deg*Math.PI/180, c = Math.cos(r), s = Math.sin(r); return new V(c*this.x - s*this.y, s*this.x + c*this.y, this.z); };\n" +
        "  return V;\n" +
        "})();\n" +
        "var Vec3 = __Vec3Impl;\n" +
        "NGTText = {\n" +
        // 空 ArrayList を返すと sound_includeSoundLib の eval が no-op になり、
        // onUpdate が再定義されないまま onUpdate(su) を再帰呼出して StackOverflow する。
        // dummy の onUpdate/onUpdate2 定義を1要素入れて、eval で no-op 化させる。
        "  readText: function(r) { var l = new java.util.ArrayList(); l.add('function onUpdate(su) {} function onUpdate2(su) {} function tick(e) {} function update(e,pt) {}'); return l; },\n" +
        "  readTextLines: function(r) { return []; },\n" +
        "  writeText: function() {},\n" +
        "  loadText: function() { return ''; },\n" +
        "  createText: function() { return ''; },\n" +
        "  getText: function() { return ''; },\n" +
        "  getFormattedText: function() { return ''; },\n" +
        "  getString: function() { return ''; },\n" +
        "  appendSibling: function() {},\n" +
        "  appendText: function() {},\n" +
        "  applyTextStyles: function() {}\n" +
        "};\n" +
        "NGTLog = { debug: function() {}, info: function() {}, warn: function() {}, error: function() {}, sendChatMessage: function(player, msg){ try{ if(typeof __SRB__!=='undefined'&&__SRB__) __SRB__.chat(player, ''+msg); }catch(e){} }, sendChatMessageToAll: function(msg){ try{ if(typeof __SRB__!=='undefined'&&__SRB__) __SRB__.chat((typeof __RTMU_MC__!=='undefined'&&__RTMU_MC__)?__RTMU_MC__.getPlayer():null, ''+msg); }catch(e){} } };\n" +
        "NGTUtil = { getCurrentTime: function() { return java.lang.System.currentTimeMillis(); }, getUniqueId: function() { return java.lang.System.nanoTime(); }, isClient: function() { return true; }, getCurrentWorld: function() { return null; }, getCurrentPlayer: function() { return null; }, getMCVersion: function() { return '1.21.1'; }, isLanguage: function() { return false; } };\n" +
        "NGTMath = { toRadians: function(d) { return d * Math.PI / 180.0; }, toDegrees: function(r) { return r * 180.0 / Math.PI; }, sin: Math.sin, cos: Math.cos, tan: Math.tan, atan2: Math.atan2, sqrt: Math.sqrt, floor: Math.floor, ceil: Math.ceil, clamp: function(v,a,b) { return Math.max(a, Math.min(b, v)); }, normalizeAngle: function(a) { while(a>=180)a-=360; while(a<-180)a+=360; return a; } };\n" +
        // ModelPackManager: スクリプトの sound lib include で頻出するので空 stub
        "if (typeof ModelPackManager === 'undefined') ModelPackManager = { INSTANCE: { getResource: function() { return null; }, getModel: function() { return null; } } };\n" +
        // RTM 拡張パックで使われる TrainControllerManager (ATO/ATC など) のスタブ。
        // 実装は無いので getController は空オブジェクトを返し、null チェックが効くようにする。
        "var __ptCtl = function() { return { isActive: false, stopDistance: 0, targetSpeed: 0, mode: 0, value: 0, isEnable: function() { return false; }, isEnabled: function() { return false; }, isWorking: function() { return false; }, isValid: function() { return false; }, getMode: function() { return 0; }, getState: function() { return 0; }, getTargetSpeed: function() { return 0; }, getTargetDistance: function() { return 0; }, getDistance: function() { return 0; }, getStopDistance: function() { return 0; }, getSpeed: function() { return 0; }, getValue: function() { return 0; }, setTarget: function() {}, setEnable: function() {}, setEnabled: function() {}, enable: function() {}, disable: function() {}, update: function() {}, reset: function() {} }; };\n" +
        "var __ptPatchController = function(c) { c = c || {}; if (!c.tascController || typeof c.tascController.isEnable !== 'function') c.tascController = __ptCtl(); if (!c.atoController || typeof c.atoController.isEnable !== 'function') c.atoController = __ptCtl(); if (!c.atsController || typeof c.atsController.isEnable !== 'function') c.atsController = __ptCtl(); if (!c.atcController || typeof c.atcController.isEnable !== 'function') c.atcController = __ptCtl(); if (typeof c.isEnable !== 'function') c.isEnable = function() { return false; }; if (typeof c.isEnabled !== 'function') c.isEnabled = function() { return false; }; if (typeof c.getSpeedLimit !== 'function') c.getSpeedLimit = function() { return 0; }; if (!c.speedOrderList) c.speedOrderList = []; return c; };\n" +
        "if (typeof TrainControllerManager === 'undefined' || !TrainControllerManager) {\n" +
        // 空ではなくダミーオブジェクトを返す。getTrainController(entity).tascController などへの
        // 連鎖アクセスが頻出するため、null 返しだと「Cannot get property X of null」で死ぬ。
        // 各サブコントローラ共通のダミーメソッド群。スクリプト(SD8200 等)は isEnable() など
        // 多様なメソッドを呼ぶため、未定義だと「X is not a function」で server script が毎tick死ぬ。
        // 真偽系は false、数値系は 0、設定/更新系は no-op を返す広めの stub にして根本的に潰す。
        "  var __ptDummyController = {\n" +
        "    tascController: __ptCtl(),\n" +
        "    atoController: __ptCtl(),\n" +
        "    atsController: __ptCtl(),\n" +
        "    atcController: __ptCtl(),\n" +
        "    isActive: false,\n" +
        "    speedOrderList: [],\n" +
        "    isEnable: function() { return false; },\n" +
        "    isEnabled: function() { return false; },\n" +
        "    update: function() {},\n" +
        "    getState: function() { return 0; },\n" +
        "    getSpeedLimit: function() { return 0; },\n" +
        "    getSpeed: function() { return 0; }\n" +
        "  };\n" +
        "  TrainControllerManager = {\n" +
        "    INSTANCE: {\n" +
        "      getController: function() { return __ptDummyController; },\n" +
        "      getTrainController: function() { return __ptDummyController; },\n" +
        "      registerController: function() {},\n" +
        "      unregisterController: function() {},\n" +
        "      hasController: function() { return false; }\n" +
        "    },\n" +
        "    getController: function() { return __ptDummyController; },\n" +
        "    getTrainController: function() { return __ptDummyController; }\n" +
        "  };\n" +
        "}\n" +
        "try { var __ptOldGetTC = TrainControllerManager.getTrainController; TrainControllerManager.getTrainController = function(e) { try { return __ptPatchController(__ptOldGetTC ? __ptOldGetTC(e) : null); } catch (ex) { return __ptPatchController(null); } }; } catch (e) { TrainControllerManager.getTrainController = function() { return __ptPatchController(null); }; }\n" +
        "try { var __ptOldGetC = TrainControllerManager.getController; TrainControllerManager.getController = function(e) { try { return __ptPatchController(__ptOldGetC ? __ptOldGetC(e) : null); } catch (ex) { return __ptPatchController(null); } }; } catch (e) { TrainControllerManager.getController = function() { return __ptPatchController(null); }; }\n" +
        // 重要: LEGACY_API_PREPEND は user script と同じ eval 内で先頭に prepend される。
        // injectScriptCompatibility() の別 eval で定義した var NGTMath は別 binding に
        // 閉じてしまうことがあり、user script 実行時に「NGTMath.getSin is not a function」
        // になる事例 (C12 render_rod) があるため、ここで pure JS の NGTMath を定義し直す。
        // user script の importPackage(Packages.jp.ngt.ngtlib.math) を no-op 化済みなので
        // この再代入が user script より前に確実に効く。\n
        "var NGTMath = {\n" +
        "  toRadians: function(deg) { return deg * Math.PI / 180; },\n" +
        "  toDegrees: function(rad) { return rad * 180 / Math.PI; },\n" +
        "  getSin: function(rad) { return Math.sin(rad); },\n" +
        "  getCos: function(rad) { return Math.cos(rad); },\n" +
        "  getTan: function(rad) { return Math.tan(rad); },\n" +
        "  getAtan2: function(y, x) { return Math.atan2(y, x); },\n" +
        "  getSqrt: function(x) { return Math.sqrt(x); },\n" +
        "  sin: function(rad) { return Math.sin(rad); },\n" +
        "  cos: function(rad) { return Math.cos(rad); },\n" +
        "  tan: function(rad) { return Math.tan(rad); },\n" +
        "  atan2: function(y, x) { return Math.atan2(y, x); },\n" +
        "  sqrt: function(x) { return Math.sqrt(x); },\n" +
        "  floor: function(x) { return Math.floor(x); },\n" +
        "  ceil: function(x) { return Math.ceil(x); },\n" +
        "  clamp: function(v, a, b) { return Math.max(a, Math.min(b, v)); },\n" +
        "  normalizeAngle: function(a) { while(a>=180)a-=360; while(a<-180)a+=360; return a; }\n" +
        "};\n" +
        // NGTUtilClient / MCWrapperClient も user script と同じ eval で確実に定義する(別 eval の
        // 定義は見えないため)。getMinecraft は __RTMU_MC__(クライアント実体)へ橋渡し。
        "function __rtmuMcShim() { return { field_71462_r: ((typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getCurrentScreen() : null), func_135016_M: function() { return { func_135041_c: function() { return { func_135034_a: function() { return ((typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getLanguageCode() : 'en_us'); } }; } }; } }; }\n" +
        "var NGTUtilClient = { getMinecraft: function() { return __rtmuMcShim(); }, getPlayer: function() { try { return (typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getPlayer() : null; } catch (e) { return null; } }, bindTexture: function(t) { try { if (typeof renderer !== 'undefined' && renderer) renderer.bindTexture(t); } catch(e){} }, playSound: function(s){}, stopSound: function(s){} };\n" +
        "var MCWrapperClient = { getPlayer: function() { try { return (typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getPlayer() : null; } catch (e) { return null; } }, getMinecraft: function() { return __rtmuMcShim(); } };\n" +
        // RTM 共通ラッパー/レール系グローバルも user script eval で定義(別 eval の定義は見えないため)。
        // entity 位置などは null 安全に。レール系は SRB の preview/敷設で参照される。
        "function __srbNum(v){ return (v==null)?0:(v.doubleValue?v.doubleValue():v); }\n" +
        // マーカー描画の基準 entityX は entity のレンダー補間位置(renderPosX)。PoseStack の原点も
        // 同じ補間位置なので相殺し、固定マーカーは真のワールド座標に完全固定される(移動・補間でブレない)。
        // getX()(tick位置)を使うと原点(補間)とズレて固定アンカーがドリフトする。フォールバックで getX。
        "var MCWrapper = { getPosX: function(e){ try{ if(typeof __RTMU_MC__!=='undefined'&&__RTMU_MC__) return __RTMU_MC__.renderPosX(e); return e?e.getX():0; }catch(x){return e?e.getX():0;} }, getPosY: function(e){ try{ if(typeof __RTMU_MC__!=='undefined'&&__RTMU_MC__) return __RTMU_MC__.renderPosY(e); return e?e.getY():0; }catch(x){return e?e.getY():0;} }, getPosZ: function(e){ try{ if(typeof __RTMU_MC__!=='undefined'&&__RTMU_MC__) return __RTMU_MC__.renderPosZ(e); return e?e.getZ():0; }catch(x){return e?e.getZ():0;} }, getWorld: function(e){ try{ return e?e.level():null; }catch(x){return null;} } };\n" +
        // BlockUtil.getMOPFromPlayer = プレイヤー視線レイキャスト。RTM の MOP 形状(field_72307_f=hitVec, func_178782_a=BlockPos)で返す。
        "var BlockUtil = { setBlock: function() {},\n" +
        "  getMOPFromPlayer: function(player, dist, includeFluids){ try{ if(typeof __RTMU_MC__==='undefined'||!__RTMU_MC__) return null; var r=__RTMU_MC__.raycast(dist||512); if(!r) return null; var hx=r.getHitX(),hy=r.getHitY(),hz=r.getHitZ(); var bx=r.getBlockX(),by=r.getBlockY(),bz=r.getBlockZ(); return { field_72307_f:{ field_72450_a:hx, field_72448_b:hy, field_72449_c:hz }, field_72311_b:bx, field_72312_c:by, field_72309_d:bz, func_178782_a:function(){ return { func_177958_n:function(){return bx;}, func_177956_o:function(){return by;}, func_177952_p:function(){return bz;} }; } }; }catch(e){ return null; } } };\n" +
        "if (typeof Mouse === 'undefined') Mouse = { isButtonDown: function(b){ try{ return (typeof __RTMU_MC__!=='undefined'&&__RTMU_MC__)?__RTMU_MC__.isMouseDown(b|0):false; }catch(e){return false;} } };\n" +
        "if (typeof RTMBlock === 'undefined') RTMBlock = { marker: { __rtmuToken: 'marker' } };\n" +
        "if (typeof RTMItem === 'undefined') RTMItem = { itemLargeRail: { __rtmuToken: 'itemLargeRail' } };\n" +
        "if (typeof RTMResource === 'undefined') RTMResource = { RAIL: { defaultName: 'default', __rtmuToken: 'RAIL' } };\n" +
        "if (typeof ResourceStateRail === 'undefined') ResourceStateRail = function(type, x){ this.type=type; this.modelId=''; this.setResourceName=function(n){ this.modelId=n; }; this.readFromNBT=function(nbt){}; this.writeToNBT=function(){ return (typeof NBTTagCompound!=='undefined')?new NBTTagCompound():{}; }; };\n" +
        "if (typeof ItemRail === 'undefined') ItemRail = { getProperty: function(item){ return null; } };\n" +
        // RTM サウンドクラスのスタブ。RTMU は車両音を別系統(soundScriptEngine)で鳴らすため、
        // 描画スクリプト内の MovingSoundTrain/NGTUtilClient.playSound は no-op で十分。これが無いと
        // ReferenceError → render が例外 → 3回でスクリプト無効化 → baked フォールバックで昼/夜ドア等が
        // 二重描画される(ゴースト)。func_147650_b は 1.12/1.21 両対応の resource ラッパーを返す。
        "if (typeof MovingSoundTrain === 'undefined') MovingSoundTrain = function(entity, resource, repeat, flag){ this.__res = resource; this.setVolume = function(v){}; this.setPitch = function(p){}; this.stop = function(){}; this.func_147650_b = function(){ var r=this.__res; return { func_110624_b:function(){ try{ return (r&&r.func_110624_b)?r.func_110624_b():((r&&r.getNamespace)?r.getNamespace():''); }catch(e){ return ''; } }, func_110623_a:function(){ try{ return (r&&r.func_110623_a)?r.func_110623_a():((r&&r.getPath)?r.getPath():''); }catch(e){ return ''; } } }; }; };\n" +
        "if (typeof MovingSoundTileEntity === 'undefined') MovingSoundTileEntity = MovingSoundTrain;\n" +
        "if (typeof MovingSound === 'undefined') MovingSound = MovingSoundTrain;\n" +
        "if (typeof RailDir === 'undefined') RailDir = { STRAIGHT: 0, NORTH: 0, EAST: 2, SOUTH: 4, WEST: 6 };\n" +
        "if (typeof NBTTagCompound === 'undefined') NBTTagCompound = function(){ this.__m={}; this.func_74782_a=function(k,v){ this.__m[k]=v; }; this.func_74775_l=function(k){ return this.__m[k]||new NBTTagCompound(); }; this.func_74778_a=function(k,v){ this.__m[k]=v; }; this.func_74779_i=function(k){ return this.__m[k]||''; }; };\n" +
        // TileEntityLargeRailBase は RTMU の LargeRailCoreBlockEntity 型に束ねる(Java.type)。
        // これで `tile instanceof TileEntityLargeRailBase` が実レールBEで true になり、レール接続検出が効く。
        "try { TileEntityLargeRailBase = Java.type('com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity'); } catch(e) { if (typeof TileEntityLargeRailBase === 'undefined') TileEntityLargeRailBase = function(){}; }\n" +
        "if (typeof TileEntitySign === 'undefined') TileEntitySign = function(){};\n" +
        // RailPosition は __SRB__ ブリッジ経由で RTMU の実 RailPosition を生成(new で返り値オブジェクトが採用される)。\n
        "if (typeof RailPosition === 'undefined') RailPosition = function(x,y,z,dir,type){ try{ return __SRB__.createRailPosition(x|0,y|0,z|0,dir|0,(type!=null?__srbNum(type):0),-1,0,0,0,0,0); }catch(e){ return { blockX:x,blockY:y,blockZ:z,direction:dir,switchType:type,anchorYaw:0,anchorPitch:0,anchorLengthHorizontal:-1,anchorLengthVertical:-1,cantCenter:0,cantEdge:0,height:0,setHeight:function(h){this.height=h;},init:function(){} }; } };\n" +
        // RailPosition.REVISION: 8方向の[dx,dz]オフセット(RTMU RailPosition.REVISION と同値)。render の getNearestEdgePos 等が参照。
        "RailPosition.REVISION = [[0.0,-0.5],[-0.5,-0.5],[-0.5,0.0],[-0.5,0.499999],[0.0,0.499999],[0.499999,0.499999],[0.499999,0.0],[0.499999,-0.5]];\n";
    private static final String SCRIPT_MODEL_KEY = "__ptScriptModel";
    private static volatile boolean graalPolyglotUnavailable = false;
    private static TrainScriptSystem instance;
    private ScriptEngine engine;
    private final Map<UUID, EntityScriptContext> entityContexts = new HashMap<>();
    private static final String SCRIPT_CORE_VERSION = "2.4.24";
    private static final Set<Integer> DISABLED_SCRIPT_ENGINES = ConcurrentHashMap.newKeySet();
    private static final Set<String> REPORTED_SCRIPT_ERRORS = ConcurrentHashMap.newKeySet();

    public static final class ScriptCoreCompat {
        @SuppressWarnings("unused")
        public final String VERSION = SCRIPT_CORE_VERSION;

        /**
         * Returns the version string exposed to old train scripts.
         */
        public String getVERSION() {
            return VERSION;
        }

        /**
         * Returns the version string exposed to old train scripts.
         */
        public String getVersion() {
            return VERSION;
        }
    }

    public static final class ScriptUtilCompat {
        public ScriptEngine doScript(String script) {
            return doScriptCompat(script);
        }

        public Object doScriptFunction(Object scriptEngine, String functionName, Object args) {
            return doScriptFunctionCompat(scriptEngine, functionName, args);
        }

        public Object doScriptIgnoreError(Object scriptEngine, String functionName, Object args) {
            return doScriptIgnoreErrorCompat(scriptEngine, functionName, args);
        }

        public Object getScriptField(Object scriptEngine, String fieldName) {
            return getScriptFieldCompat(scriptEngine, fieldName);
        }
    }

    private TrainScriptSystem() {
    }

    public static TrainScriptSystem getInstance() {
        if (instance == null) {
            instance = new TrainScriptSystem();
        }
        return instance;
    }

    public void initialize() {
        RealTrainModUnofficial.LOGGER.info("Initializing legacy Script System...");
        try {
            ScriptEngineManager manager = new ScriptEngineManager(Thread.currentThread().getContextClassLoader());
            engine = getAvailableScriptEngine(manager);
            if (engine == null) {
                RealTrainModUnofficial.LOGGER.info("Retrying script engine discovery with TrainScriptSystem class loader.");
                manager = new ScriptEngineManager(TrainScriptSystem.class.getClassLoader());
                engine = getAvailableScriptEngine(manager);
            }
            if (engine == null) {
                RealTrainModUnofficial.LOGGER.warn("JavaScript engine not available. Java 21 requires an external JS engine dependency such as Graal.js.");
            } else {
                RealTrainModUnofficial.LOGGER.info("JavaScript engine initialized successfully.");
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.error("Failed to initialize JavaScript engine: {}", e.getMessage(), e);
        }
    }

    public void setScriptEngine(ScriptEngine engine) {
        this.engine = engine;
    }

    public static ScriptEngine doScriptCompat(String script) {
        ScriptEngine scriptEngine = createScriptEngine();
        if (scriptEngine == null) {
            return null;
        }
        try {
            injectScriptCompatibility(scriptEngine, new ScriptModelRenderer(null, null));
            scriptEngine.eval(LEGACY_API_PREPEND + normalizeLegacyScriptReferences(script == null ? "" : script));
            return scriptEngine;
        } catch (Exception e) {
            throw new RuntimeException("Script exec error", e);
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
            RealTrainModUnofficial.LOGGER.debug("Ignoring legacy script function failure: {}", functionName, e);
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

    private static ScriptEngine createScriptEngine() {
        ScriptEngineManager manager = new ScriptEngineManager(Thread.currentThread().getContextClassLoader());
        ScriptEngine engine = getAvailableScriptEngine(manager);
        if (engine == null) {
            engine = getAvailableScriptEngine(new ScriptEngineManager(TrainScriptSystem.class.getClassLoader()));
        }
        return engine;
    }

    public static void loadScript(String scriptPath, Object model) {
        loadScriptFromPath(scriptPath, model, null);
    }

    public static void loadScriptFromPath(String scriptPath, Object model, String modelName) {
        RealTrainModUnofficial.LOGGER.info("legacy script load requested: {} for model {}", scriptPath, model == null ? "null" : model.getClass().getSimpleName());
        try {
            ScriptEngine scriptEngine = createScriptEngine();
            if (scriptEngine == null) {
                RealTrainModUnofficial.LOGGER.warn("JavaScript engine not available for model script: {}", scriptPath);
                return;
            }

            Path path = Path.of(scriptPath);
            if (Files.exists(path)) {
                RealTrainModUnofficial.LOGGER.info("Loading script from filesystem path: {}", path);
                String script = PackTextDecoder.readText(path);
                loadScript(scriptPath, script, model, modelName, scriptEngine);
            } else {
                RealTrainModUnofficial.LOGGER.info("Script path not found on filesystem, skipping direct load: {}", scriptPath);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.error("Failed to load script for model: {}", scriptPath, e);
        }
    }

    public static void loadScript(String scriptPath, String script, Object model) {
        loadScript(scriptPath, script, model, null);
    }

    public static void loadScript(String scriptPath, String script, Object model, String modelName) {
        RealTrainModUnofficial.LOGGER.info("legacy script load requested from content: {} for model {}", scriptPath, model == null ? "null" : model.getClass().getSimpleName());
        try {
            ScriptEngine scriptEngine = createScriptEngine();
            if (scriptEngine == null) {
                RealTrainModUnofficial.LOGGER.warn("JavaScript engine not available for model script: {}", scriptPath);
                return;
            }
            loadScript(scriptPath, script, model, modelName, scriptEngine);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.error("Failed to load script for model: {}", scriptPath, e);
        }
    }

    private static ScriptEngine getAvailableScriptEngine(ScriptEngineManager manager) {
        if (manager == null) {
            return null;
        }

        if (!graalPolyglotUnavailable) {
            for (String ecmaVersion : PREFERRED_ECMA_VERSIONS) {
                try {
                    org.graalvm.polyglot.Engine polyglotEngine = org.graalvm.polyglot.Engine.newBuilder()
                        .allowExperimentalOptions(true)
                        .build();
                    org.graalvm.polyglot.Context.Builder contextBuilder = org.graalvm.polyglot.Context.newBuilder("js")
                        .allowAllAccess(true)
                        .allowExperimentalOptions(true)
                        .option("js.nashorn-compat", "true")
                        .option("js.syntax-extensions", "true")
                        .option("js.ecmascript-version", ecmaVersion);
                    ScriptEngine scriptEngine = com.oracle.truffle.js.scriptengine.GraalJSScriptEngine.create(polyglotEngine, contextBuilder);
                    if (scriptEngine != null) {
                        RealTrainModUnofficial.LOGGER.info("Using Graal.js with RTM compatibility on ECMAScript {}.", ecmaVersion);
                        return scriptEngine;
                    }
                } catch (Throwable e) {
                    RealTrainModUnofficial.LOGGER.debug("Graal.js polyglot unavailable (ECMAScript {}): {}", ecmaVersion, e.getMessage());
                }
            }
            graalPolyglotUnavailable = true;
            RealTrainModUnofficial.LOGGER.info("Graal.js polyglot API not available on module-path; using ScriptEngineManager fallback.");
        }

        String[] engineNames = {"javascript", "js", "Graal.js", "graal.js", "nashorn"};
        for (String name : engineNames) {
            ScriptEngine scriptEngine = manager.getEngineByName(name);
            if (scriptEngine != null) {
                RealTrainModUnofficial.LOGGER.info("Using JavaScript engine '{}'.", name);
                return scriptEngine;
            }
        }

        if (!manager.getEngineFactories().isEmpty()) {
            RealTrainModUnofficial.LOGGER.warn(
                "Available script engines: {}",
                manager.getEngineFactories().stream()
                    .map(ScriptEngineFactory::getEngineName)
                    .collect(Collectors.joining(", "))
            );
        } else {
            RealTrainModUnofficial.LOGGER.warn("No script engine providers found on the classpath.");
        }

        try {
            Class<?> factoryClass = Class.forName("org.graalvm.polyglot.js.jsr223.GraalJSScriptEngineFactory");
            ScriptEngineFactory factory = (ScriptEngineFactory) factoryClass.getDeclaredConstructor().newInstance();
            ScriptEngine scriptEngine = factory.getScriptEngine();
            if (scriptEngine != null) {
                RealTrainModUnofficial.LOGGER.info("Using Graal.js ScriptEngineFactory directly.");
                return scriptEngine;
            }
        } catch (ClassNotFoundException ignored) {
            // Graal.js is not available on the classpath.
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.debug("Failed to instantiate Graal.js ScriptEngineFactory, falling back to other engines: {}", e.getMessage());
        }

        return null;
    }

    private static void loadScript(String scriptPath, String script, Object model, String modelName, ScriptEngine scriptEngine) {
        RealTrainModUnofficial.LOGGER.info("Executing model script: {} (model={})", scriptPath, model == null ? "null" : model.getClass().getSimpleName());
        try {
            ScriptModelRenderer renderer = new ScriptModelRenderer(model, modelName);
            injectScriptCompatibility(scriptEngine, renderer);
            scriptEngine.put(SCRIPT_PATH_KEY, scriptPath == null ? "" : scriptPath);
            scriptEngine.put(SCRIPT_MODEL_KEY, modelName == null ? "" : modelName);
            script = normalizeLegacyScriptReferences(script);
            script = LEGACY_API_PREPEND + (script == null ? "" : script);
            scriptEngine.eval(script);
            prepareScriptRuntimeBeforeInit(scriptEngine);

            if (model instanceof com.portofino.realtrainmodunofficial.model.MQOModel oldModel) {
                oldModel.setScriptEngine(scriptEngine);
            } else if (model instanceof com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.MqoModel newModel) {
                newModel.setScriptEngine(scriptEngine, renderer);
            } else {
                RealTrainModUnofficial.LOGGER.warn("legacy script model is not recognized type: {}", model == null ? "null" : model.getClass().getName());
            }
            invokeScriptInit(scriptEngine, renderer);
            prepareScriptRuntimeAfterInit(scriptEngine);
            RealTrainModUnofficial.LOGGER.info("Script loaded for model: {}", scriptPath);
        } catch (ScriptException e) {
            reportScriptError(scriptEngine, "load(model)", e);
            RealTrainModUnofficial.LOGGER.error("Failed to execute script for model: {}, continuing without script", scriptPath, e);
        } catch (Exception e) {
            reportScriptError(scriptEngine, "load(model)", e);
            RealTrainModUnofficial.LOGGER.error("Unexpected error loading script for model: {}, continuing without script", scriptPath, e);
        }
    }

    public static ScriptEngine loadStandaloneScript(String scriptPath, String script, String modelName) {
        ScriptEngine scriptEngine = createScriptEngine();
        if (scriptEngine == null) {
            return null;
        }
        RealTrainModUnofficial.LOGGER.info("Executing standalone script: {}", scriptPath);
        try {
            ScriptModelRenderer renderer = new ScriptModelRenderer(null, modelName);
            injectScriptCompatibility(scriptEngine, renderer);
            scriptEngine.put(SCRIPT_PATH_KEY, scriptPath == null ? "" : scriptPath);
            scriptEngine.put(SCRIPT_MODEL_KEY, modelName == null ? "" : modelName);
            script = normalizeLegacyScriptReferences(script);
            // ユーザースクリプトの先頭に Java 注入オブジェクトのリバインドを prepend する。
            // injectScriptCompatibility 内の eval で var 宣言しても、別の eval をまたぐと
            // Nashorn でグローバルが期待通りに見えないケースがあるため、確実性を最大化。
            script = LEGACY_API_PREPEND + script;
            RealTrainModUnofficial.LOGGER.info("[PREPEND-CHECK] {} prependLen={} scriptLen={} firstChars={}",
                scriptPath, LEGACY_API_PREPEND.length(), script.length(),
                script.substring(0, Math.min(80, script.length())));
            scriptEngine.eval(script);
            prepareScriptRuntimeBeforeInit(scriptEngine);
            invokeScriptInit(scriptEngine, renderer);
            prepareScriptRuntimeAfterInit(scriptEngine);
            RealTrainModUnofficial.LOGGER.info("Standalone script loaded: {}", scriptPath);
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

    private static String normalizeLegacyScriptReferences(String script) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        String oldRoot = "n" + "gt";
        String oldLibRoot = oldRoot + "lib";
        String oldVehicleRoot = "r" + "tm";
        String oldCoreName = "R" + "T" + "MCore";
        String oldClientUtilName = "N" + "G" + "TUtilClient";
        String oldUtilName = "N" + "G" + "TUtil";
        String oldLogName = "N" + "G" + "TLog";
        String oldFileLoaderName = "N" + "G" + "TFileLoader";
        String oldTessellatorName = "N" + "G" + "TTessellator";
        String packages = "Packages.jp." + oldRoot + ".";
        String result = script;
        result = result.replace("var GLHelper = " + packages + oldLibRoot + ".renderer.GLHelper;", "");
        result = result.replace("var " + oldClientUtilName + " = " + packages + oldLibRoot + ".util." + oldClientUtilName + ";", "");
        result = result.replace("var " + oldUtilName + " = " + packages + oldLibRoot + ".util." + oldUtilName + ";", "");
        result = result.replace(packages + oldVehicleRoot + "." + oldCoreName, oldCoreName);
        result = result.replace(packages + oldVehicleRoot + ".modelpack.ModelPackManager", "ModelPackManager");
        result = result.replace(packages + oldLibRoot + ".util." + oldClientUtilName, oldClientUtilName);
        result = result.replace(packages + oldLibRoot + ".util." + oldUtilName, oldUtilName);
        result = result.replace(packages + oldLibRoot + ".io." + oldLogName, oldLogName);
        result = result.replace(packages + oldLibRoot + ".io." + oldFileLoaderName + ".getInputStream", oldFileLoaderName + "_getInputStream");
        result = result.replace(packages + oldLibRoot + ".renderer.GLHelper", "GLHelper");
        // Packages.jp.ngt.ngtlib.math.Vec3 → グローバル Vec3 (LEGACY_API_PREPEND で JS 実装)
        // Nashorn の Packages は JavaPackage で、JS から property を上書きできないため、
        // ソース側で直接置換するのが確実。
        result = result.replace(packages + oldLibRoot + ".math.Vec3", "Vec3");
        result = result.replace(packages + oldLibRoot + ".math.NGTMath", "NGTMath");
        result = result.replace(packages + oldLibRoot + ".io.ScriptUtil", "ScriptUtil");
        result = result.replace(packages + oldLibRoot + ".renderer." + oldTessellatorName, "TessellatorCompat");
        result = result.replace(packages + oldLibRoot + ".renderer.model.ModelLoader", "ModelLoader");
        result = result.replace(packages + oldLibRoot + ".renderer.model.VecAccuracy", "VecAccuracy");
        result = result.replace(packages + oldLibRoot + ".math.Vec3", "Vec3");
        result = result.replace("Packages.net.minecraft.util.ResourceLocation", "ResourceLocationCompat");
        // 1.12.2 Forge の Loader (mod 存在チェック) は 1.21.1(NeoForge)に無いのでスタブへ。
        result = result.replace("Packages.net.minecraftforge.fml.common.Loader", "LoaderCompat");
        result = result.replace("if (!stream) return null;", "if (!stream) return __ptDummyTextureData();");
        result = result.replace("Java.from(", "__ptJavaFrom(");
        result = appendSuperRailBuilderOverrides(result);
        return result;
    }

    /**
     * SuperRailBuilder3 のサーバスクリプトを検出したら、レール生成/削除/レール所持判定の各関数を
     * RTMU ネイティブ敷設(__SRB__ ブリッジ)へ差し替える上書き定義を末尾に追加する。
     * GUI・制御フロー(onUpdate/dataMap)・render はそのまま活かし、低レベル RTM/MCP API の不一致を回避する。
     */
    private static String appendSuperRailBuilderOverrides(String script) {
        if (script == null || !script.contains("SuperRailBuilderVersion")) {
            return script;
        }
        boolean isServer = script.contains("function buildNormalRail");
        StringBuilder sb = new StringBuilder();
        sb.append("\n;(function(){ try {\n");
        // --- server/render 共通: プレイヤー/インベントリの MCP API を触る関数を差し替える ---
        // getPlayerRail は「手持ちレールのモデルID(真偽値兼用)」を __SRB__ 経由で返す。
        // player は server では rider ラッパー(__srbReal)、render では実 LocalPlayer。
        sb.append("  getSelectedSlotItem = function(player){ return null; };\n");
        sb.append("  hasPlayerMarker = function(player){ return false; };\n");
        sb.append("  getPlayerRail = function(player) { try { var p = (player && player.__srbReal)?player.__srbReal:player; var id = __SRB__.heldRailModelId(p); return (id && (''+id).length>0)?(''+id):null; } catch(e){ return null; } };\n");
        // doFollowing: ホストプレイヤーの上へ車体をテレポート(MCP field を避け getX/Y/Z を使う)。
        // doFollowing はサーバ側のみで車をプレイヤー上へ移動させ、クライアントはサーバ同期＋補間で
        // 滑らかに追従する。マーカーは MCWrapper.getPosX(=レンダー補間位置)基準で描くので一致して荒ぶらない。
        // クライアントで毎フレーム動かすと描画とズレるため、ここでは動かさない。
        sb.append("  doFollowing = function(entity, hostPlayer){ try{ if(!entity||!hostPlayer) return; var w=entity.field_70170_p; if(w && w.isClientSide && w.isClientSide()) return; var p=hostPlayer.__srbReal?hostPlayer.__srbReal:hostPlayer; if(!p||!p.getX) return; entity.func_70107_b(p.getX(), p.getY()+2, p.getZ()); try{entity.field_70159_w=0; entity.field_70181_x=0; entity.field_70179_y=0;}catch(e2){} }catch(e){} };\n");
        // getTileEntity: 1.12.2 の net.minecraft.util.math.BlockPos を new せず、座標直接版 func_175625_s を使う。
        // 当たり判定/道床ブロックはコアに解決して返す(__SRB__.railCoreAt)。レール沿いどこでも接続検出が効き、
        // 接続マーカーが接線ロックされる(本家挙動)。フォールバックで素の func_175625_s。
        sb.append("  getTileEntity = function(world, x, y, z){ try{ if(typeof __SRB__!=='undefined'&&__SRB__) return __SRB__.railCoreAt(world, Math.floor(x), Math.floor(y), Math.floor(z)); return world.func_175625_s(Math.floor(x), Math.floor(y), Math.floor(z)); }catch(e){ try{ return world.func_175625_s(Math.floor(x), Math.floor(y), Math.floor(z)); }catch(e2){ return null; } } };\n");
        // getTileEntityPos は func_174877_v(MCP)を使うので、座標は __SRB__.tilePos 経由で取る(レール接続検出用)。
        sb.append("  getTileEntityPos = function(tile){ try{ var p=__SRB__.tilePos(tile); return {x:p[0],y:p[1],z:p[2]}; }catch(e){ return {x:0,y:0,z:0}; } };\n");
        if (isServer) {
            // --- server 専用: rider ラッパー + 敷設ブリッジ ---
            sb.append("  var __srbWrap = function(p){ if(!p) return null; if(p.__srbReal) return p; return { __srbReal:p, func_145782_y:function(){return p.getId();}, func_184210_p:function(){try{p.stopRiding();}catch(e){}}, func_70078_a:function(t){} }; };\n");
            sb.append("  getRider = function(entity){ try{ var ps=entity.func_184188_bt(); var r=(ps&&ps.size()>0)?ps.get(0):null; return __srbWrap(r); }catch(e){ return null; } };\n");
            sb.append("  getRidingEntity = function(entity){ try{ return __srbWrap(entity.func_184187_bx()); }catch(e){ return null; } };\n");
            sb.append("  createRailPosition = function(data) { return __SRB__.createRailPosition(data.blockX|0, data.blockY|0, data.blockZ|0, data.markerDir|0, (data.switchType!=null?Number(data.switchType):0), (data.anchorLength!=null?Number(data.anchorLength):-1), (data.anchorPitch!=null?Number(data.anchorPitch):0), (data.anchorYaw!=null?Number(data.anchorYaw):0), (data.cantCenter!=null?Number(data.cantCenter):0), (data.cantEdge!=null?Number(data.cantEdge):0), (data.height!=null?Number(data.height):0)); };\n");
            sb.append("  buildNormalRail = function(world, startRP, endRP, railItem) { try { __SRB__.buildNormalRail(world, startRP, endRP, railItem); } catch(e){ try{NGTLog.error('SRB buildNormalRail err: '+e);}catch(e2){} } };\n");
            sb.append("  buildBranchRail = function(world, rps, railItem) { try { var l=new java.util.ArrayList(); for(var i=0;i<rps.length;i++) l.add(rps[i]); __SRB__.buildBranchRail(world, l, railItem); } catch(e){} };\n");
            sb.append("  deleteRail = function(world, x, y, z) { try { return __SRB__.deleteRail(world, x|0, y|0, z|0); } catch(e){ return false; } };\n");
            sb.append("  deleteRailRP = function(world, rp) { return deleteRail(world, rp.blockX, rp.blockY, rp.blockZ); };\n");
        }
        sb.append("} catch(e){} })();\n");
        return script + sb.toString();
    }

    private static void injectScriptCompatibility(ScriptEngine scriptEngine, ScriptModelRenderer renderer) {
        try {
            String oldRoot = "n" + "gt";
            String oldLibRoot = oldRoot + "lib";
            String oldVehicleRoot = "r" + "tm";
            String oldCoreName = "R" + "T" + "MCore";
            String oldClientUtilName = "N" + "G" + "TUtilClient";
            String oldUtilName = "N" + "G" + "TUtil";
            String oldLogName = "N" + "G" + "TLog";
            String oldFileLoaderName = "N" + "G" + "TFileLoader";
            String oldTessellatorName = "N" + "G" + "TTessellator";
            ScriptCoreCompat coreCompat = new ScriptCoreCompat();
            scriptEngine.put("renderer", renderer);
            scriptEngine.put("model", renderer.getModel());
            // GL11 シム
            scriptEngine.put("GL11", new GL11Compat(renderer));
            scriptEngine.put("Parts", PartsBuilder.class);
            // NGTText / NGTLog 等の頻出ユーティリティは Java オブジェクトで直接注入する。
            // ただし scriptEngine.put("NGTText", ...) だけだと、ユーザースクリプトの
            //   importPackage(Packages.jp.ngt.ngtlib.io)
            // で global の NGTText が JavaPackage に上書きされる事例がある。
            // 対策: 衝突しないアンダースコア名で put し、後段の eval ブロックで
            //   var NGTText = __RTMU_NGTText__;
            // を実行することで、ユーザースクリプトの importPackage より先に
            // global var として確立しておく (var 宣言は importPackage よりも優先)。
            NGTTextCompat ngtText = new NGTTextCompat();
            NGTLogCompat ngtLog = new NGTLogCompat();
            NGTUtilCompat ngtUtil = new NGTUtilCompat();
            NGTMathCompat ngtMath = new NGTMathCompat();
            scriptEngine.put("__RTMU_NGTText__", ngtText);
            scriptEngine.put("__RTMU_NGTLog__", ngtLog);
            scriptEngine.put("__RTMU_NGTUtil__", ngtUtil);
            scriptEngine.put("__RTMU_NGTMath__", ngtMath);
            // 直接名前でも put (importPackage を no-op 化済みなので衝突なし)
            scriptEngine.put("NGTText", ngtText);
            scriptEngine.put("NGTLog", ngtLog);
            scriptEngine.put("NGTUtil", ngtUtil);
            scriptEngine.put("NGTMath", ngtMath);
            // ScriptCore を互換オブジェクトとしてバインド
            scriptEngine.put("ScriptCoreJava", coreCompat);
            scriptEngine.put("ScriptUtilJava", new ScriptUtilCompat());
            // SRB3 等のキー駆動 GUI 用に LWJGL2 Keyboard.isKeyDown を実キー入力へ橋渡し(クライアントのみ)。
            // サーバ(クライアントクラス不在)では NoClassDefFoundError を握りつぶして false 動作にフォールバック。
            try {
                scriptEngine.put("__RTMU_KEY__", new com.portofino.realtrainmodunofficial.client.ScriptKeyboardCompat());
                scriptEngine.put("__RTMU_MC__", new com.portofino.realtrainmodunofficial.client.ScriptClientCompat());
            } catch (Throwable ignored) {
                // bind されなければ Keyboard/MC 系は null/false を返す(従来動作)。
            }
            // SuperRailBuilder3 のレール敷設/削除を RTMU ネイティブへ橋渡しするブリッジ。
            scriptEngine.put("__SRB__", new SrbRailBridge());
            try {
                scriptEngine.eval("load('nashorn:mozilla_compat.js');");
            } catch (Exception ignored) {
                RealTrainModUnofficial.LOGGER.debug("mozilla_compat.js not available for current JS engine.");
            }
            scriptEngine.eval(
                "var __trainCoreCompat = { VERSION: " + quoteJs(SCRIPT_CORE_VERSION) + ", getVERSION: function() { return this.VERSION; }, getVersion: function() { return this.VERSION; } };\n" +
                "ScriptCore = __trainCoreCompat;\n" +
                oldCoreName + " = __trainCoreCompat;\n" +
                // RTMU 互換: Java 側で put した __RTMU_xxx__ を、グローバル var として NGTText/NGTLog/NGTUtil/NGTMath に束ねる。
                // var 宣言なので、後段の typeof チェック (undefined のとき JS スタブで上書き) より前に存在し、
                // かつ importPackage が no-op 化された後でも安定して残る。
                "if (typeof __RTMU_NGTText__ !== 'undefined') var NGTText = __RTMU_NGTText__;\n" +
                "if (typeof __RTMU_NGTLog__ !== 'undefined') var NGTLog = __RTMU_NGTLog__;\n" +
                "if (typeof __RTMU_NGTUtil__ !== 'undefined') var NGTUtil = __RTMU_NGTUtil__;\n" +
                "if (typeof __RTMU_NGTMath__ !== 'undefined') var NGTMath = __RTMU_NGTMath__;\n" +
                "importPackage = function(pkg) {};\n" +
                "importClass = function(pkg) {};\n" +
                "JavaImporter = function() { return {}; };\n" +
                "if (typeof java === 'undefined' && typeof Packages !== 'undefined') java = Packages.java;\n" +
                "if (typeof Packages === 'undefined' && typeof java !== 'undefined') Packages = java;\n" +
                "if (typeof Packages === 'undefined') Packages = {};\n" +
                "if (typeof Packages.org === 'undefined') Packages.org = {};\n" +
                "if (typeof Packages.org.lwjgl === 'undefined') Packages.org.lwjgl = {};\n" +
                "if (typeof Packages.org.lwjgl.opengl === 'undefined') Packages.org.lwjgl.opengl = {};\n" +
                "if (typeof Packages.jp === 'undefined') Packages.jp = {};\n" +
                "if (typeof Packages.jp.legacy === 'undefined') Packages.jp.legacy = {};\n" +
                "if (typeof Packages.jp.legacy.legacylib === 'undefined') Packages.jp.legacy.legacylib = {};\n" +
                "if (typeof Packages.jp.legacy.legacylib.math === 'undefined') Packages.jp.legacy.legacylib.math = {};\n" +
                "if (typeof Packages.jp.legacy.legacylib.renderer === 'undefined') Packages.jp.legacy.legacylib.renderer = {};\n" +
                "if (typeof Packages.jp.legacy.legacylib.renderer.GLHelper === 'undefined') Packages.jp.legacy.legacylib.renderer.GLHelper = { disableLighting: function() {}, enableLighting: function() {}, setBrightness: function(v) {}, setLightmapMaxBrightness: function() {} };\n" +
                "if (typeof Packages.jp.legacy.legacylib.renderer.model === 'undefined') Packages.jp.legacy.legacylib.renderer.model = {};\n" +
                "if (typeof Packages.jp.legacy.legacy === 'undefined') Packages.jp.legacy.legacy = {};\n" +
                "if (typeof Packages.jp.legacy.legacy.render === 'undefined') Packages.jp.legacy.legacy.render = {};\n" +
                "if (typeof Packages.jp.legacy.legacy.entity === 'undefined') Packages.jp.legacy.legacy.entity = {};\n" +
                "if (typeof Packages.jp.legacy.legacy.entity.train === 'undefined') Packages.jp.legacy.legacy.entity.train = {};\n" +
                "if (typeof Packages.jp.legacy.legacy.entity.train.util === 'undefined') Packages.jp.legacy.legacy.entity.train.util = {};\n" +
                "if (typeof Packages.jp.legacy.legacy.train === 'undefined') Packages.jp.legacy.legacy.train = {};\n" +
                "if (typeof Packages.jp['" + oldRoot + "'] === 'undefined') Packages.jp['" + oldRoot + "'] = {};\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'] === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'] = {};\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math = Packages.jp.legacy.legacylib.math;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer = Packages.jp.legacy.legacylib.renderer;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.GLHelper === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.GLHelper = Packages.jp.legacy.legacylib.renderer.GLHelper;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model = Packages.jp.legacy.legacylib.renderer.model;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'] === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'] = {};\n" +
                "Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'][" + quoteJs(oldCoreName) + "] = " + oldCoreName + ";\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack = {};\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack.ModelPackManager === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack.ModelPackManager = { INSTANCE: { getResource: function(domain, path) { return { domain: domain, path: path, func_110624_b: function() { return domain; }, func_110623_a: function() { return path; } }; } } };\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].render === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].render = Packages.jp.legacy.legacy.render;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].entity === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].entity = Packages.jp.legacy.legacy.entity;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].train === 'undefined') Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].train = Packages.jp.legacy.legacy.train;\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io = {};\n" +
                "if (typeof Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util === 'undefined') Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util = {};\n" +
                "var " + oldClientUtilName + " = Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util[" + quoteJs(oldClientUtilName) + "] = { bindTexture: function(texture) { renderer.bindTexture(texture); } };\n" +
                "var " + oldUtilName + " = Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].util[" + quoteJs(oldUtilName) + "] = { getUniqueId: function() { return new Date().getTime(); } };\n" +
                "var " + oldLogName + " = Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io[" + quoteJs(oldLogName) + "] = { debug: function(v) {}, info: function(v) {}, warn: function(v) {}, error: function(v) {} };\n" +
                "if (typeof load === 'undefined') load = function(path) {};\n" +
                "if (typeof Java === 'undefined') Java = {};\n" +
                "if (typeof global === 'undefined' && typeof globalThis !== 'undefined') global = globalThis;\n" +
                "if (typeof self === 'undefined' && typeof globalThis !== 'undefined') self = globalThis;\n" +
                "if (typeof console === 'undefined') console = { log: function() {}, info: function() {}, warn: function() {}, error: function() {}, debug: function() {} };\n" +
                "if (typeof print === 'undefined') print = function() {};\n" +
                "var __ptOriginalJavaFrom = (typeof Java.from === 'function') ? Java.from : null;\n" +
                "function __ptJavaFrom(value) { if (value == null) return []; if (Array.isArray && Array.isArray(value)) return Array.prototype.slice.call(value); if (Object.prototype.toString.call(value) === '[object Array]') return Array.prototype.slice.call(value); if (typeof value.length === 'number') { try { return Array.prototype.slice.call(value); } catch (e) {} } if (typeof value.toArray === 'function') return Array.prototype.slice.call(value.toArray()); if (__ptOriginalJavaFrom) { try { return __ptOriginalJavaFrom(value); } catch (e) {} } return [value]; }\n" +
                "var ScriptUtil = {\n" +
                "  doScript: function(script) { return ScriptUtilJava.doScript(String(script || '')); },\n" +
                "  doScriptFunction: function(se, func, args) { return ScriptUtilJava.doScriptFunction(se, String(func || ''), __ptJavaFrom(args)); },\n" +
                "  doScriptIgnoreError: function(se, func, args) { return ScriptUtilJava.doScriptIgnoreError(se, String(func || ''), __ptJavaFrom(args)); },\n" +
                "  getScriptField: function(se, field) { return ScriptUtilJava.getScriptField(se, String(field || '')); }\n" +
                "};\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io.ScriptUtil = ScriptUtil; } catch (e) {}\n" +
                // Vec3 は LEGACY_API_PREPEND の Vec3Impl で .sub/.add 等を含めて定義する。inject側では一切宣言しない。
                ""+
                "var VecAccuracy = { LOW: 0, MEDIUM: 1, HIGH: 2 };\n" +
                "var ModelLoader = { loadModel: function(resource, accuracy, options) {\n" +
                "  var __path = null;\n" +
                "  try { __path = (resource && typeof resource.func_110623_a === 'function') ? resource.func_110623_a() : (resource && resource.path ? resource.path : ('' + resource)); } catch (e) { __path = null; }\n" +
                "  var __h = null;\n" +
                "  try { if (typeof renderer !== 'undefined' && renderer && typeof renderer.loadAuxModel === 'function' && __path) __h = renderer.loadAuxModel(__path); } catch (e) { __h = null; }\n" +
                "  var __objs = [];\n" +
                "  try { if (__h != null && renderer && typeof renderer.auxObjectNames === 'function') __objs = renderer.auxObjectNames(__h); } catch (e) { __objs = []; }\n" +
                "  return {\n" +
                "    __handle: __h,\n" +
                "    renderAll: function() { try { if (__h != null && renderer && renderer.renderAuxAll) renderer.renderAuxAll(__h); } catch (e) {} },\n" +
                "    renderOnly: function() {},\n" +
                "    renderPart: function(cull, name) { try { if (__h != null && renderer && renderer.renderAuxPart) renderer.renderAuxPart(__h, name); } catch (e) {} },\n" +
                "    objects: __objs\n" +
                "  };\n" +
                "} };\n" +
                "var ModelPackManager = { INSTANCE: { getResource: function(domain, path) { return { domain: domain, path: path, func_110624_b: function() { return domain; }, func_110623_a: function() { return path; } }; } } };\n" +
                "var TrainState = { getStateType: function(value) { return value; }, suggestState: function(value, fallback) { return value == null ? fallback : value; } };\n" +
                "TrainState.TrainStateType = { Reverser: 0, Notch: 1, Rail: 2, Door: 4, Light: 5, Pantograph: 6, Speed: 7, Destination: 8, Sound: 9, Interior: 11 };\n" +
                "var RenderPass = {\n" +
                "  NORMAL: { id: 0 },\n" +
                "  TRANSPARENT: { id: 1 },\n" +
                "  LIGHT: { id: 2 },\n" +
                "  LIGHT_FRONT: { id: 2 },\n" +
                "  LIGHT_BACK: { id: 2 },\n" +
                "  OUTLINE: { id: 3 },\n" +
                "  PICK: { id: 4 }\n" +
                "};\n" +
                "var TessellatorCompat = { instance: { startDrawingQuads: function() { renderer.tessellatorStart(); }, addVertex: function(x, y, z) { renderer.tessellatorAddVertex(x, y, z); }, addVertexWithUV: function(x, y, z, u, v) { renderer.tessellatorAddVertexWithUV(x, y, z, u, v); }, setColorRGBA_F: function(r, g, b, a) { renderer.tessellatorSetColor(r, g, b, a); }, setColorRGBA: function(r, g, b, a) { renderer.tessellatorSetColor((r || 0) / 255.0, (g || 0) / 255.0, (b || 0) / 255.0, (a || 0) / 255.0); }, setNormal: function(x, y, z) { renderer.tessellatorSetNormal(x, y, z); }, draw: function() { renderer.tessellatorDraw(); } } };\n" +
                "var GLHelper = { disableLighting: function() { renderer.disableLighting(); }, enableLighting: function() { renderer.enableLighting(); }, setBrightness: function(v) { renderer.setBrightness(v); }, setLightmapMaxBrightness: function() { renderer.setLightmapMaxBrightness(); }, preMoveTexUV: function(u, v) { renderer.setUvOffset(u, v); }, postMoveTexUV: function() { renderer.clearUvOffset(); } };\n" +
                "var NGTMath = {\n" +
                "  toRadians: function(deg) { return deg * Math.PI / 180; },\n" +
                "  toDegrees: function(rad) { return rad * 180 / Math.PI; },\n" +
                "  getSin: function(rad) { return Math.sin(rad); },\n" +
                "  getCos: function(rad) { return Math.cos(rad); },\n" +
                "  getAtan2: function(y, x) { return Math.atan2(y, x); },\n" +
                "  atan2: function(y, x) { return Math.atan2(y, x); },\n" +
                "  normalizeAngle: function(deg) { return ((deg % 360) + 360) % 360; },\n" +
                "  clampAngle: function(deg, min, max) { return Math.max(min, Math.min(max, deg)); },\n" +
                "  lerp: function(a, b, t) { return a + (b - a) * t; }\n" +
                "};\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math.NGTMath = NGTMath; } catch (e) {}\n" +
                "var ResourceLocationCompat = function(domain, path) { if (path === undefined) { var s = String(domain || ''); var i = s.indexOf(':'); this.domain = i >= 0 ? s.substring(0, i) : 'minecraft'; this.path = i >= 0 ? s.substring(i + 1) : s; } else { this.domain = domain || 'minecraft'; this.path = path || ''; } this.namespace = this.domain; this.resourcePath = this.path; this.func_110624_b = function() { return this.domain; }; this.func_110623_a = function() { return this.path; }; this.getNamespace = function() { return this.domain; }; this.getPath = function() { return this.path; }; this.toString = function() { return this.domain + ':' + this.path; }; };\n" +
                "var ResourceLocation = ResourceLocationCompat;\n" +
                // LWJGL2 Keyboard stub (imported by some RTM packs via importPackage(Packages.org.lwjgl.input))
                "if (typeof Keyboard === 'undefined') Keyboard = { KEY_ESCAPE: 1, KEY_O: 24, KEY_L: 38, KEY_Q: 16, KEY_I: 23, KEY_P: 25, KEY_U: 22, KEY_J: 36, KEY_K: 37, KEY_F: 33, KEY_G: 34, KEY_H: 35, KEY_C: 46, KEY_V: 47, KEY_B: 48, KEY_N: 49, KEY_M: 50, KEY_RIGHT: 205, KEY_LEFT: 203, KEY_UP: 200, KEY_DOWN: 208, KEY_HOME: 199, KEY_END: 207, KEY_INSERT: 210, KEY_DELETE: 211, KEY_LBRACKET: 26, KEY_RBRACKET: 27, KEY_RETURN: 28, KEY_SPACE: 57, KEY_LSHIFT: 42, KEY_LCONTROL: 29, KEY_RCONTROL: 157, isKeyDown: function(key) { try { return (typeof __RTMU_KEY__ !== 'undefined' && __RTMU_KEY__) ? __RTMU_KEY__.isKeyDown(key) : false; } catch (e) { return false; } } };\n" +
                "function __rtmuMcShim() { return { field_71462_r: ((typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getCurrentScreen() : null), func_135016_M: function() { return { func_135041_c: function() { return { func_135034_a: function() { return ((typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getLanguageCode() : 'en_us'); } }; } }; } }; }\n" +
                "if (typeof MCWrapperClient === 'undefined') MCWrapperClient = { getPlayer: function() { try { return (typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getPlayer() : null; } catch (e) { return null; } }, getMinecraft: function() { return __rtmuMcShim(); }, bindTexture: function(texture) { if (typeof renderer !== 'undefined' && renderer) renderer.bindTexture(texture); } };\n" +
                "if (typeof NGTUtilClient === 'undefined') NGTUtilClient = { getMinecraft: function() { return __rtmuMcShim(); }, getPlayer: function() { try { return (typeof __RTMU_MC__ !== 'undefined' && __RTMU_MC__) ? __RTMU_MC__.getPlayer() : null; } catch (e) { return null; } }, bindTexture: function(texture) { if (typeof renderer !== 'undefined' && renderer) renderer.bindTexture(texture); } };\n" +
                "if (typeof NGTLog === 'undefined') NGTLog = { debug: function() {}, info: function() {}, warn: function() {}, error: function() {} };\n" +
                "if (typeof GuiChat === 'undefined') GuiChat = function() {};\n" +
                "if (typeof Minecraft === 'undefined') Minecraft = { func_71410_x: function() { return null; }, getMinecraft: function() { return null; } };\n" +
                // --- SuperRailBuilder3 等の 1.12.2 RTM スクリプト互換グローバル ---
                // 本家 RTM の Java クラスは 1.21.1 に存在しないため、スクリプトが eval 時に
                // 参照するトップレベルのグローバルを最小限スタブする。
                "if (typeof RTMCore === 'undefined') RTMCore = { VERSION: 'RTMU-1.21.1', MODID: 'rtm' };\n" +
                "if (typeof Blocks === 'undefined') Blocks = {};\n" +
                "if (Blocks.field_150325_L === undefined) Blocks.field_150325_L = { __rtmuBlock: 'minecraft:white_wool' };\n" +
                "if (typeof LoaderCompat === 'undefined') LoaderCompat = { isModLoaded: function(n) { return false; } };\n" +
                "if (typeof BlockUtil === 'undefined') BlockUtil = { setBlock: function() {} };\n" +
                "function __ptDummyTextureData() { return { images: [{}], size: 1, rate: 1, width: 1, height: 1 }; }\n" +
                "function " + oldFileLoaderName + "_getInputStream(resource) { return null; }\n" +
                "var " + oldFileLoaderName + " = { getInputStream: " + oldFileLoaderName + "_getInputStream };\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].io[" + quoteJs(oldFileLoaderName) + "] = " + oldFileLoaderName + "; } catch (e) {}\n" +
                "if (typeof frontSideTrainList === 'undefined') frontSideTrainList = [];\n" +
                "if (typeof rearSideTrainList === 'undefined') rearSideTrainList = [];\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].math.Vec3 = Vec3; } catch (e) {}\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model.VecAccuracy = VecAccuracy; } catch (e) {}\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.model.ModelLoader = ModelLoader; } catch (e) {}\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer[" + quoteJs(oldTessellatorName) + "] = TessellatorCompat; } catch (e) {}\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldVehicleRoot + "'].modelpack.ModelPackManager = ModelPackManager; } catch (e) {}\n" +
                "try { Packages.jp['" + oldRoot + "']['" + oldLibRoot + "'].renderer.GLHelper = GLHelper; } catch (e) {}\n" +
                "try { renderer.renderer = renderer; } catch (e) {}\n" +
                "var GL11 = {\n" +
                "  glPushMatrix: function() { renderer.pushMatrix(); },\n" +
                "  glPopMatrix: function() { renderer.popMatrix(); },\n" +
                "  glTranslatef: function(x, y, z) { renderer.translate(x, y, z); },\n" +
                "  glRotatef: function(angle, x, y, z) { renderer.rotate(angle, x, y, z); },\n" +
                "  glScalef: function(x, y, z) { renderer.scale(x, y, z); },\n" +
                "  glColor4f: function(r, g, b, a) { renderer.setColor(r, g, b, a); },\n" +
                "  glColor3f: function(r, g, b) { renderer.setColor(r, g, b, 1.0); },\n" +
                "  glEnable: function(cap) {},\n" +
                "  glDisable: function(cap) {},\n" +
                "  glBlendFunc: function(src, dst) {},\n" +
                "  glAlphaFunc: function(func, ref) {},\n" +
                "  glDepthMask: function(flag) {}\n" +
                "};\n" +
                "function __joinGroups(g) { return (g && typeof g.join === 'function') ? g.join(',') : g; }\n" +
                // groupsStr is a pre-serialised comma-joined string stored on the object so that
                // Java registerParts() can read the group list via getMember("groupsStr") even when
                // the GraalJS JSR-223 object is opaque to normal Java reflection.
                "function Parts() {\n" +
                "  this.groups = Array.prototype.slice.call(arguments);\n" +
                "  this.groupsStr = __joinGroups(this.groups);\n" +
                // groupsStr を毎フレーム再計算せず、初回構築時の固定文字列をそのまま渡す。
                // Java 側は同じ文字列インスタンスで来ればキャッシュ済みの解析結果を返せる。
                "  this.render = function(renderer) { if (renderer) { try { renderer.renderParts(this.groupsStr); } catch(e) {} } };\n" +
                "  this.getObjects = function(model) { return renderer ? renderer.getScriptModelObjects(this.groupsStr) : []; };\n" +
                "  this.containsName = function(name) { return this.groups.indexOf(name) >= 0; };\n" +
                "}\n" +
                "function ModelParts() {\n" +
                "  this.groups = Array.prototype.slice.call(arguments);\n" +
                "  this.groupsStr = __joinGroups(this.groups);\n" +
                "  this.render = function(renderer) { if (renderer) { try { renderer.renderParts(__joinGroups(this.groups)); } catch(e) {} } };\n" +
                "  this.getObjects = function(model) { return renderer ? renderer.getScriptModelObjects(this.groupsStr) : []; };\n" +
                "  this.containsName = function(name) { return this.groups.indexOf(name) >= 0; };\n" +
                "}\n" +
                "function ActionParts(type) {\n" +
                "  this.groups = Array.prototype.slice.call(arguments, 1);\n" +
                "  this.groupsStr = __joinGroups(this.groups);\n" +
                "  this.render = function(renderer) { if (renderer) { try { renderer.renderParts(__joinGroups(this.groups)); } catch(e) {} } };\n" +
                "  this.getObjects = function(model) { return renderer ? renderer.getScriptModelObjects(this.groupsStr) : []; };\n" +
                "  this.containsName = function(name) { return this.groups.indexOf(name) >= 0; };\n" +
                "}\n" +
                "var PartsRenderer = renderer;\n" +
                "var ModelRenderer = renderer;\n" +
                "function __ptNoopPart() { return { render: function() {}, renderLight: function() {}, setOption: function() {}, addEntriesSet: function() {}, addMotionData: function() {}, addState: function() {}, getDoorState: function() { return false; }, getDoorPosZ: function() { return 0; }, getFlashState: function() { return false; } }; }\n" +
                "var ActionType = { DRAG_X: 0, DRAG_Y: 1, DRAG_Z: 2, ROTATE_X: 3, ROTATE_Y: 4, ROTATE_Z: 5, TOGGLE: 6 };\n" +
                "var Axis = { NONE: 0, POSITIVE_X: 1, NEGATIVE_X: 2, POSITIVE_Y: 3, NEGATIVE_Y: 4, POSITIVE_Z: 5, NEGATIVE_Z: 6 };\n" +
                "if (typeof NGTRenderer === 'undefined') NGTRenderer = { renderFire: function() {}, renderDesktop: function() {}, renderWebCamera: function() {}, renderTweet: function() {}, renderEBB: function() {}, renderPicture: function() {}, renderMap: function() {}, renderLightEffect: function() {}, renderFlame: function() {}, renderPortal: function() {} };\n" +
                "try { Packages.jp['" + "ngt" + "']['" + "ngtlib" + "'].renderer.NGTRenderer = NGTRenderer; } catch(e) {}\n" +
                "try { Packages.jp.legacy.legacylib.renderer.NGTRenderer = NGTRenderer; } catch(e) {}\n" +
                "if (typeof BlockScaffold === 'undefined') BlockScaffold = { getConnectionType: function() { return 0; } };\n" +
                "if (typeof BlockScaffoldStairs === 'undefined') BlockScaffoldStairs = { getConnectionType: function() { return 0; } };\n" +
                "if (typeof MCWrapperClient === 'undefined') MCWrapperClient = { playSound: function() {}, getPlayer: function() { return null; }, bindTexture: function() {} };\n" +
                // NGTText: Packages プロキシで undefined ではなくなる場合があるので、readText が
                // 関数として呼べるかも確認して、無理なら自前のスタブで上書きする。
                "if (typeof NGTText === 'undefined' || typeof NGTText.readText !== 'function') {\n" +
                "  NGTText = { createText: function() { return ''; }, getText: function() { return ''; }, getFormattedText: function() { return ''; }, getString: function() { return ''; }, appendSibling: function() {}, appendText: function() {}, applyTextStyles: function() {}, readText: function() { return ''; }, writeText: function() {}, loadText: function() { return ''; } };\n" +
                "}\n" +
                "if (typeof NGTSound === 'undefined') NGTSound = { playSound: function() {}, stopSound: function() {}, setSoundVolume: function() {}, setSoundPitch: function() {} };\n" +
                "if (typeof BlockHandler === 'undefined') BlockHandler = { getBlock: function() { return null; }, getTileEntity: function() { return null; } };\n" +
                // SoundState: ANSL系スクリプトで使われるサウンド状態クラス
                "if (typeof SoundState === 'undefined') SoundState = function(su, trackData) {\n" +
                "  this.su = su; this.trackData = trackData || {};\n" +
                "  this.isPlaying = false; this.volume = 0; this.pitch = 1;\n" +
                "  this.update = function() {}; this.play = function() {}; this.stop = function() {};\n" +
                "  this.setVolume = function(v) { this.volume = v; }; this.setPitch = function(p) { this.pitch = p; };\n" +
                "  this.getVolume = function() { return this.volume; }; this.getPitch = function() { return this.pitch; };\n" +
                "  this.isActive = function() { return this.isPlaying; };\n" +
                "};\n" +
                // 未知クラスアクセスを無音化するProxyラッパーを Packages の末端に適用
                "if (typeof Proxy !== 'undefined') {\n" +
                "  var __ptNoopCls = function() { return new Proxy(function(){}, { get: function(t,p) { if (p === 'prototype') return {}; if (typeof p === 'string') return function() { return null; }; return undefined; }, apply: function() { return null; }, construct: function() { return {}; } }); };\n" +
                "  var __ptPkgProxy = function(base) {\n" +
                "    return new Proxy(base || {}, {\n" +
                "      get: function(t, p) {\n" +
                "        if (p === 'then' || p === Symbol.toPrimitive) return undefined;\n" +
                "        var v = t[p];\n" +
                "        if (v !== undefined) return v;\n" +
                "        if (typeof p === 'string') {\n" +
                "          var first = p.charAt(0);\n" +
                "          if (first >= 'A' && first <= 'Z') return __ptNoopCls();\n" +
                "          return __ptPkgProxy({});\n" +
                "        }\n" +
                "        return undefined;\n" +
                "      }\n" +
                "    });\n" +
                "  };\n" +
                "  if (typeof Packages !== 'undefined') Packages = __ptPkgProxy(Packages);\n" +
                "}\n"
            );

            // model.body_f など任意グループ名アクセスをサポートするProxyラッパー
            // RTMスクリプトは model.body_f を直接アクセスするが、ScriptModelはJavaフィールドを持たないため
            // GraalJS Proxyでインターセプトし、グループ名文字列を持つアクセサオブジェクトを返す
            scriptEngine.eval(
                "if (typeof Proxy !== 'undefined' && typeof model !== 'undefined' && model !== null) {\n" +
                "  var __ptGA = function(n) {\n" +
                "    return new Proxy({ groupsStr: n }, {\n" +
                "      get: function(t, p) {\n" +
                "        if (p === 'groupsStr') return n;\n" +
                "        if (p === 'toString') return function() { return n; };\n" +
                "        if (p === 'valueOf') return function() { return n; };\n" +
                "        if (p === 'then') return undefined;\n" +
                "        if (p === 'render') return function(r) { if (r && typeof r.renderParts === 'function') { try { r.renderParts(n); } catch(e) {} } };\n" +
                "        if (typeof p === 'string' && !p.startsWith('__') && p !== 'constructor') return __ptGA(p);\n" +
                "        return t[p];\n" +
                "      },\n" +
                "      has: function(t, p) { return true; }\n" +
                "    });\n" +
                "  };\n" +
                "  var __ptOM = model;\n" +
                "  model = new Proxy({}, {\n" +
                "    get: function(t, n) {\n" +
                "      if (typeof n !== 'string') return undefined;\n" +
                "      if (n === 'then') return undefined;\n" +
                "      try { var v = __ptOM[n]; if (v !== null && v !== undefined) return v; } catch(e) {}\n" +
                "      return __ptGA(n);\n" +
                "    },\n" +
                "    has: function(t, n) { return true; }\n" +
                "  });\n" +
                "}\n"
            );

            // scripts that expect 1.12-style methods will call these against entity
            scriptEngine.eval(
                "if (typeof __legacy_compat_once === 'undefined') {\n" +
                "  __legacy_compat_once = true;\n" +
                "  function __safeCall(obj, fn, d) { try { return (obj && typeof obj[fn] === 'function') ? obj[fn]() : d; } catch (e) { return d; } }\n" +
                "  var ScriptCondition = {\n" +
                "    count: function(executer) { try { return executer && typeof executer.getCount === 'function' ? executer.getCount() : (executer && executer.count ? executer.count : 0); } catch (e) { return 0; } },\n" +
                "    once: function(executer) { try { return !!(executer && typeof executer.once === 'function' ? executer.once() : this.count(executer) <= 0); } catch (e) { return false; } },\n" +
                "    every: function(executer, interval) { interval = Math.max(1, interval | 0); try { return !!(executer && typeof executer.every === 'function' ? executer.every(interval) : (this.count(executer) % interval) === 0); } catch (e) { return false; } },\n" +
                "    between: function(executer, start, endExclusive) { try { return !!(executer && typeof executer.between === 'function' ? executer.between(start, endExclusive) : (this.count(executer) >= start && (endExclusive < 0 || this.count(executer) < endExclusive))); } catch (e) { return false; } },\n" +
                "    times: function(executer, maxCount) { try { return !!(executer && typeof executer.times === 'function' ? executer.times(maxCount) : (maxCount < 0 || this.count(executer) < maxCount)); } catch (e) { return false; } }\n" +
                "  };\n" +
                "}\n"
            );
        } catch (ScriptException e) {
            RealTrainModUnofficial.LOGGER.error("Failed to inject script compatibility helpers", e);
        }
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
                // CustomMonitor_* は //include で実装が定義される(展開済み)。その実装は NGTLib 直接GL等
                // 移植版に無い API を使い init/描画で落ちるため、ここで「定義済みなら互換スタブで上書き」する
                // (条件は !== undefined)。逆(=== undefined)にすると実装側が動いて _getPosList 等で例外→
                // init 失敗→運転台モニタどころかスクリプト全体が読み込めなくなる。
                // Stage 5: 実装(//include の素の CustomMonitor_LCD)を「試して、失敗したらスタブへフォールバック」。
                // Stage 2-3 で ModelLoader.renderPart / Parts.getObjects(getScriptModelObjects) を実装したので
                // 素の LCD 実装(数字/時計オーバーレイ)が動く可能性がある。動かなければ従来のスタブ(GIF表示)へ。
                // どちらでも例外で loading を壊さない設計。
                "if (typeof CustomMonitor_LCD !== 'undefined') {\n" +
                "  var __realLCD = CustomMonitor_LCD;\n" +
                "  var __wrapLCD = function(modelSet, modelObj, baseParts, texturePath) {\n" +
                "    this.baseParts = baseParts; this.texturePath = texturePath;\n" +
                "    try { this.gif = new CustomTexture(modelObj, texturePath); } catch (e) { this.gif = null; }\n" +
                // LCD も実装(路線図GIF+時刻+号車)を試す。getScriptTexture はフレーム別テクスチャを返すので
                // 路線図は綺麗に出るはず。状態隔離(行列復元+テクスチャクリア)込みなので漏れも防ぐ。
                // 失敗時は下の renderGifOnParts スタブ(路線図のみ)へフォールバック。
                "    this.__real = null;\n" +
                "    try { this.__real = new __realLCD(modelSet, modelObj, baseParts, texturePath); } catch (e) { try { if (typeof renderer !== 'undefined' && renderer && renderer.scriptDiag) renderer.scriptDiag('LCD real ctor failed: ' + e); } catch (x) {} this.__real = null; }\n" +
                "  };\n" +
                "  __wrapLCD.prototype = { constructor: __wrapLCD, render: function(renderer, entity, pass, partialTick) {\n" +
                "    if (this.__real) { var __md = renderer.getMatrixDepth ? renderer.getMatrixDepth() : -1; var __ok = true; try { this.__real.render(renderer, entity, pass, partialTick); } catch (e) { __ok = false; try { if (renderer && renderer.scriptDiag) renderer.scriptDiag('LCD real render failed: ' + e); } catch (x) {} this.__real = null; } try { if (__md >= 0 && renderer.restoreMatrixDepth) renderer.restoreMatrixDepth(__md); } catch (e) {} try { if (renderer.clearScriptTexture) renderer.clearScriptTexture(); if (renderer.clearUvWindow) renderer.clearUvWindow(); if (renderer.enableLighting) renderer.enableLighting(); } catch (e) {} if (__ok) return; }\n" +
                "    if (!entity || pass > 2 || !this.baseParts) return; var id = 0; try { id = Math.floor(entity.getTrainStateData(8)); } catch (e) {} if (typeof lcdDisplaySet !== 'undefined' && lcdDisplaySet[id]) { var set = lcdDisplaySet[id]; var tick = 0; try { tick = renderer.getTick(entity); } catch (e) {} id = set[Math.floor((tick % (set.length * 200)) / 200)] || set[0] || id; } else { try { var frames = renderer.getScriptTextureFrameCount('minecraft', this.texturePath); var tick2 = renderer.getTick(entity); if (frames > 0) id = Math.floor(tick2 / 2) % frames; } catch (e2) {} } try { if (typeof renderer.setLightmapMaxBrightness === 'function') renderer.setLightmapMaxBrightness(); if (renderer && typeof renderer.renderGifOnParts === 'function') renderer.renderGifOnParts(this.baseParts, 'minecraft', this.texturePath, id); else { this.gif.bindTexture(entity, id); this.baseParts.render(renderer); } } finally { if (typeof renderer.enableLighting === 'function') renderer.enableLighting(); renderer.clearUvWindow(); renderer.clearScriptTexture(); } }\n" +
                "  };\n" +
                "  for (var __k in __realLCD) { try { if (__realLCD.hasOwnProperty(__k)) __wrapLCD[__k] = __realLCD[__k]; } catch (e) {} }\n" +
                "  CustomMonitor_LCD = __wrapLCD;\n" +
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
                // Stage 5(JRE): メインモニタ(JRE_1)/TIMS(JRE_2) も「素の実装を試す→失敗時ベース部品描画へ
                // フォールバック」。メソッド(addEntrySet/setOption 等)は実インスタンスへ委譲し、静的 EntrySet は
                // 実クラスのものを引き継ぐ。実装が動けば運転台モニタの動的表示、ダメでも画面だけは出て loading は不変。
                "if (typeof CustomMonitor_JRE_1 !== 'undefined') {\n" +
                "  var __realJRE1 = CustomMonitor_JRE_1;\n" +
                "  var __wrapJRE1 = function(modelSet, modelObj, baseParts) { this.baseParts = baseParts; this.__real = null; try { this.__real = new __realJRE1(modelSet, modelObj, baseParts); } catch (e) { try { if (typeof renderer !== 'undefined' && renderer && renderer.scriptDiag) renderer.scriptDiag('JRE1 real ctor failed: ' + e); } catch (x) {} this.__real = null; } };\n" +
                "  __wrapJRE1.prototype = { constructor: __wrapJRE1,\n" +
                "    setOption: function(o, e2) { if (this.__real && this.__real.setOption) { try { this.__real.setOption(o, e2); } catch (e) {} } },\n" +
                "    render: function(renderer, entity, pass, partialTick) { if (this.__real && this.__real.render) { var __md = renderer.getMatrixDepth ? renderer.getMatrixDepth() : -1; var __ok = true; try { this.__real.render(renderer, entity, pass, partialTick); } catch (e) { __ok = false; try { if (renderer && renderer.scriptDiag) renderer.scriptDiag('JRE1 real render failed: ' + e); } catch (x) {} this.__real = null; } try { if (__md >= 0 && renderer.restoreMatrixDepth) renderer.restoreMatrixDepth(__md); } catch (e) {} try { if (renderer.clearScriptTexture) renderer.clearScriptTexture(); if (renderer.clearUvWindow) renderer.clearUvWindow(); if (renderer.enableLighting) renderer.enableLighting(); } catch (e) {} if (__ok) return; } try { if (pass <= 2 && this.baseParts && this.baseParts.render) this.baseParts.render(renderer); } catch (e) {} },\n" +
                "    getHashMap: function(e) { try { return (this.__real && this.__real.getHashMap) ? this.__real.getHashMap(e) : {}; } catch (x) { return {}; } },\n" +
                "    setHashMapData: function(e, k, v) { try { if (this.__real && this.__real.setHashMapData) this.__real.setHashMapData(e, k, v); } catch (x) {} },\n" +
                "    getHashMapData: function(e, k) { try { return (this.__real && this.__real.getHashMapData) ? this.__real.getHashMapData(e, k) : undefined; } catch (x) { return undefined; } } };\n" +
                "  for (var __k1 in __realJRE1) { try { if (__realJRE1.hasOwnProperty(__k1)) __wrapJRE1[__k1] = __realJRE1[__k1]; } catch (e) {} }\n" +
                "  CustomMonitor_JRE_1 = __wrapJRE1;\n" +
                "}\n" +
                "if (typeof CustomMonitor_JRE_2 !== 'undefined') {\n" +
                "  var __realJRE2 = CustomMonitor_JRE_2;\n" +
                "  var __wrapJRE2 = function(modelSet, modelObj, baseParts) { this.baseParts = baseParts; this.__real = null; try { this.__real = new __realJRE2(modelSet, modelObj, baseParts); } catch (e) { try { if (typeof renderer !== 'undefined' && renderer && renderer.scriptDiag) renderer.scriptDiag('JRE2 real ctor failed: ' + e); } catch (x) {} this.__real = null; } };\n" +
                "  __wrapJRE2.EntrySet = (__realJRE2 && __realJRE2.EntrySet) ? __realJRE2.EntrySet : {};\n" +
                "  ['tc','mc1','mc2','t','tsd','m1','m2','m3','m4','m5','m6','m7','m8'].forEach(function(k) { if (!__wrapJRE2.EntrySet[k]) __wrapJRE2.EntrySet[k] = { iconF: 'entry_' + k, iconB: 'entry_' + k }; });\n" +
                "  __wrapJRE2.prototype = { constructor: __wrapJRE2,\n" +
                "    addEntrySet: function(n, t, o) { if (this.__real && this.__real.addEntrySet) { try { this.__real.addEntrySet(n, t, o); } catch (e) {} } },\n" +
                "    addEntriesSet: function(l, t, o) { if (this.__real && this.__real.addEntriesSet) { try { this.__real.addEntriesSet(l, t, o); } catch (e) {} } },\n" +
                "    setOption: function(o, e2) { if (this.__real && this.__real.setOption) { try { this.__real.setOption(o, e2); } catch (e) {} } },\n" +
                "    render: function(renderer, entity, pass, partialTick) { if (this.__real && this.__real.render) { var __md = renderer.getMatrixDepth ? renderer.getMatrixDepth() : -1; var __ok = true; try { this.__real.render(renderer, entity, pass, partialTick); } catch (e) { __ok = false; try { if (renderer && renderer.scriptDiag) renderer.scriptDiag('JRE2(TIMS) real render failed: ' + e); } catch (x) {} this.__real = null; } try { if (__md >= 0 && renderer.restoreMatrixDepth) renderer.restoreMatrixDepth(__md); } catch (e) {} try { if (renderer.clearScriptTexture) renderer.clearScriptTexture(); if (renderer.clearUvWindow) renderer.clearUvWindow(); if (renderer.enableLighting) renderer.enableLighting(); } catch (e) {} if (__ok) return; } try { if (pass <= 2 && this.baseParts && this.baseParts.render) this.baseParts.render(renderer); } catch (e) {} },\n" +
                "    getHashMap: function(e) { try { return (this.__real && this.__real.getHashMap) ? this.__real.getHashMap(e) : {}; } catch (x) { return {}; } },\n" +
                "    setHashMapData: function(e, k, v) { try { if (this.__real && this.__real.setHashMapData) this.__real.setHashMapData(e, k, v); } catch (x) {} },\n" +
                "    getHashMapData: function(e, k) { try { return (this.__real && this.__real.getHashMapData) ? this.__real.getHashMapData(e, k) : undefined; } catch (x) { return undefined; } } };\n" +
                "  for (var __k2 in __realJRE2) { try { if (__realJRE2.hasOwnProperty(__k2) && __k2 !== 'EntrySet') __wrapJRE2[__k2] = __realJRE2[__k2]; } catch (e) {} }\n" +
                "  CustomMonitor_JRE_2 = __wrapJRE2;\n" +
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
        if (scriptEngine instanceof Invocable invocable) {
            try {
                invocable.invokeFunction("init", renderer, initModel);
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

    private static boolean isScriptDisabled(ScriptEngine scriptEngine) {
        return scriptEngine != null && DISABLED_SCRIPT_ENGINES.contains(System.identityHashCode(scriptEngine));
    }

    private static void disableBrokenScript(ScriptEngine scriptEngine, String phase, Throwable error) {
        if (scriptEngine == null) {
            return;
        }
        reportScriptError(scriptEngine, phase, error);
        if (DISABLED_SCRIPT_ENGINES.add(System.identityHashCode(scriptEngine))) {
            RealTrainModUnofficial.LOGGER.warn("Disabling legacy train script after {} failed", phase, error);
        }
    }

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

    /**
     * SRB3 等のサーバスクリプトを毎tick実行する用。
     * entity を `onUpdate(entity, scriptExecuter)` の形式で呼び出す。
     * scriptExecuter はスクリプト側で任意に使われる helper。現状は null を渡す。
     */
    public static void invokeServerScriptOnUpdate(ScriptEngine scriptEngine, Object entity) {
        if (scriptEngine == null || isScriptDisabled(scriptEngine)) return;
        try {
            scriptEngine.put("entity", entity);
            scriptEngine.put("executer", null);
            scriptEngine.put("executor", null);
        } catch (Throwable ignored) {
        }
        Invocable invocable = (Invocable) scriptEngine;
        try {
            invocable.invokeFunction("onUpdate", entity, null);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (ScriptException e) {
            disableBrokenScript(scriptEngine, "onUpdate(entity, executer) [server]", e);
            return;
        } catch (Throwable t) {
            disableBrokenScript(scriptEngine, "onUpdate(entity, executer) [server-runtime]", t);
            return;
        }
        try {
            invocable.invokeFunction("onUpdate", entity);
        } catch (NoSuchMethodException ignored) {
        } catch (ScriptException e) {
            disableBrokenScript(scriptEngine, "onUpdate(entity) [server]", e);
        } catch (Throwable t) {
            disableBrokenScript(scriptEngine, "onUpdate(entity) [server-runtime]", t);
        }
    }

    public static void invokeScriptTick(ScriptEngine scriptEngine, Object entity) {
        if (scriptEngine == null || isScriptDisabled(scriptEngine)) return;
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
                disableBrokenScript(scriptEngine, "tick(entity)", e);
                return;
            }
            try {
                invocable.invokeFunction("tick");
                return;
            } catch (NoSuchMethodException ignored) {
                // no zero-arg tick function
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "tick()", e);
                return;
            }
            if (compat != null) {
                try {
                    invocable.invokeFunction("onUpdate", compat);
                    return;
                } catch (NoSuchMethodException ignored) {
                    // no one-argument compat onUpdate
                } catch (ScriptException e) {
                    disableBrokenScript(scriptEngine, "onUpdate(compat)", e);
                    return;
                }
                try {
                    invocable.invokeFunction("onUpdate", entity, compat);
                    return;
                } catch (NoSuchMethodException ignored) {
                    // no two-argument onUpdate
                    } catch (ScriptException e) {
                        disableBrokenScript(scriptEngine, "onUpdate(entity, compat)", e);
                        return;
                    }
                }
            try {
                invocable.invokeFunction("onUpdate", entity);
                return;
            } catch (NoSuchMethodException ignored) {
                // no one-argument entity onUpdate
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "onUpdate(entity)", e);
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
            disableBrokenScript(scriptEngine, "tick/onUpdate fallback", e);
        }
    }

    public static void invokeScriptUpdate(ScriptEngine scriptEngine, Object entity, float partialTicks) {
        if (scriptEngine == null || isScriptDisabled(scriptEngine)) return;
        if (scriptEngine instanceof Invocable invocable) {
            try {
                invocable.invokeFunction("update", entity, partialTicks);
                return;
            } catch (NoSuchMethodException ignored) {
                // no update function
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "update(entity, partialTicks)", e);
                return;
            }
            try {
                invocable.invokeFunction("update", entity);
                return;
            } catch (NoSuchMethodException ignored) {
                // no entity-only update function
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "update(entity)", e);
                return;
            }
            try {
                invocable.invokeFunction("update", partialTicks);
                return;
            } catch (NoSuchMethodException ignored) {
                // no partialTick-only update function
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "update(partialTicks)", e);
                return;
            }
            try {
                invocable.invokeFunction("update");
                return;
            } catch (NoSuchMethodException ignored) {
                // no zero-arg update function
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "update()", e);
                return;
            }
        }
        try {
            scriptEngine.eval("if (typeof update === 'function') update();");
        } catch (ScriptException e) {
            disableBrokenScript(scriptEngine, "update() fallback", e);
        }
    }

    public static void invokeScriptRender(ScriptEngine scriptEngine, Object entity, float partialTicks) {
        if (scriptEngine == null || isScriptDisabled(scriptEngine)) return;
        int pass = 0;
        Object rendererObj = scriptEngine.get("renderer");
        if (rendererObj instanceof ScriptModelRenderer r) {
            pass = r.getCurrentPass();
        }
        if (scriptEngine instanceof Invocable invocable) {
            try {
                invocable.invokeFunction("render", entity, pass, partialTicks);
                return;
            } catch (NoSuchMethodException ignored) {
                // no 3-arg render function
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "render(entity, pass, partialTicks)", e);
                return;
            }
            try {
                invocable.invokeFunction("render", entity, partialTicks);
                return;
            } catch (NoSuchMethodException ignored) {
                // no 2-arg render function
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "render(entity, partialTicks)", e);
                return;
            }
            try {
                invocable.invokeFunction("render", entity);
                return;
            } catch (NoSuchMethodException ignored) {
                // no entity-only render function
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "render(entity)", e);
                return;
            }
            try {
                invocable.invokeFunction("render", partialTicks);
                return;
            } catch (NoSuchMethodException ignored) {
                // no partialTick-only render function
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "render(partialTicks)", e);
                return;
            }
            try {
                invocable.invokeFunction("render");
                return;
            } catch (NoSuchMethodException ignored) {
                // no zero-arg render function
            } catch (ScriptException e) {
                disableBrokenScript(scriptEngine, "render()", e);
                return;
            }
        }
        try {
            scriptEngine.eval("if (typeof render === 'function') render();");
        } catch (ScriptException e) {
            disableBrokenScript(scriptEngine, "render() fallback", e);
        }
    }

    public void executeTrainScript(TrainEntity train, String script) {
        if (engine == null || script == null || script.isEmpty()) {
            return;
        }

        EntityScriptContext context = getOrCreateContext(train);
        setupScriptContext(context, train, null);

        try {
            Bindings bindings = engine.createBindings();
            bindings.putAll(context.variables);
            engine.eval(script, bindings);
        } catch (ScriptException e) {
            RealTrainModUnofficial.LOGGER.error("Script execution error for vehicle '{}'", train.getVehicleId(), e);
        }
    }

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

    public void executeEventScript(Entity entity, String eventType, Object... parameters) {
        EntityScriptContext context = getOrCreateContext(entity);
        context.variables.put("eventType", eventType);
        context.variables.put("eventParams", parameters);

        // Event scripts would be loaded from model definition
        // For now, this is a placeholder for future implementation
    }

    private EntityScriptContext getOrCreateContext(Entity entity) {
        return entityContexts.computeIfAbsent(entity.getUUID(), k -> new EntityScriptContext());
    }

    private void setupScriptContext(EntityScriptContext context, TrainEntity train, Player player) {
        context.variables.put("currentTrain", train);
        context.variables.put("train", train);
        context.variables.put("player", player);
        context.variables.put("currentPlayer", player);
        context.variables.put("world", train.level());
        context.variables.put("level", train.level());
        context.variables.put("x", train.getX());
        context.variables.put("y", train.getY());
        context.variables.put("z", train.getZ());
        context.variables.put("yaw", train.getYRot());
        context.variables.put("pitch", train.getXRot());
        context.variables.put("trainDistance", train.getTrainDistance());
        context.variables.put("vehicleId", train.getVehicleId());
    }

    public void removeContext(Entity entity) {
        entityContexts.remove(entity.getUUID());
    }

    private static class EntityScriptContext {
        final Map<String, Object> variables = new HashMap<>();
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
        // RTM 1.7.10 obfuscated world field (entity.field_70170_p)。lib_FormationFix 等が
        // entity.field_70170_p.field_72995_K(world.isRemote) を参照するため公開する。
        public final com.portofino.realtrainmodunofficial.entity.TrainEntity.WorldCompat field_70170_p;

        public LegacyScriptExecutor(TrainEntity train) {
            this.train = train;
            this.field_70170_p = train != null ? train.field_70170_p
                : new com.portofino.realtrainmodunofficial.entity.TrainEntity.WorldCompat(null);
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
                    managerClass.getMethod("play", TrainEntity.class, String.class, String.class, float.class, float.class, boolean.class)
                        .invoke(null, train, namespace, soundName, volume, pitch, looping);
                } else {
                    managerClass.getMethod("stop", TrainEntity.class, String.class, String.class)
                        .invoke(null, train, namespace, soundName);
                }
            } catch (Exception e) {
                RealTrainModUnofficial.LOGGER.debug("Legacy sound bridge failed for {}:{}", namespace, soundName, e);
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

        // RTM EntityTrainBase#getSignal 互換。ATS表示スクリプト(2419等)が entity.getSignal() を
        // 呼ぶ。未実装だと render が例外→3回でスクリプト無効化→baked フォールバックで
        // 昼/夜ドアが二重に出る等の不具合になっていた。TrainEntity 側に委譲する(既定0=現示なし)。
        public int getSignal() {
            return train == null ? 0 : train.getSignal();
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
        // 旧 RTM Render スクリプトは entity.getRotation() で yaw を取得する。
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

        // ---- Resource state (RTM 1.12.2 entity.getResourceState().getDataMap()) ----

        public com.portofino.realtrainmodunofficial.entity.TrainEntity.ResourceStateCompat getResourceState() {
            return train == null ? null : train.getResourceState();
        }

        // ---- Formation / coupler (RTM 1.12.2 entity.getTrainDirection() etc.) ----

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

        // func_70089_S() = isEntityAlive (1.7.10/1.12.2)
        public boolean func_70089_S() {
            return train != null && !train.isRemoved();
        }

        // func_70027_ad() = isDead / isRemoved (1.7.10/1.12.2)
        public boolean func_70027_ad() {
            return train == null || train.isRemoved();
        }

        // func_70075_an() = isInWater (1.7.10)
        public boolean func_70075_an() {
            return train != null && train.isInWater();
        }

        // func_70093_af() = isSneaking (1.7.10/1.12.2)
        public boolean func_70093_af() {
            return false;
        }

        // func_70661_as() = isSprinting (1.7.10/1.12.2)
        public boolean func_70661_as() {
            return false;
        }

        // func_70617_f_() = isInWater (1.12.2 variant)
        public boolean func_70617_f_() {
            return func_70075_an();
        }

        // func_70086_ai() = isWet (1.7.10)
        public boolean func_70086_ai() {
            return train != null && train.isInWaterOrRain();
        }

        // func_70023_ah() = isBurning (1.7.10)
        public boolean func_70023_ah() {
            return train != null && train.isOnFire();
        }

        // func_70040_Z() = isSneaking variant (1.7.10)
        public boolean func_70040_Z() {
            return false;
        }

        // func_70003_b(int, String) = canUseCommand / hasPermissionLevel (1.12.2)
        public boolean func_70003_b(int level, String command) {
            return false;
        }

        // func_110140_aT() = getAttributeMap (1.12.2) — return stub
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

        // func_70030_z() = preparePlayerToSpawn / onUpdate stub (1.7.10)
        public void func_70030_z() {}

        // func_70021_al() = getPassengerList (1.7.10) — returns empty
        public Object[] func_70021_al() {
            return new Object[0];
        }

        // func_70678_g(float) = getEyeHeight variant (1.7.10)
        public float func_70678_g(float partialTick) {
            return 1.5F;
        }

        // func_70047_e() = getEyeHeight (1.7.10/1.12.2)
        public float func_70047_e() {
            return 1.5F;
        }

        // func_70057_ab() = getAir (1.7.10 variant)
        public int func_70057_ab() {
            return 300;
        }

        // func_70020_e() = setAir (1.7.10)
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

        // func_145782_y() is already implemented (getId / entityId)

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
        // 通常 translucent (AlphaBlend) を「最初に描画したパス」だけに限定するための記録。
        // -1 = まだ未描画。 これにより mat1 等の半透明面が複数 pass で重ね描きされて
        // z-fighting する (外装チラつき) のを防ぎつつ、 同一 pass 内の複数 renderParts
        // (椅子を複数脚など) は全て描画できる。
        private int firstTranslucentPass = -1;
        // Groups registered via registerParts() during init() — script "owns" these, baked render skips them
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

        // ==== 半透明遅延描画 (Deferred Translucent) ====
        // スクリプトは body_o(窓含む)→body_i(椅子) の順に「不透明→半透明」を即描画するため、
        // 窓(半透明)が椅子(不透明)より先に描かれ、窓越しに内装が見えない等の順序問題が出る。
        // deferTranslucent=true の間は半透明描画を即時せずキューに溜め、スクリプト全体(全 parts)が
        // 終わってから flushDeferredTranslucent() で一括描画する。これで「全不透明 → 全半透明」の
        // 正しい順序になり、窓越しに内装が見え、ライト等の半透明も最後に正しく重なる。
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

        // ==== Script Replay Cache (停車中など状態が変わらないフレームで JS engine を完全に bypass) ====
        // 1 度 render() を実行したときに renderer に対して行われた呼び出し列を記録し、
        // 同じ entity 状態のフレームでは記録された Op 列を直接再生する (JS engine 起動なし)。
        // GraalJS の関数実行 + JS↔Java 橋渡しコスト (1フレーム ~10-20ms) が消える。
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

        /**
         * pass + entity 状態を long に圧縮。同じ signature の連続フレームでは
         * 録画された Op 列を再生する。state には speed / door / light / notch
         * 等 「branch に影響しうる」値を含める。yaw 等の連続変化値は含めない。
         */
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

        // renderParts() の入力文字列 → 解析済み結果のキャッシュ。
        // SL の動軸スクリプトのように毎フレーム同じ groupsStr で renderParts を多数回
        // 呼び出すケースで、extractGroupNames/strip/filter/normalize/hasGroupNamed の
        // 重複処理を完全に省く。Parts.groupsStr は JS 側で 1 回しか生成されないため
        // 同一 String インスタンスのまま渡され、HashMap ヒット率はほぼ 100%。
        private final Map<String, ParsedGroupSet> renderPartsParseCache = new HashMap<>();

        // emissive pass で、lightMode ごとの presentGroupNames をキャッシュ。
        // pass 2 の renderParts ごとに 3 コレクション確保していたのを排除する。
        // キー: ParsedGroupSet インスタンスの identity × lightMode int → Set<String>
        private final java.util.IdentityHashMap<ParsedGroupSet, int[]> emissiveLightModeKeys = new java.util.IdentityHashMap<>();
        private final java.util.IdentityHashMap<ParsedGroupSet, Set<String>[]> emissivePresentCache = new java.util.IdentityHashMap<>();

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

        public Object getModel() {
            if (mqoModel != null) {
                return mqoModel.getScriptModel();
            }
            return model;
        }

        /**
         * Returns the model object expected by legacy render scripts.
         */
        public Object getModelObject() {
            return getModel();
        }

        /**
         * Returns the model object as a legacy model-set placeholder.
         */
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
            // 初回調査用。通常プレイでは script 初期化ログだけでもかなり多くなるため debug に留める。
            if (scriptRegisteredGroups.size() < 200) {
                RealTrainModUnofficial.LOGGER.debug(
                    "[registerParts] partsType={} extracted={} usable={} rejected={} totalRegistered={}",
                    parts == null ? "null" : parts.getClass().getSimpleName(),
                    names == null ? 0 : names.size(),
                    usable.size(), rejected, scriptRegisteredGroups.size());
            }
            // RTM 原作の Parts は .render(renderer) で対応グループを描画する。
            // ここで実用版 ScriptParts を返し、bogieF.render(renderer) 等が機能するようにする。
            return new ScriptParts(this, usable);
        }

        /** スクリプトが {@code bogieF.render(renderer)} を呼ぶと現在の poseStack で描画する。 */
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
                // pack 由来の擬似シャドウ (黒い平板を地面に貼るタイプ) は無効化する。
                // entity renderer の shadowRadius は 0 にしてあるが、こちらはモデル内の
                // group として描かれるためここでフィルタする。
                // shadow 完全一致のみ。 shadowXX など別の group まで巻き込まないようにする。
                if (x.equalsIgnoreCase("shadow")) continue;
                if (currentPass >= 2 && isLightOffGroup(x)) continue;
                if (shouldSuppressOerMseScriptHoodGroup(x)) continue;
                normalized.add(x);
            }
            // [診断] ヘッドライトグループが renderRegisteredGroups に到達したか(フィルタ前)。
            for (String dn : normalized) {
                if (dn.contains("light_f") || dn.contains("light_r")) {
                    String lk = "pre|" + dn + "|p" + currentPass;
                    if (lightRenderDiag.add(lk)) {
                        RealTrainModUnofficial.LOGGER.info(
                            "[LightRenderDiag] PRE group={} pass={} (entered renderRegisteredGroups)", dn, currentPass);
                    }
                }
            }
            if (normalized.isEmpty()) return;
            if (currentPass >= 2) {
                normalized = filterLegacyScriptEmissiveGroups(normalized);
                if (normalized.isEmpty()) return;
            }
            // [診断] 発光/角度フィルタ後にヘッドライトが残ったか。
            for (String dn : normalized) {
                if (dn.contains("light_f") || dn.contains("light_r")) {
                    String lk = "post|" + dn + "|p" + currentPass;
                    if (lightRenderDiag.add(lk)) {
                        RealTrainModUnofficial.LOGGER.info(
                            "[LightRenderDiag] POST group={} pass={} (survived filters -> will render)", dn, currentPass);
                    }
                }
            }
            // 角度バリアント (body-30 / body-90 / body-180 / bogie1-90 等) のフィルタ。
            // RTM の連結曲げ用に用意された「曲げ角ごとの代替メッシュ」で、本家は曲げ角に応じ
            // 1 つだけ描画する。移植版は同じ Parts に登録された全部を重ねて描くため、
            // 直線状態でも曲げボディが翼のように外へはみ出す (ポリゴン重なり)。
            // 0°(サフィックス無し)の本体が同じ描画呼び出しに含まれる時だけ曲げ変種を除外し、
            // 直線/単行の見た目を本家に合わせる。
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
        // これ未満 (1〜9) は車体のセクション分割 (D51 の body-1/2/3 等) とみなす。
        private static final int ANGLE_SUFFIX_THRESHOLD = 10;

        /**
         * 連結曲げ用の角度/ミラー変種を除外する。
         * RTM は同一 Parts に「素の名前(0°直線)」と曲げ角・ミラーの代替メッシュをまとめて登録し、
         * 曲げ角に応じ 1 つだけ描く (例: C57 body = "body","body(mx)","body-30",...,"body-180(mx)" /
         * bogie2 = "bogie2-60","bogie2-90" など素の名前が無いケースもある)。
         * 移植版には曲げ処理が無いため全部描くと翼状に重なる。
         *
         * ★重要: RTM の "(mx)" は「鏡像コピー」。直線(0°)の車体は body と body(mx) の
         *   左右 2 枚で 1 つになる。曲げ変種は body-30 / body-90(mx) のように**角度数字付き**だけ。
         *   → 角度数字付き(-NN, NN≥THRESHOLD)だけを「曲げ変種」として落とす。角度の無い (mx) は
         *     直線の鏡像半身なので残す (これを落とすと車体が半分になり断片化する)。
         *
         * 方針:
         *   - 非曲げ変種(素 / 鏡像 (mx) / セクション -1〜-9) は保持。
         *   - 曲げ変種(角度-数字≥THRESHOLD)は一切描かない (素の有無に関わらず)。
         * D51 の body-1/2/3 はセクション扱いで全保持。
         */
        private java.util.Set<String> filterAngleVariantGroups(java.util.Set<String> names) {
            // 曲げ変種(角度数字付き)は一切描かない。直線の単行列車に曲げメッシュは不要で、
            // 原点姿勢で描くと翼状/斜めに飛び出す (C57 の body-30..180, bogie2-60 等)。
            // 素 / 鏡像 (mx) / セクション(-1〜-9) のみ残す。素が無い部品 (例: 面を持たない
            // placeholder bogie2 しか 0° が無い後台車) は 0° では非表示 = RTM 互換。
            java.util.Set<String> out = new java.util.LinkedHashSet<>(names.size());
            for (String n : names) {
                if (!isBendVariant(n)) {
                    out.add(n);
                }
            }
            return out;
        }

        private static String stripMirrorSuffix(String n) {
            // RTM の鏡像/向き変種サフィックスは多様: (mx)/(my)/(mz) 単軸, (mxz)/(mxy)/(mxyz) 複数軸,
            // (r) 反転, さらにそれらが積み重なる場合もある(例 "body-35(mxz)", "body-100(r)")。
            // 以前は (mx) や単軸しか剥がせず、(mxz)/(r) 付きの曲げ変種が角度判定をすり抜けて
            // 原点姿勢で描画され、パーツの飛び散り/車番の重複を起こしていた。
            // 末尾の括弧が「鏡像/向きトークン(m,x,y,z,r のみ)」なら、すべて(連続も)剥がす。
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

        // ===== ModelLoader.loadModel 互換: 補助モデル(モニタ表示用 .mqoz 等)を同パックから読み描画する =====
        /** スクリプトの ModelLoader.loadModel(resource,...) 用。元パックから modelFile を読み、ハンドルを返す。 */
        public Object loadAuxModel(String modelFile) {
            if (mqoModel == null || modelFile == null || modelFile.isBlank()) return null;
            java.nio.file.Path pack = mqoModel.getSourcePackPath();
            if (pack == null) return null;
            try {
                return MqoModelLoader.loadAuxModel(pack, modelFile);
            } catch (Throwable t) {
                return null;
            }
        }

        /**
         * NGTLib PolygonModel#renderPart 互換。指定パーツを「生の頂点+生UV」で、外部バインド済みテクスチャ
         * (NGTUtilClient.bindTexture / gif.bindTexture で設定した boundTexture)を使って描画する。
         * renderNamedGroups の加工(forceCutout/UV window/opaque分割)を通さないので数字/路線図が garbling しない。
         */
        public void renderAuxPart(Object auxHandle, String partName) {
            if (!(auxHandle instanceof MqoModelLoader.MqoModel aux) || poseStack == null || buffer == null) return;
            String n = normalizeLegacyGroupName(partName);
            if (n.isEmpty()) return;
            java.util.Set<String> names = java.util.Set.of(n);
            // [診断] partName・boundTexture・実描画バッチ数を partName 単位で1回だけ出す。
            try {
                String dk = "auxpart|" + n;
                if (SCRIPT_DIAG_SEEN.add(dk)) {
                    int bc = aux.countScriptPartBatches(names);
                    RealTrainModUnofficial.LOGGER.info("[ScriptDiag] renderAuxPart part={} boundTex={} batches={}",
                        n, boundTexture, bc);
                }
            } catch (Throwable ignored) {}
            try {
                aux.renderScriptPartRaw(names, poseStack, buffer, packedLight, overlay, boundTexture);
            } catch (Throwable ignored) {
            }
        }

        /** 補助モデル全パーツを描画(model.renderAll 互換)。 */
        public void renderAuxAll(Object auxHandle) {
            if (!(auxHandle instanceof MqoModelLoader.MqoModel aux) || poseStack == null || buffer == null) return;
            try {
                MqoModelLoader.renderModelWithoutScript(aux, poseStack, buffer, packedLight, overlay, false, null, this);
                if (aux.hasTranslucentBatches()) {
                    MqoModelLoader.renderModelWithoutScript(aux, poseStack, buffer, packedLight, overlay, true, null, this);
                }
            } catch (Throwable ignored) {
            }
        }

        /** 補助モデルのグループ名一覧(model.objects 相当の簡易版)。 */
        public Object auxObjectNames(Object auxHandle) {
            if (auxHandle instanceof MqoModelLoader.MqoModel aux) {
                return aux.getAllNormalizedGroupNames().toArray(new String[0]);
            }
            return new String[0];
        }

        private static final java.util.Set<String> SCRIPT_DIAG_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();
        /** スクリプト側の try/catch から呼ぶ診断ログ。メッセージ単位で1回だけ出す(ノイズ防止)。 */
        public void scriptDiag(String msg) {
            if (msg == null) return;
            String key = msg.length() > 200 ? msg.substring(0, 200) : msg;
            if (SCRIPT_DIAG_SEEN.add(key)) {
                RealTrainModUnofficial.LOGGER.info("[ScriptDiag] {}", key);
            }
        }

        /** Parts.getObjects(model) 互換: 指定グループ群の GroupObject[](faces→vertices) を現モデルから返す。 */
        public Object[] getPartObjects(String groupsStr) {
            if (mqoModel == null || groupsStr == null || groupsStr.isBlank()) {
                return new Object[0];
            }
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            for (String raw : expandSerializedGroupNames(groupsStr)) {
                String n = normalizeLegacyGroupName(raw);
                if (!n.isEmpty()) names.add(n);
            }
            try {
                return mqoModel.getScriptGroupObjects(names);
            } catch (Throwable t) {
                return new Object[0];
            }
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
            // pass 2+ is the legacy emissive pass, but do not make the whole script pass
            // fullbright. Some train scripts (Spacia etc.) call render_parts() again in pass
            // 2, and a pass-wide fullbright makes exterior body meshes flash/glow in daylight.
            // Only explicit GLHelper.setLightmapMaxBrightness() or filtered emissive batches
            // should become fullbright.
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
                // fields and stops tick-driven displays like E259 rollsign/LCD.
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

        /**
         * UV ウィンドウまたは UV オフセットがアクティブかどうか。
         * GPU VBO キャッシュは静的 UV を前提とするため、これらが有効なフレームでは
         * CPU フォールバック経路を使う。
         */
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

        // Parts.getObjects(model) 用。指定グループの GroupObject[](faces→vertices)を現モデルから返す。
        // ★ Nashorn の Java.from は java.util.List を変換せず undefined を返す(配列専用)。本家 NGTLib も
        //   GroupObject[] を返すので、ここは必ず Java 配列(Object[])で返す(List だと _getPosList が落ちる)。
        public Object[] getScriptModelObjects(String groupsCsv) {
            if (mqoModel == null || groupsCsv == null || groupsCsv.isBlank()) {
                return new Object[0];
            }
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            for (String raw : expandSerializedGroupNames(groupsCsv)) {
                String n = normalizeLegacyGroupName(raw);
                if (!n.isEmpty()) names.add(n);
            }
            try {
                return mqoModel.getScriptGroupObjects(names);
            } catch (Throwable t) {
                return new Object[0];
            }
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
                    vc.addVertex(mat,
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

        /**
         * 指定グループ(LCD面)の各クワッド面の上に、gif/画像テクスチャをフルUV(0-1)で貼り付けて描画する。
         * E259 等の CustomMonitor_LCD は別オーバーレイモデルを NGTLib 直接GLで重ねるが、その API は
         * 1.21 に無いため、ここでは LCD 面の実頂点に直接 gif クワッドを描く(発光・面法線方向に微小オフセット)。
         * @param groupsCsv 対象グループ名(カンマ/スペース区切り, 例 "lcd1")
         */
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
                    vc.addVertex(mat, vx, vy, vz)
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

        /**
         * Disables lighting for old model scripts.
         */
        public void disableLighting() {
            // Modern rendering keeps lighting in the packed light value.
        }

        /**
         * Enables lighting for old model scripts.
         */
        public void enableLighting() {
            this.packedLight = basePackedLight;
            this.lightmapMaxForced = false;
            recordOp(OP_ENABLE_LIGHTING, 0, 0, 0, 0, 0, null, ' ');
        }

        /**
         * Applies a packed light value requested by old model scripts.
         */
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

        /**
         * Forces full brightness for emissive script parts.
         */
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
                            if (shouldSuppressOerMseScriptHoodGroup(n)) continue;
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
                            if (shouldSuppressOerMseScriptHoodGroup(n)) return false;
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
                // [診断] ヘッドライト(ex16_light_f/r_on/off)が各passで実描画へ到達するか。
                for (String pgn : presentGroupNames) {
                    if (pgn.contains("light_f") || pgn.contains("light_r")) {
                        String lk = pgn + "|p" + currentPass;
                        if (lightRenderDiag.add(lk)) {
                            RealTrainModUnofficial.LOGGER.info(
                                "[LightRenderDiag] group={} pass={} (reached render)", pgn, currentPass);
                        }
                    }
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

        private boolean shouldSuppressOerMseScriptHoodGroup(String lowerGroupName) {
            return false;
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
                // 室内灯は従来どおり(室内灯ON時のみ)。
                if (interiorOn && isInteriorEmissionGroup(lower)) {
                    return true;
                }
                // 明示的な点灯(_on)のヘッドライト/標識灯/急行灯は発光パスで光らせる。
                // スクリプトは進行方向等で点灯グループ(ex16_light_f_on)だけを render するため、
                // ここに来る _on グループ = 点灯すべきもの。これを発光させないと点灯/消灯の差
                // (発光グロー)が出ず「常に消灯」に見えていた(スペーシア等)。
                // 消灯(_off)や、on/off の区別が無い汎用 "light"(=車体パネルの AlphaBlend,Light 等)は
                // 除外して、車体全体がフラッシュする副作用を防ぐ。
                if (!isLightOffGroup(lower) && isExplicitOnLightGroup(lower)) {
                    return true;
                }
                return false;
            }
            if (isLegacyDisplayGroup(lower)) {
                return true;
            }
            if (isInteriorEmissionGroup(lower)) {
                return interiorOn;
            }
            if (lower.matches("lamp_\\d+")) {
                return false;
            }
            if (lower.contains("doorlamp")) {
                return false;
            }
            if (lower.contains("light") || lower.contains("lamp") || lower.contains("marker")) {
                return shouldRenderLightGroup(train, lower) && !isLightOffGroup(lower);
            }
            return false;
        }

        /**
         * 明示的に「点灯」状態のライト系グループか。light/lamp/marker/led を含み、かつ
         * 末尾が _on / on<数字> 等。on/off ペアを持つ切替式ライト(ヘッドライト・急行灯・標識灯・
         * ドアランプ点灯)だけを発光対象にし、on/off の区別が無い汎用 "light"(車体パネル等)は除外する。
         */
        private static boolean isExplicitOnLightGroup(String lower) {
            if (lower == null) return false;
            boolean lightLike = lower.contains("light") || lower.contains("lamp")
                || lower.contains("marker") || lower.contains("led");
            if (!lightLike) return false;
            return lower.endsWith("_on") || lower.endsWith("-on")
                || lower.matches(".*_on\\d*$") || lower.matches(".*-on\\d*$")
                || lower.matches(".*on\\d+$") || lower.contains("_on_") || lower.contains("-on-");
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
            // RTM scripts own every group registered via registerParts().
            // Even when a script intentionally skips a registered group this frame,
            // baked rendering must not draw it back in (MSE hood/end parts, car-type variants).
            return !scriptRegisteredGroups.isEmpty()
                || !scriptedOpaqueGroups.isEmpty()
                || !scriptedTranslucentGroups.isEmpty();
        }

        /**
         * Called when the script is permanently disabled (render() threw an exception).
         * Clears group ownership so baked render can render all groups normally instead
         * of skipping everything that was registered via registerParts() during init.
         */
        public void clearScriptRegisteredGroups() {
            scriptRegisteredGroups.clear();
            scriptedOpaqueGroups.clear();
            scriptedTranslucentGroups.clear();
            scriptedEmissiveGroups.clear();
        }

        private final java.util.Set<String> doorDiagLogged = new java.util.HashSet<>();
        private final java.util.Set<String> lightRenderDiag = new java.util.HashSet<>();
        public boolean shouldRenderBakedGroup(String groupName, boolean translucent) {
            boolean result = shouldRenderBakedGroup0(groupName, translucent);
            // [診断] ドア二重描画調査: door グループに対するベイク描画判定を (名前+pass) 単位で1回だけ出す。
            String n = normalizeLegacyGroupName(groupName);
            if (n.contains("door")) {
                String key = n + "|" + translucent;
                if (doorDiagLogged.add(key)) {
                    RealTrainModUnofficial.LOGGER.info(
                        "[DoorDiag] group={} translucent={} bakedRender={} registered={}",
                        n, translucent, result, scriptRegisteredGroups.contains(n));
                }
            }
            return result;
        }
        private boolean shouldRenderBakedGroup0(String groupName, boolean translucent) {
            String normalized = normalizeLegacyGroupName(groupName);
            if (normalized.isEmpty()) {
                return true;
            }
            // 擬似シャドウ group (完全一致のみ) は常に skip (ユーザー要望で車両の影は無効化)。
            if (normalized.equalsIgnoreCase("shadow")) {
                return false;
            }
            if (shouldSuppressOerMseScriptHoodGroup(normalized)) {
                return false;
            }
            // 角度曲げ変種 (body-30 / body-180(mx) / bogie1-90 / bogie2-60 等、角度数字付き) は
            // RTM の連結曲げ用代替メッシュ。移植版には曲げ処理が無く、原点姿勢で描くと翼状/斜めに
            // 散乱する。直線の単行列車では一切描かない (台車ルールより前に判定。bogie2-60 もここで除外)。
            // 角度の無い (mx) 鏡像は直線の半身なので対象外。D51 の body-1/2/3 はセクション扱い。
            if (isBendVariant(normalized)) {
                return false;
            }
            // 台車・車輪グループは常にベイクド描画する。スクリプトの render_bogie() が
            // サイレント失敗・座標バグ・getWheelRotationR 未実装等で見えなくなる事例が
            // 多発するため、スクリプト描画と併せて baked からも必ず描く。
            // (二重描画になるが、車輪が消えるよりは重なって見える方を優先 — ユーザー要望)
            boolean isBogieLike = isBogieLikeGroup(normalized);
            if (isBogieLike) {
                return !scriptedOpaqueGroups.contains(normalized)
                    && !scriptedTranslucentGroups.contains(normalized);
            }
            // Groups registered by the script in init() are fully managed by it —
            // skip them in baked render even if they weren't rendered this frame
            // (e.g. body02 when CarType="01": script skips it, baked must too).
            if (scriptRegisteredGroups.contains(normalized)) {
                return false;
            }
            // RTM script は on/off、号車別、表示種別の片方だけを render() で選ぶ。
            // 直接 registerParts されていない兄弟 group を baked が描くと、
            // MSE の幌、スペーシアの急行灯、床下板や表示器の別状態が復活する。
            // そのため script が同じ「切替ファミリー」を 1 つでも登録している場合、
            // 未登録の兄弟も script 管理として baked から落とす。
            if (scriptRegisteredAnyInSelectorFamily(normalized)) {
                return false;
            }
            // "type" セレクタ変種 (type_1 / type_180 / type0 等) は RTM が車種設定で 1 つだけ
            // 表示する front-end タイプ。同じ MQO に複数同梱され、スクリプトは使う 1 つだけ登録する。
            // スクリプトが type* を登録済みなのに、この未登録 type* を baked が原点描画すると
            // 空中に破片として出る (C57_001 の type_180 = ユーザー報告の散乱パネル)。
            // → type* を 1 つでも登録していれば、未登録の type* は描かない。
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

        /**
         * Returns wheel rotation angle in degrees for legacy scripts.
         * Called as renderer.getWheelRotationR(entity) in RTM 1.12.2 scripts.
         */
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
            return System.currentTimeMillis() / 1000L;
        }

        /**
         * Returns legacy renderer scratch data addressed by an integer key.
         */
        public Object getData(long key) {
            return scriptData.getOrDefault(key, 0);
        }

        /**
         * Stores legacy renderer scratch data addressed by an integer key.
         */
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

        /**
         * Returns the current world hour for old scripts that omit the entity argument.
         */
        public int getMCHour() {
            return getMCHour(currentEntity);
        }

        public int getMCMinute(Object entity) {
            int time = getMCTime(entity);
            return (int) ((time % 1000) * 0.06F);
        }

        /**
         * Returns the current world minute for old scripts that omit the entity argument.
         */
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
                // 本家 MachinePartsRenderer.getLightState 準拠:
                //   照明(LIGHT) → レッドストーン電力で 1(点灯) / -1(消灯)。
                //     ※ TileEntityLight.isGettingPower と同じ。スクリプトは pass==2 で
                //       state==1 のとき発光(_lightN)テクスチャを描く。
                //   踏切(CROSSING) → 点滅カウンタ(0/1)、信号(SIGNAL) → 現示状態。
                //     (どちらも getLightCount() が従来通り返す。)
                if (blockEntity.getCategory()
                        == com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory.LIGHT) {
                    return blockEntity.isPowered() ? 1 : -1;
                }
                return blockEntity.getLightCount();
            }
            return -1;
        }

        private long getWorldDayTime(Object entity) {
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
                // Parts() stores a pre-joined comma string as this.groupsStr so Java can read it
                // reliably via getMember() even when the object is opaque to normal reflection.
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

        public int getMetadata(Object entity) { return 0; }

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

        public long getSystemTimeMillis() { return System.currentTimeMillis(); }

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

    /**
     * GL11 即時モード関数の最小シム。RTM 原作のレンダラスクリプトが直叩きする
     * glPushMatrix/glPopMatrix/glTranslated/glRotated/glScalef 等を ScriptModelRenderer の
     * poseStack 操作にブリッジする。
     */
    public static final class GL11Compat {
        private final ScriptModelRenderer renderer;

        public GL11Compat(ScriptModelRenderer renderer) {
            this.renderer = renderer;
        }

        // GL11 行列操作を ScriptModelRenderer 経由で PoseStack に橋渡しする。
        // ScriptModelRenderer 側で NaN/Infinite ガードと matrixDepth 管理を行うため、
        // ここでは委譲するだけ。これで RTM スクリプトの GL11.glPushMatrix → glTranslated →
        // glRotated → model.renderPart("doorL") → glPopMatrix パターンが機能する。
        public void glPushMatrix() { if (renderer != null) renderer.pushMatrix(); }
        public void glPopMatrix() { if (renderer != null) renderer.popMatrix(); }
        public void glTranslatef(float x, float y, float z) { if (renderer != null) renderer.translate(x, y, z); }
        public void glTranslated(double x, double y, double z) { if (renderer != null) renderer.translate((float) x, (float) y, (float) z); }
        public void glRotatef(float angle, float x, float y, float z) { if (renderer != null) renderer.rotate(angle, x, y, z); }
        public void glRotated(double angle, double x, double y, double z) { if (renderer != null) renderer.rotate((float) angle, (float) x, (float) y, (float) z); }
        public void glScalef(float sx, float sy, float sz) { if (renderer != null) renderer.scale(sx, sy, sz); }
        public void glScaled(double sx, double sy, double sz) { if (renderer != null) renderer.scale((float) sx, (float) sy, (float) sz); }

        // 以下は no-op (色/ブレンドは ScriptModelRenderer 側の高レベル API で扱う)
        public void glColor3f(float r, float g, float b) {}
        public void glColor4f(float r, float g, float b, float a) {}
        public void glEnable(int cap) {}
        public void glDisable(int cap) {}
        public void glBlendFunc(int sfactor, int dfactor) {}
        public void glDepthMask(boolean flag) {}
        public void glLineWidth(float width) {}
        public void glNormal3f(float x, float y, float z) {}
        public void glBegin(int mode) {}
        public void glEnd() {}
        public void glVertex3f(float x, float y, float z) {}
        public void glVertex3d(double x, double y, double z) {}
        public void glTexCoord2f(float u, float v) {}
        public void glTexCoord2d(double u, double v) {}

        // よく参照される定数
        public static final int GL_BLEND = 0x0BE2;
        public static final int GL_TEXTURE_2D = 0x0DE1;
        public static final int GL_LIGHTING = 0x0B50;
        public static final int GL_DEPTH_TEST = 0x0B71;
        public static final int GL_CULL_FACE = 0x0B44;
        public static final int GL_ALPHA_TEST = 0x0BC0;
        public static final int GL_SRC_ALPHA = 0x0302;
        public static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
        public static final int GL_ONE = 1;
        public static final int GL_ZERO = 0;
        public static final int GL_TRIANGLES = 0x0004;
        public static final int GL_QUADS = 0x0007;
    }

    /**
     * スクリプトで {@code new Parts("bogieF", "wheelF1", ...)} と呼ばれる用のビルダー。
     * 引数の文字列群を保持し、{@code ScriptModelRenderer.registerParts(parts)} に渡された時点で
     * グループ名を抽出する。
     */
    public static final class PartsBuilder {
        private final java.util.List<String> groupNames = new java.util.ArrayList<>();

        public PartsBuilder(Object arg0) {
            addArg(arg0);
        }

        public PartsBuilder(Object arg0, Object arg1) {
            addArg(arg0); addArg(arg1);
        }

        public PartsBuilder(Object arg0, Object arg1, Object arg2) {
            addArg(arg0); addArg(arg1); addArg(arg2);
        }

        public PartsBuilder(Object arg0, Object arg1, Object arg2, Object arg3) {
            addArg(arg0); addArg(arg1); addArg(arg2); addArg(arg3);
        }

        public PartsBuilder(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
            addArg(arg0); addArg(arg1); addArg(arg2); addArg(arg3); addArg(arg4);
        }

        public PartsBuilder(Object arg0, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
            addArg(arg0); addArg(arg1); addArg(arg2); addArg(arg3); addArg(arg4); addArg(arg5);
        }

        public PartsBuilder(Object[] args) {
            if (args != null) for (Object a : args) addArg(a);
        }

        private void addArg(Object a) {
            if (a == null) return;
            String s = String.valueOf(a);
            if (!s.isBlank()) groupNames.add(s);
        }

        public java.util.List<String> getGroupNames() { return groupNames; }

        public String getGroupsStr() {
            return String.join(",", groupNames);
        }
    }

    /**
     * RTM 原作の jp.ngt.ngtlib.io.NGTText スタブ。
     * Nashorn のオーバーロード解決は不安定で、複数の readText() オーバーロードがあると
     * "is not a function" エラーになることがある。各メソッドを 1 個の定義に統一する。
     * readText は本来テキストファイル内容を List<String> で返す。
     */
    public static final class NGTTextCompat {
        public java.util.List<String> readText(Object resource) {
            return new java.util.ArrayList<>();
        }
        public String[] readTextLines(Object resource) {
            return new String[0];
        }
        public void writeText(Object a) {}
        public String loadText(Object a) { return ""; }
        public String createText(Object a) { return ""; }
        public String getText(Object a) { return ""; }
        public String getFormattedText(Object a) { return ""; }
        public String getString(Object a) { return ""; }
        public void appendSibling(Object a) {}
        public void appendText(Object a) {}
        public void applyTextStyles(Object a) {}
    }

    /** RTM 原作の jp.ngt.ngtlib.io.NGTLog スタブ。debug/info/warn/error を Java の logger に橋渡し。 */
    public static final class NGTLogCompat {
        public void debug(Object... args) { /* silent */ }
        public void info(Object... args) {
            if (args != null && args.length > 0) {
                RealTrainModUnofficial.LOGGER.info("[NGTLog] {}", args[0]);
            }
        }
        public void warn(Object... args) {
            if (args != null && args.length > 0) {
                RealTrainModUnofficial.LOGGER.warn("[NGTLog] {}", args[0]);
            }
        }
        public void error(Object... args) {
            if (args != null && args.length > 0) {
                RealTrainModUnofficial.LOGGER.error("[NGTLog] {}", args[0]);
            }
        }
    }

    /** RTM 原作の jp.ngt.ngtlib.util.NGTUtil スタブ。 */
    public static final class NGTUtilCompat {
        public long getCurrentTime() { return System.currentTimeMillis(); }
        public long getUniqueId() { return System.nanoTime(); }
        public boolean isClient() { return true; }
        public Object getCurrentWorld() { return null; }
        public Object getCurrentPlayer() {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                return mc == null ? null : mc.player;
            } catch (Throwable t) { return null; }
        }
        public String getMCVersion() { return "1.21.1"; }
        public boolean isLanguage(String code) {
            try {
                return code != null && code.equalsIgnoreCase(
                    net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected()
                );
            } catch (Throwable t) { return false; }
        }
    }

    /** RTM 原作の jp.ngt.ngtlib.math.NGTMath スタブ。 */
    public static final class NGTMathCompat {
        public double toRadians(double deg) { return Math.toRadians(deg); }
        public double toDegrees(double rad) { return Math.toDegrees(rad); }
        public float sin(float a) { return (float) Math.sin(a); }
        public float cos(float a) { return (float) Math.cos(a); }
        public float tan(float a) { return (float) Math.tan(a); }
        public float atan2(float y, float x) { return (float) Math.atan2(y, x); }
        // RTM 原作 NGTMath は static getSin/getCos/getAtan2 (引数 double) を持つ。
        // user script が importPackage(Packages.jp.ngt.ngtlib.math) で NGTMath を
        // Java 参照に上書きするケースに備えてここに用意する (Render_c12.js 等)。
        public double getSin(double rad) { return Math.sin(rad); }
        public double getCos(double rad) { return Math.cos(rad); }
        public double getTan(double rad) { return Math.tan(rad); }
        public double getAtan2(double y, double x) { return Math.atan2(y, x); }
        public double getSqrt(double x) { return Math.sqrt(x); }
        public float sqrt(float x) { return (float) Math.sqrt(x); }
        public float floor(float x) { return (float) Math.floor(x); }
        public float ceil(float x) { return (float) Math.ceil(x); }
        public float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
        public double clampD(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
        public float normalizeAngle(float a) {
            while (a >= 180.0F) a -= 360.0F;
            while (a < -180.0F) a += 360.0F;
            return a;
        }
    }
}
