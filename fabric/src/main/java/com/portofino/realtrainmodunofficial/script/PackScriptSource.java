package com.portofino.realtrainmodunofficial.script;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.ngtlib.io.NGTFileLoader;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * パックスクリプトの共通前処理 (クライアント/サーバー両用)。
 * //include 解決・文字コード判定・旧 FQN の互換リマップ・プリリュード。
 */
public final class PackScriptSource {

    /**
     * Nashorn 実行前に評価される共通プリリュード。
     * importPackage は未定義名しか束縛しないため、ここで束縛した var が常に勝つ。
     */
    /**
     * GL 束縛だけを分離したもの。
     * モデル添付の描画スクリプト経路 (TrainScriptSystem.loadScript) は GL 呼び出しを
     * ScriptModelRenderer の OpList に記録するため、GL11 は専用のシムを put 済みで、
     * ここで上書きしてはいけない。そのため GL 以外の束縛を #PRELUDE_NO_GL として切り出す。
     */
    public static final String PRELUDE_GL =
            "var GL11 = Java.type('jp.ngt.ngtlib.renderer.GL11Facade');\n" +
            "var GL12 = GL11;\n" +
            // LWJGL2 の BufferUtils / 1.12 の OpenGlHelper (NGTO Builder 2 が行列バッファと
            // ブレンド指定に使う)。未定義だとそこでスクリプトが止まる。
            "var BufferUtils = Java.type('jp.ngt.ngtlib.renderer.BufferUtilsCompat');\n" +
            "var OpenGlHelper = Java.type('jp.ngt.ngtlib.renderer.OpenGlHelperCompat');\n"
            // Parts も描画機構に依存する。実 jp.ngt.rtm.render.Parts は GLRecorder へ描くので、
            // OpList 経路 (ScriptModelRenderer) が自前で用意した renderer 対応の Parts を
            // 上書きしてはいけない (上書きすると parts.render が全て空振りする)。
            + bindOpt("Parts", "jp.ngt.rtm.render.Parts")
            + bindOpt("ActionParts", "jp.ngt.rtm.render.ActionParts");

    public static final String PRELUDE_NO_GL =
            // rtm-ts の Multi-target 構成対策。
            // var RTMX_COMPAT_TARGETS = RTMX_COMPAT_TARGETS || {};
            // を先頭に置き、その後の loader 内で各ターゲット (kaizpatch/mc1710/mc1122) の
            // IIFE が RTMX_COMPAT_TARGETS.<target> = ... を代入する設計。
            "var RTMX_COMPAT_TARGETS = (typeof RTMX_COMPAT_TARGETS !== 'undefined' && RTMX_COMPAT_TARGETS) ? RTMX_COMPAT_TARGETS : {};\n" +
            "var MathHelper = Java.type('jp.ngt.mccompat.MathHelper');\n" +
            // importPackage(net.minecraft.util) 経由の裸 ResourceLocation を互換クラスへ束縛
            // (net.minecraft.util に実クラスを置くとバニラと split package でモジュール解決が落ちる)
            "var ResourceLocation = Java.type('jp.ngt.mccompat.ResourceLocation');\n" +
            // LWJGL2 入力 (SRB3/NGTO Builder)
            "var Keyboard = Java.type('jp.ngt.mccompat.input.Keyboard');\n" +
            "var Mouse = Java.type('jp.ngt.mccompat.input.Mouse');\n" +
            // 1.7.10 net.minecraft.init.Blocks
            "var Blocks = Java.type('jp.ngt.mccompat.init.Blocks');\n" +
            // 1.7.10 ブロッククラス名 → 1.21 実クラス (instanceof 用)
            "var BlockStairs = Java.type('" + net.minecraft.world.level.block.StairBlock.class.getName() + "');\n" +
            "var BlockDoor = Java.type('" + net.minecraft.world.level.block.DoorBlock.class.getName() + "');\n" +
            "var BlockFenceGate = Java.type('" + net.minecraft.world.level.block.FenceGateBlock.class.getName() + "');\n" +
            "var BlockLog = Java.type('" + net.minecraft.world.level.block.RotatedPillarBlock.class.getName() + "');\n" +
            "var BlockOldLog = BlockLog;\n" +
            "var BlockNewLog = BlockLog;\n" +
            "var BlockLadder = Java.type('" + net.minecraft.world.level.block.LadderBlock.class.getName() + "');\n" +
            "var BlockButton = Java.type('" + net.minecraft.world.level.block.ButtonBlock.class.getName() + "');\n" +
            "var BlockSlab = Java.type('" + net.minecraft.world.level.block.SlabBlock.class.getName() + "');\n" +
            "var Block = Java.type('" + net.minecraft.world.level.block.Block.class.getName() + "');\n" +
            "var ITileEntityProvider = Java.type('" + net.minecraft.world.level.block.EntityBlock.class.getName() + "');\n" +
            // 1.7.10 TextureMap (ブロックアトラス)。NGTO Builder のプレビューが field_110575_b を参照
            "var TextureMap = Java.type('jp.ngt.mccompat.TextureMap');\n" +
            "var ItemBlock = Java.type('" + net.minecraft.world.item.BlockItem.class.getName() + "');\n" +
            // 1.7.10 NBT
            "var NBTTagCompound = Java.type('jp.ngt.mccompat.nbt.NBTTagCompound');\n" +
            "var NBTTagList = Java.type('jp.ngt.mccompat.nbt.NBTTagList');\n" +
            // jp.ngt 系の確定バインド — importPackage 経由の遅延解決が実行時に
            // "is not defined" になるケース (SRB3 の RTMItem 等) があるため、
            // スクリプトが未修飾名で使うクラスはここで直接束縛する。
            // (存在しないクラスでエンジンごと死なないよう個別 try)
            bindOpt("RTMCore", "jp.ngt.rtm.RTMCore") +
            bindOpt("RTMItem", "jp.ngt.rtm.RTMItem") +
            bindOpt("RTMBlock", "jp.ngt.rtm.RTMBlock") +
            bindOpt("RTMRail", "jp.ngt.rtm.RTMRail") +
            bindOpt("ItemRail", "jp.ngt.rtm.item.ItemRail") +
            bindOpt("RailPosition", "jp.ngt.rtm.rail.util.RailPosition") +
            bindOpt("RailMapBasic", "jp.ngt.rtm.rail.util.RailMapBasic") +
            bindOpt("RailMaker", "jp.ngt.rtm.rail.util.RailMaker") +
            bindOpt("RailDir", "jp.ngt.rtm.rail.util.RailDir") +
            bindOpt("TileEntityLargeRailBase", "jp.ngt.rtm.rail.TileEntityLargeRailBase") +
            bindOpt("TileEntityLargeRailCore", "jp.ngt.rtm.rail.TileEntityLargeRailCore") +
            bindOpt("NGTLog", "jp.ngt.ngtlib.io.NGTLog") +
            // SL パックが蒸気/煙で使う (importPackage は no-op なので未修飾名を直接束縛)
            bindOpt("EnumParticleTypes", "jp.ngt.mccompat.EnumParticleTypes") +
            bindOpt("NGTMath", "jp.ngt.ngtlib.math.NGTMath") +
            bindOpt("Vec3", "jp.ngt.ngtlib.math.Vec3") +
            // 本家 RenderMotor / RenderClutch / RenderReversGear が
            // renderer.getRotation(entity, Axis.POSITIVE_Y) で使う。
            bindOpt("Axis", "jp.ngt.ngtlib.math.Axis") +
            bindOpt("NGTUtil", "jp.ngt.ngtlib.util.NGTUtil") +
            bindOpt("NGTUtilClient", "jp.ngt.ngtlib.util.NGTUtilClient") +
            bindOpt("MCWrapper", "jp.ngt.ngtlib.util.MCWrapper") +
            bindOpt("MCWrapperClient", "jp.ngt.ngtlib.util.MCWrapperClient") +
            bindOpt("BlockUtil", "jp.ngt.ngtlib.block.BlockUtil") +
            bindOpt("TileEntityCustom", "jp.ngt.ngtlib.block.TileEntityCustom") +
            bindOpt("NGTObject", "jp.ngt.ngtlib.block.NGTObject") +
            bindOpt("BlockSet", "jp.ngt.ngtlib.block.BlockSet") +
            bindOpt("GLHelper", "jp.ngt.ngtlib.renderer.GLHelper") +
            bindOpt("NGTRenderer", "jp.ngt.ngtlib.renderer.NGTRenderer") +
            bindOpt("NGTRenderHelper", "jp.ngt.ngtlib.renderer.NGTRenderHelper") +
            bindOpt("NGTObjectRenderer", "jp.ngt.ngtlib.renderer.NGTObjectRenderer") +
            bindOpt("MCTE", "jp.ngt.mcte.MCTE") +
            bindOpt("ItemMiniature", "jp.ngt.mcte.item.ItemMiniature") +
            // 車両/レール描画スクリプトが直接 new する描画クラス
            bindOpt("ActionType", "jp.ngt.rtm.render.ActionType") +
            bindOpt("ModelObject", "jp.ngt.rtm.render.ModelObject") +
            bindOpt("PartsRenderer", "jp.ngt.rtm.render.PartsRenderer") +
            bindOpt("VehiclePartsRenderer", "jp.ngt.rtm.render.VehiclePartsRenderer") +
            bindOpt("RailPartsRenderer", "jp.ngt.rtm.render.RailPartsRenderer") +
            bindOpt("MachinePartsRenderer", "jp.ngt.rtm.render.MachinePartsRenderer") +
            bindOpt("SignalPartsRenderer", "jp.ngt.rtm.render.SignalPartsRenderer") +
            bindOpt("WirePartsRenderer", "jp.ngt.rtm.render.WirePartsRenderer") +
            bindOpt("OrnamentPartsRenderer", "jp.ngt.rtm.render.OrnamentPartsRenderer") +
            bindOpt("TileEntityPartsRenderer", "jp.ngt.rtm.render.TileEntityPartsRenderer") +
            bindOpt("EntityPartsRenderer", "jp.ngt.rtm.render.EntityPartsRenderer") +
            bindOpt("RenderPass", "jp.ngt.rtm.render.RenderPass") +
            // 状態・設定 (スクリプトが最も多く触る)
            bindOpt("ResourceState", "jp.ngt.rtm.modelpack.state.ResourceState") +
            bindOpt("DataMap", "jp.ngt.rtm.modelpack.state.DataMap") +
            bindOpt("TrainState", "jp.ngt.rtm.entity.train.util.TrainState") +
            bindOpt("TrainConfig", "jp.ngt.rtm.modelpack.cfg.TrainConfig") +
            bindOpt("ModelPackManager", "jp.ngt.rtm.modelpack.ModelPackManager") +
            bindOpt("Formation", "jp.ngt.rtm.entity.train.util.Formation") +
            bindOpt("EnumNotch", "jp.ngt.rtm.entity.train.util.EnumNotch") +
            // レール
            bindOpt("Point", "jp.ngt.rtm.rail.util.Point") +
            bindOpt("RailMap", "jp.ngt.rtm.rail.util.RailMap") +
            bindOpt("RailProperty", "jp.ngt.rtm.rail.util.RailProperty") +
            bindOpt("SwitchType", "jp.ngt.rtm.rail.util.SwitchType") +
            bindOpt("MarkerState", "jp.ngt.rtm.rail.util.MarkerState") +
            bindOpt("TileEntityLargeRailSwitchCore", "jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore") +
            bindOpt("TileEntityMarker", "jp.ngt.rtm.rail.TileEntityMarker") +
            // モデル (頂点操作をするスクリプト用)
            bindOpt("NGTTessellator", "jp.ngt.ngtlib.renderer.NGTTessellator") +
            bindOpt("ModelLoader", "jp.ngt.ngtlib.renderer.model.ModelLoader") +
            bindOpt("VecAccuracy", "jp.ngt.ngtlib.renderer.model.VecAccuracy") +
            bindOpt("GroupObject", "jp.ngt.ngtlib.renderer.model.GroupObject") +
            bindOpt("Face", "jp.ngt.ngtlib.renderer.model.Face") +
            bindOpt("Vertex", "jp.ngt.ngtlib.renderer.model.Vertex") +
            bindOpt("TextureCoordinate", "jp.ngt.ngtlib.renderer.model.TextureCoordinate") +
            bindOpt("TextureSet", "jp.ngt.ngtlib.renderer.model.TextureSet") +
            bindOpt("Material", "jp.ngt.ngtlib.renderer.model.Material") +
            bindOpt("PolygonModel", "jp.ngt.ngtlib.renderer.model.PolygonModel") +
            // IO・ワールド
            bindOpt("NGTText", "jp.ngt.ngtlib.io.NGTText") +
            bindOpt("NGTFileLoader", "jp.ngt.ngtlib.io.NGTFileLoader") +
            bindOpt("ScriptUtil", "jp.ngt.ngtlib.io.ScriptUtil") +
            bindOpt("NGTWorld", "jp.ngt.ngtlib.world.NGTWorld") +
            bindOpt("TileEntityPlaceable", "jp.ngt.ngtlib.block.TileEntityPlaceable") +
            // 電気
            bindOpt("Connection", "jp.ngt.rtm.electric.Connection") +
            bindOpt("WireManager", "jp.ngt.rtm.electric.WireManager") +
            bindOpt("SignalLevel", "jp.ngt.rtm.electric.SignalLevel") +
            bindOpt("BlockInsulator", "jp.ngt.rtm.electric.BlockInsulator") +
            bindOpt("TileEntityInsulator", "jp.ngt.rtm.electric.TileEntityInsulator") +
            bindOpt("TileEntityConnectorBase", "jp.ngt.rtm.electric.TileEntityConnectorBase") +
            // エンティティ・アイテム
            bindOpt("EntityVehicleBase", "jp.ngt.rtm.entity.vehicle.EntityVehicleBase") +
            bindOpt("EntityTrainBase", "jp.ngt.rtm.entity.train.EntityTrainBase") +
            bindOpt("EntityBogie", "jp.ngt.rtm.entity.train.EntityBogie") +
            bindOpt("SoundUpdaterTrain", "jp.ngt.rtm.sound.SoundUpdaterTrain") +
            bindOpt("ItemInstalledObject", "jp.ngt.rtm.item.ItemInstalledObject") +
            bindOpt("RTMResource", "jp.ngt.rtm.RTMResource") +
            bindOpt("EntityVehiclePart", "jp.ngt.rtm.entity.train.parts.EntityVehiclePart") +
            // 1.7.10 のバニラクラス名 (スクリプトが instanceof で使う)
            bindOpt("Entity", net.minecraft.world.entity.Entity.class) +
            bindOpt("EntityPlayer", net.minecraft.world.entity.player.Player.class) +
            bindOpt("EntityLivingBase", net.minecraft.world.entity.LivingEntity.class) +
            bindOpt("World", net.minecraft.world.level.Level.class) +
            bindOpt("ItemStack", net.minecraft.world.item.ItemStack.class) +
            bindOpt("Item", net.minecraft.world.item.Item.class) +
            bindOpt("TileEntity", net.minecraft.world.level.block.entity.BlockEntity.class) +
            // 1.12 Forge の mod 存在チェック (rtm-ts の mc1122 ターゲットが class body で呼ぶ)
            bindOpt("Loader", "net.minecraftforge.fml.common.Loader") +
            // importPackage(net.minecraft.client) だけで参照される Minecraft。
            // ★必ず bindOpt で束縛すること。このシムはクライアント専用クラスを引き込むので、
            //   素の Java.type だとサーバーでプリリュードごと落ちてスクリプトが全滅する。
            //   bindOpt なら失敗しても未定義になるだけで、他の束縛は生きる
            //   (サーバー側スクリプトが Minecraft を使うこと自体が誤り)。
            bindOpt("Minecraft", "jp.ngt.mccompat.Minecraft");

    /** GL 束縛込みの完全版 (描画を GLRecorder に記録する通常経路用)。 */
    /**
     * ES6 以降の組み込み関数の補完。
     * Nashorn は構文としては ES6 まで見るが、標準ライブラリは ES5 のままで
     * Object.assign や Array.from すら無い (実測)。
     */
    public static final String POLYFILL =
        "if (!Object.assign) Object.assign = function (t) { for (var i = 1; i < arguments.length; i++) "
        + "{ var s = arguments[i]; if (s) for (var k in s) if (Object.prototype.hasOwnProperty.call(s, k)) t[k] = s[k]; } return t; };\n"
        + "if (!Object.values) Object.values = function (o) { var r = []; for (var k in o) "
        + "if (Object.prototype.hasOwnProperty.call(o, k)) r.push(o[k]); return r; };\n"
        + "if (!Object.entries) Object.entries = function (o) { var r = []; for (var k in o) "
        + "if (Object.prototype.hasOwnProperty.call(o, k)) r.push([k, o[k]]); return r; };\n"
        + "if (!Array.from) Array.from = function (a, f) { var r = [], n = a.length === undefined ? 0 : a.length; "
        + "for (var i = 0; i < n; i++) r.push(f ? f(a[i], i) : a[i]); return r; };\n"
        + "if (!Array.of) Array.of = function () { return Array.prototype.slice.call(arguments); };\n"
        + "if (!Array.prototype.includes) Array.prototype.includes = function (v) { return this.indexOf(v) >= 0; };\n"
        + "if (!Array.prototype.find) Array.prototype.find = function (f, t) { for (var i = 0; i < this.length; i++) "
        + "if (f.call(t, this[i], i, this)) return this[i]; return undefined; };\n"
        + "if (!Array.prototype.findIndex) Array.prototype.findIndex = function (f, t) { for (var i = 0; i < this.length; i++) "
        + "if (f.call(t, this[i], i, this)) return i; return -1; };\n"
        + "if (!Array.prototype.fill) Array.prototype.fill = function (v, s, e) { s = s || 0; "
        + "e = e === undefined ? this.length : e; for (var i = s; i < e; i++) this[i] = v; return this; };\n"
        + "if (!String.prototype.includes) String.prototype.includes = function (v) { return this.indexOf(v) >= 0; };\n"
        + "if (!String.prototype.startsWith) String.prototype.startsWith = function (v, p) { return this.substr(p || 0, v.length) === v; };\n"
        + "if (!String.prototype.endsWith) String.prototype.endsWith = function (v, l) { l = l === undefined ? this.length : l; "
        + "return this.substring(l - v.length, l) === v; };\n"
        + "if (!String.prototype.repeat) String.prototype.repeat = function (n) { var r = ''; for (var i = 0; i < n; i++) r += this; return r; };\n"
        + "if (!String.prototype.padStart) String.prototype.padStart = function (n, p) { p = p === undefined ? ' ' : p; "
        + "var s = String(this); while (s.length < n) s = p + s; return s.length > n ? s.slice(s.length - n) : s; };\n"
        + "if (!String.prototype.padEnd) String.prototype.padEnd = function (n, p) { p = p === undefined ? ' ' : p; "
        + "var s = String(this); while (s.length < n) s += p; return s.slice(0, Math.max(n, String(this).length)); };\n"
        + "if (!String.prototype.trimStart) String.prototype.trimStart = function () { return String(this).replace(/^\\s+/, ''); };\n"
        + "if (!String.prototype.trimEnd) String.prototype.trimEnd = function () { return String(this).replace(/\\s+$/, ''); };\n"
        + "if (!Number.isInteger) Number.isInteger = function (v) { return typeof v === 'number' && isFinite(v) && Math.floor(v) === v; };\n"
        + "if (!Number.isFinite) Number.isFinite = function (v) { return typeof v === 'number' && isFinite(v); };\n"
        + "if (!Number.isNaN) Number.isNaN = function (v) { return v !== v; };\n"
        + "if (!Number.parseFloat) Number.parseFloat = parseFloat;\n"
        + "if (!Number.parseInt) Number.parseInt = parseInt;\n"
        + "if (!Math.trunc) Math.trunc = function (v) { return v < 0 ? Math.ceil(v) : Math.floor(v); };\n"
        + "if (!Math.sign) Math.sign = function (v) { return v > 0 ? 1 : (v < 0 ? -1 : 0); };\n"
        + "if (!Math.hypot) Math.hypot = function (a, b) { return Math.sqrt(a * a + b * b); };\n"
        + "if (!Math.cbrt) Math.cbrt = function (v) { return v < 0 ? -Math.pow(-v, 1 / 3) : Math.pow(v, 1 / 3); };\n"
        + "if (!Math.log2) Math.log2 = function (v) { return Math.log(v) / Math.LN2; };\n"
        + "if (!Math.log10) Math.log10 = function (v) { return Math.log(v) / Math.LN10; };\n";

    public static final String PRELUDE = PRELUDE_GL + PRELUDE_NO_GL + POLYFILL;

    /**
     * ★バニラのクラスはこちら (class リテラル版) を使うこと。
     * クラス名を文字列で書くと、Fabric の配布版では見つからない。
     */
    private static String bindOpt(String name, Class<?> type) {
        return bindOpt(name, type.getName());
    }

    private static String bindOpt(String name, String fqn) {
        // 失敗したクラス名は __bindFails に集約 (ScriptUtil.doScript がログに出す)
        return "try { var " + name + " = Java.type('" + fqn + "'); } catch (__e) { "
                + "if (typeof __bindFails === 'undefined') { __bindFails = ''; } "
                + "__bindFails += '" + name + " '; }\n";
    }

    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*//include\\s*<([^>]+)>", Pattern.MULTILINE);

    private static final String[][] FQN_REMAP = {
            // LWJGL直束縛 (var GL11 = Packages.org.lwjgl.opengl.GL11 等) を互換クラスへ。
            // LWJGL3の実GL11に解決されると、固定機能関数(glPushMatrix等)は関数ポインタNULLで
            // jni_FatalError→プロセスabort (200が設置直後に落ちていた原因)。
            {"Packages.org.lwjgl.opengl.GL11", "Packages.jp.ngt.ngtlib.renderer.GL11Facade"},
            {"Packages.org.lwjgl.opengl.GL12", "Packages.jp.ngt.ngtlib.renderer.GL11Facade"},
            {"Packages.org.lwjgl.BufferUtils", "Packages.jp.ngt.ngtlib.renderer.BufferUtilsCompat"},
            {"Packages.net.minecraft.client.renderer.OpenGlHelper", "Packages.jp.ngt.ngtlib.renderer.OpenGlHelperCompat"},
            {"Packages.org.lwjgl.input.Keyboard", "Packages.jp.ngt.mccompat.input.Keyboard"},
            {"Packages.org.lwjgl.input.Mouse", "Packages.jp.ngt.mccompat.input.Mouse"},
            {"Packages.net.minecraft.util.ResourceLocation", "Packages.jp.ngt.mccompat.ResourceLocation"},
            {"Packages.net.minecraft.client.renderer.texture.TextureUtil", "Packages.jp.ngt.mccompat.TextureUtil"},
            {"Packages.net.minecraft.client.renderer.texture.DynamicTexture", "Packages.jp.ngt.mccompat.DynamicTexture"},
            {"Packages.net.minecraft.client.Minecraft", "Packages.jp.ngt.mccompat.Minecraft"},
            {"Packages.net.minecraft.util.math.BlockPos", "Packages." + net.minecraft.core.BlockPos.class.getName()},
            // NGTO Builder が hasTileEntity で使う: 1.7.10 ITileEntityProvider = 1.21 EntityBlock
            // (BE を持つブロックのマーカーインタフェース)。未対応だと設置経路で instanceof が落ちる。
            {"Packages.net.minecraft.block.ITileEntityProvider", "Packages." + net.minecraft.world.level.block.EntityBlock.class.getName()},
            // ★1.7.10 のバニラ FQN。
            // instanceof の右辺に渡った瞬間に
            // "instanceof must be called with a javascript or java object" で
            // スクリプトが停止する (NGTOBuilder2 のビーム設置が動かなかった原因)。
            {"Packages.net.minecraft.block.BlockFenceGate", "Packages." + net.minecraft.world.level.block.FenceGateBlock.class.getName()},
            {"Packages.net.minecraft.block.BlockLadder", "Packages." + net.minecraft.world.level.block.LadderBlock.class.getName()},
            {"Packages.net.minecraft.block.BlockButton", "Packages." + net.minecraft.world.level.block.ButtonBlock.class.getName()},
            {"Packages.net.minecraft.block.BlockStairs", "Packages." + net.minecraft.world.level.block.StairBlock.class.getName()},
            {"Packages.net.minecraft.block.BlockDoor", "Packages." + net.minecraft.world.level.block.DoorBlock.class.getName()},
            {"Packages.net.minecraft.block.BlockLog", "Packages." + net.minecraft.world.level.block.RotatedPillarBlock.class.getName()},
            {"Packages.net.minecraft.entity.player.EntityPlayer", "Packages." + net.minecraft.world.entity.player.Player.class.getName()},
            {"Packages.net.minecraft.nbt.NBTTagCompound", "Packages.jp.ngt.mccompat.nbt.NBTTagCompound"},
            {"Packages.net.minecraft.init.Blocks", "Packages.jp.ngt.mccompat.init.Blocks"},
            {"Packages.net.minecraft.client.renderer.texture.TextureMap", "Packages.jp.ngt.mccompat.TextureMap"},
            {"Packages.net.minecraft.world.EnumSkyBlock", "Packages.jp.ngt.mccompat.EnumSkyBlock"},
            {"Packages.net.minecraft.util.MathHelper", "Packages.jp.ngt.mccompat.MathHelper"},
            {"Packages.net.minecraft.util.math.MathHelper", "Packages.jp.ngt.mccompat.MathHelper"},
            {"Packages.net.minecraft.util.EnumParticleTypes", "Packages.jp.ngt.mccompat.EnumParticleTypes"},
    };

    /**
     * Packages.net.minecraft.block.Block (裸の Block 型)。
     * 上の FQN_REMAP は単純な String.replace なので、ここに素の Block を
     * 並べると未収録の BlockXxx まで前方一致で壊す。
     */
    private static final Pattern BARE_VANILLA_BLOCK =
        Pattern.compile("Packages\\.net\\.minecraft\\.block\\.Block(?![A-Za-z0-9_$])");

    /**
     * 1.7.10 Block の static メソッド呼び出し (getBlockFromItem 等) を互換クラスへ。
     * 前にドットが無い場合のみ置換 (FQN 内の二重置換を防ぐ)。
     */
    private static final Pattern[] BLOCK_STATIC_PATTERNS = {
            Pattern.compile("(?<![.\\w])Block\\.func_149634_a\\("),
            Pattern.compile("(?<![.\\w])Block\\.func_149682_b\\("),
            Pattern.compile("(?<![.\\w])Block\\.func_149729_e\\("),
    };
    private static final String[] BLOCK_STATIC_REPLACEMENTS = {
            "Packages.jp.ngt.mccompat.block.Block.func_149634_a(",
            "Packages.jp.ngt.mccompat.block.Block.func_149682_b(",
            "Packages.jp.ngt.mccompat.block.Block.func_149729_e(",
    };

    private PackScriptSource() {
    }

    /** include 解決 + 互換リマップ済みのソースを返す (プリリュードは含まない)。 */
    /**
     * .seatRotation を .getSeatRotationRaw に置き換えるためのパターン。
     * .getSeatRotation には (直前が "get" なので) マッチしない。
     */
    private static final Pattern SEAT_ROTATION_FIELD = Pattern.compile("\\.seatRotation\\b(?!\\s*\\()");

    /**
     * var X = X; (FQN リマップの結果生じる自己代入宣言)。
     * var は巻き上げられるので、宣言の時点でグローバル X が undefined に潰れ、
     * PRELUDE で束縛したクラスが見えなくなる。宣言ごと消すのが正しい。
     */
    private static final Pattern SELF_ASSIGN_DECL =
            Pattern.compile("\\bvar\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*\\1\\s*;");

    public static String prepare(String source) {
        return prepare(source, null);
    }

    /**
     * スクリプトを実行できる形にする。
     * 型を取り除き、class を ES5 化する。
     * @param path 元のファイルのパス。.ts のときだけ TypeScript として
     */
    public static String prepare(String source, String path) {
        if (TypeScriptTranspiler.isTypeScript(path)) {
            String problem = TypeScriptTranspiler.diagnose(source);
            if (problem != null) {
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.warn(
                    "[RTMU/TS] {}: {}", path, problem);
            }
            source = TypeScriptTranspiler.toJavaScript(source);
        }
        String out = resolveIncludes(source, new HashSet<>());
        out = remapLegacyClasses(out);
        out = SELF_ASSIGN_DECL.matcher(out).replaceAll("");
        out = remapVanillaOnlyMethods(out);
        return remapFieldAccess(out);
    }

    /**
     * レシーバがバニラのインスタンスで、シムで包むことも継承することもできない
     * MCP 名メソッドを、静的ヘルパー呼び出しへ書き換える。
     * blockSet.block.func_149716_u の block は
     * jp.ngt.ngtlib.block.BlockSet のフィールドで型はバニラの Block。
     */
    private static String remapVanillaOnlyMethods(String source) {
        String out = VANILLA_HAS_TILE_ENTITY.matcher(source)
            .replaceAll("Packages.jp.ngt.mccompat.init.Blocks.func_149716_u($1)");
        out = VANILLA_TILE_READ_NBT.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.tileentity.TileEntityCompat.func_145839_a($1, $2)");
        out = VANILLA_TILE_WRITE_NBT.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.tileentity.TileEntityCompat.func_145841_b($1, $2)");
        out = VANILLA_ENTITY_UUID.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.EntityCompatUtil.func_110124_au($1)");
        out = VANILLA_CLOSE_SCREEN.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.EntityCompatUtil.func_71053_j($1)");
        out = VANILLA_BLOCKPOS_OFFSET.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.VanillaCompat.func_177967_a($1, $2)");
        out = VANILLA_FACING_INDEX.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.VanillaCompat.func_176745_a($1)");
        out = VANILLA_ITEMBLOCK_GET_BLOCK.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.VanillaCompat.func_179223_d($1)");
        out = VANILLA_TILE_SET_POS.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.VanillaCompat.func_174878_a($1, $2)");
        return out;
    }

    /** <式>.func_177967_a(facing, n) = BlockPos.offset。BlockPos は実バニラ型で拡張できない。 */
    private static final Pattern VANILLA_BLOCKPOS_OFFSET =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_177967_a\\(([^()]*)\\)");

    /** <式>.func_176745_a = EnumFacing.getIndex。 */
    private static final Pattern VANILLA_FACING_INDEX =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_176745_a\\(\\)");

    /** <式>.func_173_d = ItemBlock.getBlock。 */
    private static final Pattern VANILLA_ITEMBLOCK_GET_BLOCK =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_179223_d\\(\\)");

    /** <式>.func_174878_a(pos) = TileEntity.setPos。 */
    private static final Pattern VANILLA_TILE_SET_POS =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_174878_a\\(([^()]*(?:\\([^()]*\\))?[^()]*)\\)");

    /** <式>.func_145839_a(nbt) = TileEntity.readFromNBT。 */
    private static final Pattern VANILLA_TILE_READ_NBT =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_145839_a\\(([^()]*)\\)");

    /** <式>.func_145841_b(nbt) / func_189515_b = TileEntity.writeToNBT。 */
    private static final Pattern VANILLA_TILE_WRITE_NBT =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_(?:145841_b|189515_b)\\(([^()]*)\\)");

    /** <式>.func_110124_au = Entity.getUniqueID。 */
    private static final Pattern VANILLA_ENTITY_UUID =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_110124_au\\(\\)");

    /** <式>.func_71053_j = EntityPlayer.closeScreen。 */
    private static final Pattern VANILLA_CLOSE_SCREEN =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_71053_j\\(\\)");

    /** <式>.func_149716_u = Block.hasTileEntity。 */
    private static final Pattern VANILLA_HAS_TILE_ENTITY =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_149716_u\\(\\)");

    /**
     * Nashorn の「フィールドより getter を優先する」仕様を回避するための書き換え。
     * 本家 EntityVehicleBase は public int seatRotation と
     * float getSeatRotation (= seatRotation / 45) の両方を持つ。
     */
    public static String remapFieldAccess(String source) {
        String out = SEAT_ROTATION_FIELD.matcher(source).replaceAll(".getSeatRotationRaw()");
        // tileEntity.field_145850_b = TileEntity.worldObj。
        // フィールドを足せないため、静的ヘルパーへ回す。
        out = TILE_WORLD_FIELD.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.tileentity.TileEntityCompat.field_145850_b($1)");
        return out;
    }

    /** <式>.field_145850_b = TileEntity.worldObj。 */
    private static final Pattern TILE_WORLD_FIELD =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.field_145850_b\\b(?!\\s*\\()");

    public static String remapLegacyClasses(String source) {
        String out = source;
        for (String[] pair : FQN_REMAP) {
            out = out.replace(pair[0], pair[1]);
        }
        // Packages.net.minecraft.block.Block.func_xxx → 互換 static (先に FQN を素の形に落とす)
        out = out.replace("Packages.net.minecraft.block.Block.", "Block.");
        // ItemBlock.field_150939_a (内包する Block)。1.21 の BlockItem に SRG フィールドは無いが
        // ランタイムは mojmap なので getBlock がそのまま呼べる。NGTO Builder のマスク機能が使う。
        out = out.replace(".field_150939_a", ".getBlock()");
        // NGTO Builder の hasTileEntity は 1.12 idiom: block.hasTileEntity(block.func_176203_a(meta))。
        // 1.21 の Block にこれらメソッドは無く、毎ブロック TypeError→catch で大量ログになる。
        out = out.replace("block.hasTileEntity(block.func_176203_a(blockSet.metadata))", "false");
        out = out.replace("block.hasTileEntity(blockSet.metadata)", "false");
        for (int i = 0; i < BLOCK_STATIC_PATTERNS.length; i++) {
            out = BLOCK_STATIC_PATTERNS[i].matcher(out).replaceAll(
                    Matcher.quoteReplacement(BLOCK_STATIC_REPLACEMENTS[i]));
        }
        // 残った素の Block 型 (instanceof の右辺など)。静的アクセス形は上で "Block." に落ちているので、
        // ここに来るのは型として使われているものだけ。
        out = BARE_VANILLA_BLOCK.matcher(out)
            .replaceAll(Matcher.quoteReplacement(
                // ★クラス名を直書きしないこと。Fabric の配布版は intermediary 名で動くので
                // 文字列だと解決できない。class リテラルならビルド時に remap される。
                "Packages." + net.minecraft.world.level.block.Block.class.getName()));
        return out;
    }

    public static String resolveIncludes(String source, Set<String> visited) {
        Matcher m = INCLUDE_PATTERN.matcher(source);
        StringBuilder includes = new StringBuilder();
        while (m.find()) {
            String path = m.group(1).trim();
            if (!visited.add(path.toLowerCase(Locale.ROOT))) {
                continue;
            }
            byte[] bytes = NGTFileLoader.findAsset(path);
            if (bytes == null) {
                RealTrainModUnofficial.LOGGER.warn("Script include not found: {}", path);
                continue;
            }
            String text = decode(bytes);
            // //include で読んだ側も .ts なら型を落とす (混在してよい)
            if (TypeScriptTranspiler.isTypeScript(path)) {
                text = TypeScriptTranspiler.toJavaScript(text);
            }
            includes.append(resolveIncludes(text, visited)).append('\n');
        }
        return includes + source;
    }

    public static String decode(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.indexOf('�') >= 0) {
            return new String(bytes, java.nio.charset.Charset.forName("Shift_JIS"));
        }
        return utf8;
    }
}
