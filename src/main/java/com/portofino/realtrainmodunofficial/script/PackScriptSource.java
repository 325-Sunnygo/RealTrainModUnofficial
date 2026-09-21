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
            // GL11/GL12/BufferUtils/OpenGlHelper は描画の根幹だが、素の Java.type にすると
            // クラス初期化失敗でプリリュード全体が中止する。安全束縛して未定義に留める。
            bindOpt("GL11", "jp.ngt.ngtlib.renderer.GL11Facade")
            + "var GL12 = GL11;\n"
            // LWJGL2 の BufferUtils / 1.12 の OpenGlHelper (NGTO Builder 2 が行列バッファと
            // ブレンド指定に使う)。未定義だとそこでスクリプトが止まる。
            + bindOpt("BufferUtils", "jp.ngt.ngtlib.renderer.BufferUtilsCompat")
            + bindOpt("OpenGlHelper", "jp.ngt.ngtlib.renderer.OpenGlHelperCompat")
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
            // ★ここは全て bindOpt (安全束縛)。素の Java.type だと 1 クラスの初期化失敗
            //   (レジストリ未起動・任意 mod 不在など) でプリリュード全体が中止し、
            //   スクリプトが 1 行も走らなくなる。
            bindOpt("MathHelper", "jp.ngt.mccompat.MathHelper")
            // importPackage(net.minecraft.util) 経由の裸 ResourceLocation を互換クラスへ束縛
            // (net.minecraft.util に実クラスを置くとバニラと split package でモジュール解決が落ちる)
            + bindOpt("ResourceLocation", "jp.ngt.mccompat.ResourceLocation")
            // LWJGL2 入力 (SRB3/NGTO Builder)
            + bindOpt("Keyboard", "jp.ngt.mccompat.input.Keyboard")
            + bindOpt("Mouse", "jp.ngt.mccompat.input.Mouse")
            // 1.7.10 net.minecraft.init.Blocks
            + bindOpt("Blocks", "jp.ngt.mccompat.init.Blocks")
            // 1.7.10 ブロッククラス名 → 1.21 実クラス (instanceof 用)
            + bindOpt("BlockStairs", net.minecraft.world.level.block.StairBlock.class)
            + bindOpt("BlockDoor", net.minecraft.world.level.block.DoorBlock.class)
            + bindOpt("BlockFenceGate", net.minecraft.world.level.block.FenceGateBlock.class)
            + bindOpt("BlockLog", net.minecraft.world.level.block.RotatedPillarBlock.class)
            + "var BlockOldLog = BlockLog;\n"
            + "var BlockNewLog = BlockLog;\n"
            + bindOpt("BlockLadder", net.minecraft.world.level.block.LadderBlock.class)
            + bindOpt("BlockButton", net.minecraft.world.level.block.ButtonBlock.class)
            + bindOpt("BlockSlab", net.minecraft.world.level.block.SlabBlock.class)
            + bindOpt("Block", net.minecraft.world.level.block.Block.class)
            + bindOpt("ITileEntityProvider", net.minecraft.world.level.block.EntityBlock.class)
            // 1.7.10 TextureMap (ブロックアトラス)。NGTO Builder のプレビューが field_110575_b を参照
            + bindOpt("TextureMap", "jp.ngt.mccompat.TextureMap")
            + bindOpt("ItemBlock", net.minecraft.world.item.BlockItem.class)
            // 1.7.10 NBT
            + bindOpt("NBTTagCompound", "jp.ngt.mccompat.nbt.NBTTagCompound")
            + bindOpt("NBTTagList", "jp.ngt.mccompat.nbt.NBTTagList")
            // jp.ngt 系の確定バインド — importPackage 経由の遅延解決が実行時に
            // "is not defined" になるケース (SRB3 の RTMItem 等) があるため、
            // スクリプトが未修飾名で使うクラスはここで直接束縛する。
            // (存在しないクラスでエンジンごと死なないよう個別 try)
            + bindOpt("RTMCore", "jp.ngt.rtm.RTMCore") +
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
     * ★ES6 組み込み関数のポリフィル (Object.assign / Array.from 等) は撤去した。
     * 本家 KaizPatchX には存在せず、同梱スクリプトも一つも使っていない。
     * 本家と同じく「Nashorn が標準で持つ範囲」だけを使う (挙動を本家に一致させる)。
     */
    /**
     * 未定義クラスでプリリュード全体を止めないための安全な {@code Java.type} ラッパ。
     * ★素の java.lang.Class を返す汎用バインダ (Class.forName) で代用すると Nashorn の
     * StaticClass 挙動 (静的メソッド呼び出し・new) が失われ、GL11.glPushMatrix() や
     * new Parts(...) が壊れる。必ず {@code Java.type} を通すこと。
     */
    private static final String BIND_HELPER =
        "function __rtmBind(fqn) { try { return Java.type(fqn); } catch (e) { return null; } }\n";

    public static final String PRELUDE = BIND_HELPER + PRELUDE_GL + PRELUDE_NO_GL;

    /**
     * ★バニラのクラスはこちら (class リテラル版) を使うこと。
     * クラス名を文字列で書くと、Fabric の配布版では見つからない。
     */
    private static String bindOpt(String name, Class<?> type) {
        return bindOpt(name, type.getName());
    }

    private static String bindOpt(String name, String fqn) {
        // 失敗したクラス名は __bindFails に集約 (ScriptUtil.doScript がログに出す)。
        return "var " + name + " = __rtmBind('" + fqn + "'); "
                + "if (" + name + " === null) { "
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
            // 1.7.10 net.minecraft.util.Vec3 を本家 Vec3 へ (func_72443_a / createVectorHelper を提供)
            {"Packages.net.minecraft.util.Vec3", "Packages.jp.ngt.ngtlib.math.Vec3"},
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
            // ★EntityPlayer より先に置く (単純 replace のため EntityPlayerMP が
            //   <Player の置換結果>MP に化けるのを防ぐ)。
            //   NGTO Builder2 は player instanceof EntityPlayerMP と player.field_71135_a を使う。
            {"Packages.net.minecraft.entity.player.EntityPlayerMP", "Packages.jp.ngt.mccompat.PlayerCompat"},
            {"Packages.net.minecraft.entity.player.EntityPlayer", "Packages." + net.minecraft.world.entity.player.Player.class.getName()},
            // 1.7.10 バイオーム (NGTO Builder2 Snowfall ブラシ)
            {"Packages.net.minecraft.world.biome.BiomeGenBase", "Packages.jp.ngt.mccompat.BiomeGenBase"},
            // 1.7.10 Forge Loader (NGTO Builder2 の isModLoaded)
            {"Packages.cpw.mods.fml.common.Loader", "Packages.jp.ngt.mccompat.FmlLoader"},
            // 1.7.10 S21PacketChunkData (Snowfall ブラシのチャンク同期。中身は使われない)
            {"Packages.net.minecraft.network.play.server.S21PacketChunkData", "Packages.jp.ngt.mccompat.network.PacketChunkDataCompat"},
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
        // ★widenLegacyWorldHeight (y<0||y>=256 を -64||320 へ置換) は撤去した。
        //   本家に無く、スクリプト本文のリテラルを勝手に書き換えるため挙動が変わる。
        out = remapLegacyClasses(out);
        out = SELF_ASSIGN_DECL.matcher(out).replaceAll("");
        out = remapVanillaOnlyMethods(out);
        out = remapNbtCalls(out);
        return remapFieldAccess(out);
    }

    /**
     * NBT の SRG メソッドを、レシーバを引数に取る静的版へ回す。
     *
     * <p>スクリプトが受け取る NBT はシムとは限らない。
     * {@code jp.ngt.ngtlib.block.BlockSet.nbt} は Java 側の都合で実バニラの CompoundTag で、
     * 実バニラ型には 1.7.10 の SRG 名を生やせない。
     * NGTO Builder2 はこれに対して {@code nbt.func_74737_b()} (copy) を呼ぶので
     * 「is not a function」で落ち、しかもパックが例外を捨てるため無音で失敗していた。
     *
     * <p>レシーバがシムの場合も {@code NBTCompat} が素通しするので、既存の動作は変わらない。
     */
    private static String remapNbtCalls(String src) {
        Matcher m = NBT_CALL.matcher(src);
        StringBuilder out = new StringBuilder(src.length() + 64);
        while (m.find()) {
            String receiver = m.group(1);
            String method = m.group(2);
            String args = m.group(3).trim();
            String replacement;
            if (receiver.endsWith("NBTCompat")) {
                replacement = m.group();
            } else {
                replacement = "Packages.jp.ngt.mccompat.nbt.NBTCompat." + method
                    + "(" + receiver + (args.isEmpty() ? "" : ", " + args) + ")";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * {@code <式>.func_XXXX(引数)} の NBT 版。引数の入れ子は 1 段まで見る
     * ({@code a.func_74776_a("k", f(x))} が拾えるように)。
     */
    private static final Pattern NBT_CALL = Pattern.compile(
        "([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\."
        + "(func_74737_b|func_74760_g|func_74776_a|func_74764_b|func_74775_l|func_74782_a"
        + "|func_74778_a|func_74779_i|func_74768_a|func_74762_e|func_74757_a|func_74767_n"
        + "|func_74780_a|func_74769_h|func_150295_c)"
        + "\\(((?:[^()]|\\([^()]*\\))*)\\)");


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
        out = BLOCK_CAN_PLACE_AT.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.block.Block.func_149742_c($1, $2)");
        out = BLOCK_GET_ICON.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.client.BlockClientCompat.func_149691_a($1, $2)");
        out = BLOCK_IS_LEAVES.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.block.Block.isLeaves($1, $2)");
        return out;
    }

    /** <式>.isLeaves(world,x,y,z) = Block.isLeaves (1.7.10 MCP 名。1.21 に無い)。 */
    private static final Pattern BLOCK_IS_LEAVES =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.isLeaves\\(((?:[^()]|\\([^()]*\\))*)\\)");

    /** <式>.func_149742_c(world,x,y,z) = Block.canPlaceBlockAt (レシーバは実バニラ Block)。 */
    private static final Pattern BLOCK_CAN_PLACE_AT =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_149742_c\\(((?:[^()]|\\([^()]*\\))*)\\)");

    /** <式>.func_149691_a(side, meta) = Block.getIcon (クライアント専用)。 */
    private static final Pattern BLOCK_GET_ICON =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.func_149691_a\\(((?:[^()]|\\([^()]*\\))*)\\)");

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
        // tileEntity.field_145851_c/d/e = xCoord/yCoord/zCoord。同じく静的ヘルパーへ回す。
        out = TILE_X_FIELD.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.tileentity.TileEntityCompat.field_145851_c($1)");
        out = TILE_Y_FIELD.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.tileentity.TileEntityCompat.field_145848_d($1)");
        out = TILE_Z_FIELD.matcher(out)
            .replaceAll("Packages.jp.ngt.mccompat.tileentity.TileEntityCompat.field_145849_e($1)");
        return out;
    }

    // ★代入の左辺 (X.field_... = ...) は書き換えない。書き換えると
    //   `method(args) = x` となり Nashorn が "Invalid left hand side for assignment" で落ちる
    //   (NGTO Builder2 が tileEntity.field_145851_c = x として代入する)。
    private static final Pattern TILE_X_FIELD =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.field_145851_c\\b(?!\\s*\\()(?!\\s*=(?!=))");
    private static final Pattern TILE_Y_FIELD =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.field_145848_d\\b(?!\\s*\\()(?!\\s*=(?!=))");
    private static final Pattern TILE_Z_FIELD =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.field_145849_e\\b(?!\\s*\\()(?!\\s*=(?!=))");

    /** <式>.field_145850_b = TileEntity.worldObj。 */
    private static final Pattern TILE_WORLD_FIELD =
        Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\.field_145850_b\\b(?!\\s*\\()(?!\\s*=(?!=))");

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

    /**
     * 1.7.10 時代のパックが持つ「Y は 0〜255」という前提を 1.21 の範囲へ広げる。
     *
     * <p>1.21 のワールドは Y=-64 から始まるので、地下 (Y<0) に建てると
     * {@code if (y < 0 || y >= 256) return;} のような番兵に引っかかって
     * <b>何も置かれずに黙って終わる</b>。
     * 実測: NGTO Builder2 で Y=-47 に架線を張ると
     * "Skip TileEntity NBT: invalid y=-47" だけが出てビームが出なかった。
     *
     * <p>置き換えるのは<b>この形の範囲チェックだけ</b>。他の比較には触らない。
     */
}
