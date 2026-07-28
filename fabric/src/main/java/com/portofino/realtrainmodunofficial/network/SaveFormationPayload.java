package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.formation.TrainFormation;
import com.portofino.realtrainmodunofficial.formation.TrainFormationData;
import com.portofino.realtrainmodunofficial.item.TrainVehicleItem;
import com.portofino.realtrainmodunofficial.item.VehicleFormationItem;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveFormationPayload(String name, List<String> vehicleIds, List<Boolean> reversedFlags) implements CustomPacketPayload {
   public static final CustomPacketPayload.Type<SaveFormationPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("realtrainmodunofficial", "save_formation"));
   public static final StreamCodec<ByteBuf, SaveFormationPayload> STREAM_CODEC;

   public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handleOnServer(SaveFormationPayload payload, IPayloadContext context) {
      context.enqueueWork(() -> {
         Player player = context.player();
         TrainFormation formation = new TrainFormation();
         formation.setName(payload.name());

         for(int i = 0; i < payload.vehicleIds().size(); ++i) {
            String vehicleId = (String)payload.vehicleIds().get(i);
            if (vehicleId != null && !vehicleId.isBlank()) {
               formation.addVehicle(vehicleId);
            }
         }

         for(InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof VehicleFormationItem || stack.getItem() instanceof TrainVehicleItem) {
               TrainFormationData.setFormation(stack, formation);
               break;
            }
         }

      });
   }

   static {
      STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SaveFormationPayload::name, ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), SaveFormationPayload::vehicleIds, ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.BOOL), SaveFormationPayload::reversedFlags, SaveFormationPayload::new);
   }
}
