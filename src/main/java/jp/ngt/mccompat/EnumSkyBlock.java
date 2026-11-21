package jp.ngt.mccompat;

import net.minecraft.world.level.LightLayer;

/** パックスクリプト互換: 1.7.10/1.12 の net.minecraft.world.Enu… */
public enum EnumSkyBlock {
    Sky(LightLayer.SKY),
    Block(LightLayer.BLOCK);

    public static final EnumSkyBlock SKY = Sky;
    public static final EnumSkyBlock BLOCK = Block;

    public final LightLayer layer;

    EnumSkyBlock(LightLayer layer) {
        this.layer = layer;
    }
}
