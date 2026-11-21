package jp.ngt.mccompat;

import net.minecraft.world.level.chunk.LevelChunk;

/** 1.7.10 net.minecraft.world.chunk.Chunk のスクリプト互換。 */
public final class ChunkCompat {
    private final LevelChunk chunk;
    private int[] biomeCache;

    public ChunkCompat(LevelChunk chunk) {
        this.chunk = chunk;
    }

    /** func_76605_m = getBiomeArray。 */
    public int[] func_76605_m() {
        if (this.biomeCache == null) {
            this.biomeCache = this.readBiomes();
        }
        return this.biomeCache;
    }

    /** func_76630_e = markDirty。 */
    public void func_76630_e() {
        this.chunk.setUnsaved(true);
    }

    private int[] readBiomes() {
        int[] arr = new int[256];
        int quartY = Math.max(0, this.chunk.getSections().length * 4 - 1);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                arr[(z << 4) | x] =
                    BiomeGenBase.idOf(this.chunk.getNoiseBiome(x >> 2, quartY, z >> 2));
            }
        }
        return arr;
    }
}
