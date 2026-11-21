package jp.ngt.mccompat;

import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 1.7.10 {@code net.minecraft.world.chunk.Chunk} のスクリプト互換。
 * NGTO Builder2 の Snowfall ブラシが
 * {@code chunk.func_76605_m()} (= getBiomeArray, 16x16 byte 配列) と
 * {@code chunk.func_76630_e()} (= markDirty) を呼ぶ。
 *
 * <p><b>1.21 の制約</b>: チャンクのバイオームは {@code PalettedContainerRO} で
 * read-only になり、公開 API に setter が無い (LevelChunkSection#getBiomes は RO)。
 * そのため 1.7.10 の「バイオーム配列を書き換える」操作は再現できない。
 * ここでは読取は実値、書換えは no-op (markDirty のみ) として例外を出さない。
 */
public final class ChunkCompat {
    private final LevelChunk chunk;
    private int[] biomeCache;

    public ChunkCompat(LevelChunk chunk) {
        this.chunk = chunk;
    }

    /** func_76605_m = getBiomeArray。index = (z & 15) << 4 | (x & 15)。 */
    public int[] func_76605_m() {
        if (this.biomeCache == null) {
            this.biomeCache = this.readBiomes();
        }
        return this.biomeCache;
    }

    /** func_76630_e = markDirty。1.21 ではバイオームを書き戻せないので dirty のみ立てる。 */
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
