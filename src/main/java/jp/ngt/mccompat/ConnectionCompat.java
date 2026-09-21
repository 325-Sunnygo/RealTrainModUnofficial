package jp.ngt.mccompat;

/**
 * 1.7.10 {@code NetHandlerPlayServer} のスクリプト互換。
 * NGTO Builder2 の {@code RTMApiCompat.syncBiomeChunk} が
 * {@code player.field_71135_a.func_147359_a(packet)} の形で呼ぶ。
 *
 * <p>1.21 ではチャンク再送は通常の同期経路が担うため、ここは no-op。
 * (バイオームの書換え自体が 1.21 の公開 API では不可能なため。ChunkCompat 参照)
 */
public final class ConnectionCompat {
    /** func_147359_a = sendPacket */
    public void func_147359_a(Object packet) {
        // no-op
    }
}
