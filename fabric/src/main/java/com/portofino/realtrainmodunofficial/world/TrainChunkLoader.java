package com.portofino.realtrainmodunofficial.world;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/**
 * 本家 RTM のチャンクローダー (TrainState State_ChunkLoader) 相当。
 * ON の列車の周囲 3×3 チャンクを NeoForge の TicketController で強制ロードする。
 * 軽量化: チャンクをまたいだ時だけチケットを付け替える。
 */
public final class TrainChunkLoader {

    public static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "train_chunk_loader"),
            (level, ticketHelper) -> {
                //ワールドロード時の残チケット掃除: 所有エンティティが復元されなければ
                //列車側の tick で再登録されるため、一旦すべて破棄して良い
                ticketHelper.getEntityTickets().keySet().forEach(ticketHelper::removeAllTickets);
            });

    /** 半径 (1 = 3×3 チャンク) */
    private static final int RADIUS = 1;

    private TrainChunkLoader() {
    }

    /**
     * 列車の現在位置に合わせてチケットを更新する。
     *
     * @param lastChunk 前回のチャンク (ChunkPos.toLong)。Long.MIN_VALUE = 未登録
     * @return 新しい lastChunk 値 (無効時は Long.MIN_VALUE)
     */
    public static long update(Entity train, boolean enabled, long lastChunk) {
        if (!(train.level() instanceof ServerLevel level)) {
            return lastChunk;
        }
        long current = ChunkPos.asLong(train.blockPosition());
        if (!enabled) {
            if (lastChunk != Long.MIN_VALUE) {
                setTickets(level, train, lastChunk, false);
            }
            return Long.MIN_VALUE;
        }
        if (current == lastChunk) {
            return lastChunk;
        }
        if (lastChunk != Long.MIN_VALUE) {
            setTickets(level, train, lastChunk, false);
        }
        setTickets(level, train, current, true);
        return current;
    }

    /** 列車の消滅時に呼ぶ */
    public static void release(Entity train, long lastChunk) {
        if (lastChunk != Long.MIN_VALUE && train.level() instanceof ServerLevel level) {
            setTickets(level, train, lastChunk, false);
        }
    }

    private static void setTickets(ServerLevel level, Entity train, long chunkLong, boolean add) {
        int cx = ChunkPos.getX(chunkLong);
        int cz = ChunkPos.getZ(chunkLong);
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                CONTROLLER.forceChunk(level, train, cx + dx, cz + dz, add, true);
            }
        }
    }
}
