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
 * VehicleScriptRenderers (クライアント描画) と CarServerScripts (サーバー) が共用する。
 */
public final class PackScriptSource {

    /**
     * Nashorn 実行前に評価される共通プリリュード。
     * importPackage は未定義名しか束縛しないため、ここで束縛した var が常に勝つ。
     */
    public static final String PRELUDE =
            "var GL11 = Java.type('jp.ngt.ngtlib.renderer.GL11Facade');\n" +
            "var GL12 = GL11;\n" +
            "var MathHelper = Java.type('jp.ngt.mccompat.MathHelper');\n" +
            //LWJGL2 入力 (SRB3/NGTO Builder)
            "var Keyboard = Java.type('jp.ngt.mccompat.input.Keyboard');\n" +
            "var Mouse = Java.type('jp.ngt.mccompat.input.Mouse');\n" +
            //1.7.10 net.minecraft.init.Blocks
            "var Blocks = Java.type('jp.ngt.mccompat.init.Blocks');\n" +
            //1.7.10 ブロッククラス名 → 1.21 実クラス (instanceof 用)
            "var BlockStairs = Java.type('net.minecraft.world.level.block.StairBlock');\n" +
            "var BlockDoor = Java.type('net.minecraft.world.level.block.DoorBlock');\n" +
            "var BlockFenceGate = Java.type('net.minecraft.world.level.block.FenceGateBlock');\n" +
            "var BlockLog = Java.type('net.minecraft.world.level.block.RotatedPillarBlock');\n" +
            "var BlockOldLog = BlockLog;\n" +
            "var BlockNewLog = BlockLog;\n" +
            "var BlockLadder = Java.type('net.minecraft.world.level.block.LadderBlock');\n" +
            "var BlockButton = Java.type('net.minecraft.world.level.block.ButtonBlock');\n" +
            "var BlockSlab = Java.type('net.minecraft.world.level.block.SlabBlock');\n" +
            "var Block = Java.type('net.minecraft.world.level.block.Block');\n" +
            "var ITileEntityProvider = Java.type('net.minecraft.world.level.block.EntityBlock');\n" +
            //1.7.10 TextureMap (ブロックアトラス)。NGTO Builder のプレビューが field_110575_b を参照
            "var TextureMap = Java.type('jp.ngt.mccompat.TextureMap');\n" +
            "var ItemBlock = Java.type('net.minecraft.world.item.BlockItem');\n" +
            //1.7.10 NBT
            "var NBTTagCompound = Java.type('jp.ngt.mccompat.nbt.NBTTagCompound');\n" +
            "var NBTTagList = Java.type('jp.ngt.mccompat.nbt.NBTTagList');\n" +
            //jp.ngt 系の確定バインド — importPackage 経由の遅延解決が実行時に
            //"is not defined" になるケース (SRB3 の RTMItem 等) があるため、
            //スクリプトが未修飾名で使うクラスはここで直接束縛する。
            //(存在しないクラスでエンジンごと死なないよう個別 try)
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
            //SL パックが蒸気/煙で使う (importPackage は no-op なので未修飾名を直接束縛)
            bindOpt("EnumParticleTypes", "jp.ngt.mccompat.EnumParticleTypes") +
            bindOpt("NGTMath", "jp.ngt.ngtlib.math.NGTMath") +
            bindOpt("Vec3", "jp.ngt.ngtlib.math.Vec3") +
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
            //車両/レール描画スクリプトが直接 new する描画クラス
            bindOpt("Parts", "jp.ngt.rtm.render.Parts") +
            bindOpt("ModelObject", "jp.ngt.rtm.render.ModelObject");

    private static String bindOpt(String name, String fqn) {
        //失敗したクラス名は __bindFails に集約 (ScriptUtil.doScript がログに出す)
        return "try { var " + name + " = Java.type('" + fqn + "'); } catch (__e) { "
                + "if (typeof __bindFails === 'undefined') { __bindFails = ''; } "
                + "__bindFails += '" + name + " '; }\n";
    }

    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*//include\\s*<([^>]+)>", Pattern.MULTILINE);

    private static final String[][] FQN_REMAP = {
            {"Packages.net.minecraft.util.ResourceLocation", "Packages.jp.ngt.mccompat.ResourceLocation"},
            {"Packages.net.minecraft.client.renderer.texture.TextureUtil", "Packages.jp.ngt.mccompat.TextureUtil"},
            {"Packages.net.minecraft.client.renderer.texture.DynamicTexture", "Packages.jp.ngt.mccompat.DynamicTexture"},
            {"Packages.net.minecraft.client.Minecraft", "Packages.jp.ngt.mccompat.Minecraft"},
            {"Packages.net.minecraft.util.math.BlockPos", "Packages.net.minecraft.core.BlockPos"},
            // NGTO Builder が hasTileEntity() で使う: 1.7.10 ITileEntityProvider = 1.21 EntityBlock
            // (BE を持つブロックのマーカーインタフェース)。未対応だと設置経路で instanceof が落ちる。
            {"Packages.net.minecraft.block.ITileEntityProvider", "Packages.net.minecraft.world.level.block.EntityBlock"},
            {"Packages.net.minecraft.world.EnumSkyBlock", "Packages.jp.ngt.mccompat.EnumSkyBlock"},
            {"Packages.net.minecraft.util.MathHelper", "Packages.jp.ngt.mccompat.MathHelper"},
            {"Packages.net.minecraft.util.math.MathHelper", "Packages.jp.ngt.mccompat.MathHelper"},
            {"Packages.net.minecraft.util.EnumParticleTypes", "Packages.jp.ngt.mccompat.EnumParticleTypes"},
    };

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

    /**
     * include 解決 + 互換リマップ済みのソースを返す (プリリュードは含まない)。
     */
    /**
     * {@code .seatRotation} を {@code .getSeatRotationRaw()} に置き換えるためのパターン。
     * {@code .getSeatRotation()} には (直前が "get" なので) マッチしない。
     */
    private static final Pattern SEAT_ROTATION_FIELD = Pattern.compile("\\.seatRotation\\b(?!\\s*\\()");

    public static String prepare(String source) {
        String out = resolveIncludes(source, new HashSet<>());
        out = remapLegacyClasses(out);
        return remapFieldAccess(out);
    }

    /**
     * Nashorn の「フィールドより getter を優先する」仕様を回避するための書き換え。
     * <p>
     * 本家 EntityVehicleBase は {@code public int seatRotation} と
     * {@code float getSeatRotation()} (= seatRotation / 45) の<b>両方</b>を持つ。
     * 実際のパック (小田急 30000 形 / E259 / E257-500 等) は生の値が要るので
     * {@code entity.seatRotation / 45} と書き、RTM 標準の Render223.js は
     * {@code entity.getSeatRotation() * 15} と書く。
     * <p>
     * ところが Nashorn (Dynalink) はプロパティ解決で getter を優先するため、
     * {@code getSeatRotation()} を定義した瞬間に {@code entity.seatRotation} まで
     * getter を返すようになり、パック側で 45 が二重に効いて座席が動かなくなる。
     * <p>
     * そこでスクリプト中の {@code .seatRotation} だけを {@code .getSeatRotationRaw()} に
     * 書き換える。これで「フィールド参照は生の値」「getSeatRotation() は本家どおり」の
     * 両方が成立する。
     */
    public static String remapFieldAccess(String source) {
        return SEAT_ROTATION_FIELD.matcher(source).replaceAll(".getSeatRotationRaw()");
    }

    public static String remapLegacyClasses(String source) {
        String out = source;
        for (String[] pair : FQN_REMAP) {
            out = out.replace(pair[0], pair[1]);
        }
        //Packages.net.minecraft.block.Block.func_xxx → 互換 static (先に FQN を素の形に落とす)
        out = out.replace("Packages.net.minecraft.block.Block.", "Block.");
        //ItemBlock.field_150939_a (内包する Block)。1.21 の BlockItem に SRG フィールドは無いが
        //ランタイムは mojmap なので getBlock() がそのまま呼べる。NGTO Builder のマスク機能が使う。
        out = out.replace(".field_150939_a", ".getBlock()");
        //NGTO Builder の hasTileEntity() は 1.12 idiom: block.hasTileEntity(block.func_176203_a(meta))。
        //1.21 の Block にこれらメソッドは無く、毎ブロック TypeError→catch で大量ログになる。1.21 では
        //直前の instanceof EntityBlock (ITileEntityProvider) が BE 判定の正解なので、フォールバックは false に落とす。
        out = out.replace("block.hasTileEntity(block.func_176203_a(blockSet.metadata))", "false");
        out = out.replace("block.hasTileEntity(blockSet.metadata)", "false");
        for (int i = 0; i < BLOCK_STATIC_PATTERNS.length; i++) {
            out = BLOCK_STATIC_PATTERNS[i].matcher(out).replaceAll(
                    Matcher.quoteReplacement(BLOCK_STATIC_REPLACEMENTS[i]));
        }
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
