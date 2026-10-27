package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge;
import com.portofino.realtrainmodunofficial.item.ModelSelectableItem;
import com.portofino.realtrainmodunofficial.item.RailItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * モデル選択画面 → サーバーの確定。
 * カスタム名 (State.Name) と色 (State.Color) も運ぶ。
 */
public record SelectModelPayload(String modelId, String dataMapValue, String customName, int color,boolean autoSplit)
        implements CustomPacketPayload {
    public static final Type<SelectModelPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, "select_model")
    );
    public static final StreamCodec<ByteBuf, SelectModelPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        SelectModelPayload::modelId,
        ByteBufCodecs.STRING_UTF8,
        SelectModelPayload::dataMapValue,
        ByteBufCodecs.STRING_UTF8,
        SelectModelPayload::customName,
        ByteBufCodecs.INT,
        SelectModelPayload::color,
        ByteBufCodecs.BOOL,
        SelectModelPayload::autoSplit,
        SelectModelPayload::new
    );

    public SelectModelPayload(String modelId,String dataMapValue,String customName,int color) {this(modelId,dataMapValue,customName,color,true);}

    public SelectModelPayload(String modelId) {
        this(modelId, "", "", 0xFFFFFF);
    }

    public SelectModelPayload(String modelId, String dataMapValue) {
        this(modelId, dataMapValue, "", 0xFFFFFF);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(SelectModelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            String safeModelId = payload.modelId() == null ? "" : payload.modelId();
            String safeDataMap = payload.dataMapValue() == null ? "" : payload.dataMapValue();
            String safeName = payload.customName() == null ? "" : payload.customName();
            int color = payload.color();

            // 専用サーバー保険: アイテムのコンポーネントが同期・保持されなくても選択が効くよう、
            // サーバー側にプレイヤーごとの選択を控える (設置側が null のとき拾う)。
            com.portofino.realtrainmodunofficial.vehicle.ServerVehicleSelection.set(player.getUUID(), safeModelId);

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof RailItem
                    || stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.TrainItem
                    || stack.getItem() instanceof com.portofino.realtrainmodunofficial.item.CarItem
                    || stack.getItem() instanceof ModelSelectableItem) {
                    LegacyItemStackBridge.setSelectedModelData(stack, safeModelId, safeDataMap, safeName, color);
                    if(stack.getItem() instanceof RailItem) {
                        var property=jp.ngt.rtm.item.ItemRail.getProperty(stack);if(property==null)property=jp.ngt.rtm.item.ItemRail.getDefaultProperty();
                        jp.ngt.rtm.item.ItemRail.writePropToItem(new jp.ngt.rtm.rail.util.RailProperty(safeModelId,property.block,property.blockMetadata,property.blockHeight,payload.autoSplit()),stack);
                    }
                    break;
                }
            }
        });
    }
}
