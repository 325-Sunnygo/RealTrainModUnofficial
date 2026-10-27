package jp.ngt.mccompat.network;

/**
 * 1.7.10 {@code S21PacketChunkData} のスクリプト互換プレースホルダ。
 * NGTO Builder2 の {@code RTMApiCompat.syncBiomeChunk} が
 * {@code new S21PacketChunkData(chunk, true, 65535)} を生成するが、
 * 1.21 ではこのパケットは存在せず、送信側 (ConnectionCompat) も no-op のため
 * 中身は使われない。
 */
public final class PacketChunkDataCompat {
    public PacketChunkDataCompat(Object chunk, boolean fullChunk, int availableSections) {
        // no-op
    }
}
