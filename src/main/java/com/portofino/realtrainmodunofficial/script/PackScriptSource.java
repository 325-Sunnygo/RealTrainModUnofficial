package com.portofino.realtrainmodunofficial.script;

import jp.ngt.ngtlib.io.NGTFileLoader;

import java.util.HashSet;
import java.util.Set;

/** パックスクリプトの共通前処理 (クライアント/サーバー両用)。 */
public final class PackScriptSource {

    /** Nashorn 実行前に評価される共通プリリュード。 */
    /** GL 束縛だけを分離したもの。 モデル添付の描画スクリプト経路 (TrainScriptSyste… */
    public static final String PRELUDE_GL =
            // GL11/GL12/BufferUtils/OpenGlHelper は描画の根幹だが、素の Ja…
            bindOpt("GL11", "jp.ngt.ngtlib.renderer.GL11Facade")
            + "var GL12 = GL11;\n"
            + bindOpt("GL13", "jp.ngt.ngtlib.renderer.GL13Facade")
            + bindOpt("GL14", "jp.ngt.ngtlib.renderer.GL14Facade")
            // LWJGL2 の BufferUtils / 1.12 の OpenGlHelper (NGTO…
            + bindOpt("BufferUtils", "jp.ngt.ngtlib.renderer.BufferUtilsCompat")
            + bindOpt("OpenGlHelper", "jp.ngt.ngtlib.renderer.OpenGlHelperCompat")
            // Parts も描画機構に依存する。実 jp.ngt.rtm.render.Parts は GLRe…
            + bindOpt("Parts", "jp.ngt.rtm.render.Parts")
            + bindOpt("ActionParts", "jp.ngt.rtm.render.ActionParts");

    public static final String PRELUDE_NO_GL =
            // rtm-ts の Multi-target 構成対策。
            "var RTMX_COMPAT_TARGETS = (typeof RTMX_COMPAT_TARGETS !== 'undefined' && RTMX_COMPAT_TARGETS) ? RTMX_COMPAT_TARGETS : {};\n" +
            // ★ここは全て bindOpt (安全束縛)。
            bindOpt("MathHelper", "jp.ngt.mccompat.MathHelper")
            // importPackage(net.minecraft.util) 経由の裸 ResourceLo…
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
            // 1.7.10 TextureMap (ブロックアトラス)。
            + bindOpt("TextureMap", "jp.ngt.mccompat.TextureMap")
            + bindOpt("ItemBlock", net.minecraft.world.item.BlockItem.class)
            // 1.7.10 NBT
            + bindOpt("NBTTagCompound", "jp.ngt.mccompat.nbt.NBTTagCompound")
            + bindOpt("NBTTagList", "jp.ngt.mccompat.nbt.NBTTagList")
            // jp.ngt 系の確定バインド — importPackage 経由の遅延解決が実行時に "is…
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
            // SL パックが蒸気/煙で使う (importPackage は no-op なので未修飾名を直接束…
            bindOpt("EnumParticleTypes", "jp.ngt.mccompat.EnumParticleTypes") +
            bindOpt("PerlinNoise", "jp.ngt.ngtlib.math.PerlinNoise") +
            bindOpt("ColorUtil", "jp.ngt.ngtlib.util.ColorUtil") +
            bindOpt("__rtmuScaffold", "jp.ngt.rtm.block.tileentity.TileEntityScaffold") +
            "var TileEntityScaffold = __rtmuScaffold === null ? null : __rtmuScaffold.TYPE;\n" +
            bindOpt("NGTMath", "jp.ngt.ngtlib.math.NGTMath") +
            bindOpt("Vec3", "jp.ngt.ngtlib.math.Vec3") +
            // 本家 RenderMotor / RenderClutch / RenderReversGear…
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
            // 1.12 Forge の mod 存在チェック (rtm-ts の mc1122 ターゲットが c…
            bindOpt("Loader", "net.minecraftforge.fml.common.Loader") +
            // importPackage(net.minecraft.client) だけで参照される Mine…
            bindOpt("Minecraft", "jp.ngt.mccompat.Minecraft");

    /** GL 束縛込みの完全版 (描画を GLRecorder に記録する通常経路用)。 */
    /** ★ES6 組み込み関数のポリフィル (Object.assign / Array.from 等)… */
    /** 未定義クラスでプリリュード全体を止めないための安全な Java.type ラッパ。 */
    private static final String BIND_HELPER =
        "function __rtmBind(fqn) { try { return Java.type(fqn); } catch (e) { return null; } }\n";

    /** KaizPatchX のスクリプトは 1.7.10 の Packages.xxx を直接参照する。 */
    private static final String PACKAGE_SHIM = """
var __rtmClassMap = {
  'org.lwjgl.opengl.GL11': 'jp.ngt.ngtlib.renderer.GL11Facade',
  'org.lwjgl.opengl.GL12': 'jp.ngt.ngtlib.renderer.GL11Facade',
  'org.lwjgl.opengl.GL13': 'jp.ngt.ngtlib.renderer.GL13Facade',
  'org.lwjgl.opengl.GL14': 'jp.ngt.ngtlib.renderer.GL14Facade',
  'org.lwjgl.BufferUtils': 'jp.ngt.ngtlib.renderer.BufferUtilsCompat',
  'org.lwjgl.input.Keyboard': 'jp.ngt.mccompat.input.Keyboard',
  'org.lwjgl.input.Mouse': 'jp.ngt.mccompat.input.Mouse',
  'net.minecraft.util.ResourceLocation': 'jp.ngt.mccompat.ResourceLocation',
  'net.minecraft.util.MathHelper': 'jp.ngt.mccompat.MathHelper',
  'net.minecraft.util.math.MathHelper': 'jp.ngt.mccompat.MathHelper',
  'net.minecraft.util.Vec3': 'jp.ngt.ngtlib.math.Vec3',
  'net.minecraft.util.ChunkCoordinates': 'jp.ngt.mccompat.ChunkCoordinates',
  'net.minecraft.util.AxisAlignedBB': 'jp.ngt.mccompat.AxisAlignedBB',
  'net.minecraft.util.EnumParticleTypes': 'jp.ngt.mccompat.EnumParticleTypes',
  'net.minecraft.world.EnumSkyBlock': 'jp.ngt.mccompat.EnumSkyBlock',
  'net.minecraft.world.World': 'net.minecraft.world.level.Level',
  'net.minecraft.world.biome.BiomeGenBase': 'jp.ngt.mccompat.BiomeGenBase',
  'net.minecraft.world.gen.structure.MapGenStructureData': 'jp.ngt.mccompat.world.MapGenStructureData',
  'net.minecraft.entity.Entity': 'net.minecraft.world.entity.Entity',
  'net.minecraft.entity.EntityLivingBase': 'net.minecraft.world.entity.LivingEntity',
  'net.minecraft.entity.player.EntityPlayer': 'net.minecraft.world.entity.player.Player',
  'net.minecraft.entity.player.EntityPlayerMP': 'jp.ngt.mccompat.PlayerCompat',
  'net.minecraft.tileentity.TileEntity': 'net.minecraft.world.level.block.entity.BlockEntity',
  'net.minecraft.nbt.NBTTagCompound': 'jp.ngt.mccompat.nbt.NBTTagCompound',
  'net.minecraft.nbt.NBTTagList': 'jp.ngt.mccompat.nbt.NBTTagList',
  'net.minecraft.math.BlockPos': 'net.minecraft.core.BlockPos',
  'net.minecraft.util.math.BlockPos': 'net.minecraft.core.BlockPos',
  'net.minecraft.client.Minecraft': 'jp.ngt.mccompat.Minecraft',
  'net.minecraft.client.gui.Gui': 'jp.ngt.mccompat.Gui',
  'net.minecraft.client.renderer.texture.TextureMap': 'jp.ngt.mccompat.TextureMap',
  'net.minecraft.client.renderer.texture.TextureUtil': 'jp.ngt.mccompat.TextureUtil',
  'net.minecraft.client.renderer.texture.DynamicTexture': 'jp.ngt.mccompat.DynamicTexture',
  'net.minecraft.client.renderer.OpenGlHelper': 'jp.ngt.ngtlib.renderer.OpenGlHelperCompat',
  'net.minecraftforge.client.model.ModelFormatException': 'jp.ngt.ngtlib.renderer.model.ModelFormatException',
  'net.minecraft.network.play.server.S21PacketChunkData': 'jp.ngt.mccompat.network.PacketChunkDataCompat',
  'net.minecraft.init.Blocks': 'jp.ngt.mccompat.init.Blocks',
  'net.minecraft.block.Block': 'net.minecraft.world.level.block.Block',
  'net.minecraft.block.BlockStairs': 'net.minecraft.world.level.block.StairBlock',
  'net.minecraft.block.BlockDoor': 'net.minecraft.world.level.block.DoorBlock',
  'net.minecraft.block.BlockFenceGate': 'net.minecraft.world.level.block.FenceGateBlock',
  'net.minecraft.block.BlockLog': 'net.minecraft.world.level.block.RotatedPillarBlock',
  'net.minecraft.block.BlockOldLog': 'net.minecraft.world.level.block.RotatedPillarBlock',
  'net.minecraft.block.BlockNewLog': 'net.minecraft.world.level.block.RotatedPillarBlock',
  'net.minecraft.block.BlockLadder': 'net.minecraft.world.level.block.LadderBlock',
  'net.minecraft.block.BlockButton': 'net.minecraft.world.level.block.ButtonBlock',
  'net.minecraft.block.BlockSlab': 'net.minecraft.world.level.block.SlabBlock',
  'net.minecraft.block.ITileEntityProvider': 'net.minecraft.world.level.block.EntityBlock',
  'cpw.mods.fml.common.Loader': 'jp.ngt.mccompat.FmlLoader'
};
// Java.type('org.lwjgl.opengl.GL11') のような直接参照も互換クラスへ回す。
// (KaizPatchX は 1.7.10 の実クラスをそのまま使えるが、1.21 では差し替えが必要)
var __rtmRealJavaType = Java.type;
Java.type = function(fqn) {
  var mapped = __rtmClassMap[fqn];
  return __rtmRealJavaType(mapped || fqn);
};
function __rtmResolveClass(fqn) {
  var mapped = __rtmClassMap[fqn];
  if (mapped) {
    try { return Java.type(mapped); } catch (e) { return null; }
  }
  try { return Java.type(fqn); } catch (e) { return null; }
}
function __rtmPackageNode(prefix) {
  var node = {};
  node.toString = function() { return '[JavaPackage ' + (prefix || '') + ']'; };
  node.__noSuchProperty__ = function(name) {
    var fqn = prefix ? (prefix + '.' + name) : name;
    var cls = __rtmResolveClass(fqn);
    if (cls !== null) { return cls; }
    return __rtmPackageNode(fqn);
  };
  return node;
}
Packages = __rtmPackageNode('');
""";

    public static final String PRELUDE = BIND_HELPER + PACKAGE_SHIM + PRELUDE_GL + PRELUDE_NO_GL;
    /** Server scripts do not initialize optional client… */
    public static final String SERVER_PRELUDE = PRELUDE.replaceAll(
        "(?m)^var (NGTUtilClient|MCWrapperClient|Minecraft|SoundUpdaterTrain|PartsRenderer|VehiclePartsRenderer|RailPartsRenderer|MachinePartsRenderer|SignalPartsRenderer|WirePartsRenderer|OrnamentPartsRenderer|TileEntityPartsRenderer|EntityPartsRenderer|NGTObjectRenderer|NGTRenderer|NGTRenderHelper) = __rtmBind[^\\n]*\\n", "var $1 = null;\n");

    /** ★バニラのクラスはこちら (class リテラル版) を使うこと。 */
    private static String bindOpt(String name, Class<?> type) {
        return bindOpt(name, type.getName());
    }

    private static String bindOpt(String name, String fqn) {
        // 失敗したクラス名は __bindFails に集約 (ScriptUtil.doScript がロ…
        return "var " + name + " = __rtmBind('" + fqn + "'); "
                + "if (" + name + " === null) { "
                + "if (typeof __bindFails === 'undefined') { __bindFails = ''; } "
                + "__bindFails += '" + name + " '; }\n";
    }

    private PackScriptSource() {
    }

    /** include を展開したソースを返す (プリリュードは含まない)。 */
    public static String prepare(String source) {
        return prepare(source, null);
    }

    /** 本家 ModelPackManager.loadScript と同じく //include を展開… */
    public static String prepare(String source, String path) {
        return prepare(source, path, NGTFileLoader::findAsset);
    }

    /** Allows development tools to resolve includes with… */
    public static String prepare(String source, String path, java.util.function.Function<String, byte[]> loader) {
        // KaizPatchX と同じ: スクリプト本文は書き換えない。
        return resolveIncludes(source, new HashSet<>(), loader);
    }

    public static String resolveIncludes(String source, Set<String> visited) {
        return resolveIncludes(source, visited, NGTFileLoader::findAsset);
    }

    public static String resolveIncludes(String source, Set<String> visited,
                                         java.util.function.Function<String, byte[]> loader) {
        return LegacyScriptIncludes.expand(source, visited, path -> {
            byte[] bytes = loader.apply(path);
            if (bytes == null) {
                return null;
            }
            return decode(bytes);
        });
    }

    public static String decode(byte[] bytes) {
        String text = jp.ngt.ngtlib.io.NGTText.decodeText(bytes);
        return text.startsWith("\ufeff") ? text.substring(1) : text;
    }

}
