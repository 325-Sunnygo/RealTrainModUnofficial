package jp.ngt.rtm.rail.util;

import com.portofino.realtrainmodunofficial.rail.RailDefinition;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 本家 jp.ngt.rtm.rail.util.RailProperty の忠実移植。
 *
 * 1.21 移植差分:
 * - blockMetadata は 1.21 に存在しないが、本家 NBT 互換のためフィールド/キーを保持する。
 * - getModelSet() は Phase 4 (ModelPackManager) で本家型に置換予定。
 *   それまでは暫定的に既存の RailRegistry (RailDefinition) から ballastWidth を解決する。
 */
public final class RailProperty {
    public final String railModel;
    public final Block block;
    public final int blockMetadata;
    public final float blockHeight;
    public final String unlocalizedName;

    /**
     * 本家では ItemRail.getDefaultProperty()。TODO(Phase 4): ItemRail 移植後に移動。
     */
    public static RailProperty getDefaultProperty() {
        return new RailProperty("", Blocks.GRAVEL, 0, 0.0625F);
    }

    public RailProperty(String par1, Block par2, int par3, float par4) {
        this.railModel = par1;
        this.block = par2;
        this.blockMetadata = par3;
        this.blockHeight = (par4 <= 0.0F) ? 0.0625F : par4;
        this.unlocalizedName = par2.getDescriptionId();
    }

    public static RailProperty readFromNBT(CompoundTag nbt) {
        String s0 = nbt.getString("RailModel");
        String s1 = nbt.getString("BlockName");
        Block block = Blocks.AIR;
        ResourceLocation loc = ResourceLocation.tryParse(s1);
        if (loc != null && BuiltInRegistries.BLOCK.containsKey(loc)) {
            block = BuiltInRegistries.BLOCK.get(loc);
        }
        int i0 = nbt.getInt("BlockMetadata");
        float b0 = nbt.getFloat("BlockHeight");
        return new RailProperty(s0, block, i0, b0);
    }

    public void writeToNBT(CompoundTag nbt) {
        nbt.putString("RailModel", this.railModel);
        String s1 = BuiltInRegistries.BLOCK.getKey(this.block).toString();
        nbt.putString("BlockName", s1);
        nbt.putInt("BlockMetadata", this.blockMetadata);
        nbt.putFloat("BlockHeight", this.blockHeight);
    }

    /**
     * 道床幅。本家では getModelSet().getConfig().ballastWidth。
     * TODO(Phase 4): ModelPackManager/ModelSetRail 移植後に本家 API へ置換。
     */
    public int getBallastWidth() {
        RailDefinition def = RailRegistry.getById(this.railModel);
        int width = def != null ? def.getBallastWidth() : 3;
        if (width < 1) {
            width = 3;
        }
        // 本家 RailConfig は奇数を強制する
        if (width % 2 == 0) {
            width += 1;
        }
        return width;
    }
}
