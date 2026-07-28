package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.block.LargeRailCoreBlock;
import com.portofino.realtrainmodunofficial.blockentity.LargeRailCoreBlockEntity;
import com.portofino.realtrainmodunofficial.blockentity.RailCollisionBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Requests a server-side clone of a placed rail so middle-click works in multiplayer too.
 */
public record CopyRailPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<CopyRailPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "copy_rail")
    );

    public static final StreamCodec<ByteBuf, CopyRailPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        CopyRailPayload::pos,
        CopyRailPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(CopyRailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) {
                return;
            }

            // 中クリックは見えている collision ブロックに当たることが多いので、
            // そこから core を辿って同じ複製結果を返す。
            LargeRailCoreBlockEntity core = null;
            BlockPos corePos = payload.pos();
            if (player.level().getBlockEntity(payload.pos()) instanceof LargeRailCoreBlockEntity directCore) {
                core = directCore;
            } else if (player.level().getBlockEntity(payload.pos()) instanceof RailCollisionBlockEntity collision) {
                corePos = collision.getCorePos();
                if (player.level().getBlockEntity(corePos) instanceof LargeRailCoreBlockEntity collisionCore) {
                    core = collisionCore;
                }
            }

            if (core == null) {
                return;
            }

            ItemStack clone = LargeRailCoreBlock.createRailCloneStack(corePos, core);
            if (clone.isEmpty()) {
                return;
            }

            // サーバー側で選択中スロットを置き換えると、シングル/マルチどちらでも同じ見え方になる。
            player.getInventory().setItem(player.getInventory().selected, clone);
            player.containerMenu.broadcastChanges();
        });
    }
}
