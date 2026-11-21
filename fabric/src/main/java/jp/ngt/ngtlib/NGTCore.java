package jp.ngt.ngtlib;
import jp.ngt.ngtlib.network.PacketNBT;
/** Server-owned legacy NBT broadcasts using the ordered vanilla block/tile protocol. */
public final class NGTCore {
    public static final NetworkWrapper NETWORK_WRAPPER=new NetworkWrapper();
    private NGTCore() {}
    public static final class NetworkWrapper {
        public void sendToAll(PacketNBT packet) {
            if(!packet.toClient || !(packet.tile.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;
            if(!level.getServer().isSameThread())throw new IllegalStateException("Tile synchronization must run on the server thread");
            if(level.getBlockEntity(packet.tile.getBlockPos())!=packet.tile)return;
            packet.tile.setChanged();
            var block=new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level,packet.tile.getBlockPos());
            var data=net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(packet.tile,(tile,registries)->packet.data.copy());
            for(var player:level.players()) {player.connection.send(block);player.connection.send(data);}
            var state=level.getBlockState(packet.tile.getBlockPos());level.sendBlockUpdated(packet.tile.getBlockPos(),state,state,3);
        }
    }
}
