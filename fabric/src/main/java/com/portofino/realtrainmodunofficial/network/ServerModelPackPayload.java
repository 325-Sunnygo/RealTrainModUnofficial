package com.portofino.realtrainmodunofficial.network;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.modelpack.transfer.ServerPackStorage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.ByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ServerModelPackPayload(byte action,String session,String name,long size,long offset,byte[] data) implements CustomPacketPayload {
    public static final byte LIST=0,MANIFEST=1,GET=2,CHUNK=3,FAILED=4;
    public ServerModelPackPayload {
        if(action<0||action>FAILED||session.length()>64||name.length()>200||data.length>65536||size<0||offset<0)throw new IllegalArgumentException("Invalid model pack payload");
    }
    public static final Type<ServerModelPackPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID,"server_model_pack"));
    public static final StreamCodec<ByteBuf,ServerModelPackPayload> STREAM_CODEC=StreamCodec.of((buffer,message)->{
        var bytes=new FriendlyByteBuf(buffer);bytes.writeByte(message.action);bytes.writeUtf(message.session,64);bytes.writeUtf(message.name,200);
        bytes.writeLong(message.size);bytes.writeLong(message.offset);bytes.writeByteArray(message.data);
    },buffer->{var bytes=new FriendlyByteBuf(buffer);return new ServerModelPackPayload(bytes.readByte(),bytes.readUtf(64),bytes.readUtf(200),bytes.readLong(),bytes.readLong(),bytes.readByteArray(65536));});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(ServerModelPackPayload message,IPayloadContext context) {
        context.enqueueWork(()->{
            if(context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                if(message.action==LIST||message.action==GET)com.portofino.realtrainmodunofficial.modelpack.transfer.ServerPackSender.handle(message,player);
            } else if(message.action==MANIFEST||message.action==CHUNK||message.action==FAILED) {
                com.portofino.realtrainmodunofficial.client.ServerPackClient.handle(message);
            }
        });
    }
}
