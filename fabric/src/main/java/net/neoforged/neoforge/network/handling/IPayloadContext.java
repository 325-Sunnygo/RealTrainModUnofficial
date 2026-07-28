package net.neoforged.neoforge.network.handling;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** シム: Fabric Networking のコンテキストを NeoForge 形に写像する。 */
public interface IPayloadContext {
    /** 受信側のプレイヤー (S2C ならクライアントプレイヤー、C2S なら送信者)。 */
    Player player();

    /** メインスレッドで実行する (Fabric 側で既にメインスレッドなら即時)。 */
    void enqueueWork(Runnable work);

    /** 逆方向へ応答を送る。 */
    void reply(CustomPacketPayload payload);
}
