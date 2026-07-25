package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.client.screen.ScriptBlockScreen;
import com.portofino.realtrainmodunofficial.client.screen.SignalChangerScreen;
import com.portofino.realtrainmodunofficial.client.screen.SignalReceiverScreen;
import com.portofino.realtrainmodunofficial.client.screen.SignalValueScreen;
import com.portofino.realtrainmodunofficial.client.screen.TrainDetectorScreen;
import com.portofino.realtrainmodunofficial.client.sound.CrossingGateSoundManager;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.item.TrainItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientHooksClient {
    private ClientHooksClient() {
    }

    public static void openRailSelectScreen(Player player, ItemStack stack) {
        ClientItemHelper.openRailSelectScreen(player, stack);
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack, TrainItem.Category category) {
        ClientItemHelper.openTrainSelectScreen(player, stack, category);
    }

    /** 設置済み列車のモデル差し替え (本家 guiIdSelectEntityModel)。 */
    public static void openEntityModelSelectScreen(Object vehicle) {
        ClientItemHelper.openEntityModelSelectScreen(vehicle);
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack) {
        ClientItemHelper.openTrainSelectScreen(player, stack);
    }

    public static void openVehicleFormationScreen(ItemStack stack) {
        ClientItemHelper.openVehicleFormationScreen(stack);
    }

    public static void openCarSelectScreen(Player player, ItemStack stack) {
        ClientItemHelper.openCarSelectScreen(player, stack);
    }

    public static void openInstalledObjectSelectScreen(Player player, ItemStack stack, InstalledObjectCategory category) {
        ClientItemHelper.openInstalledObjectSelectScreen(player, stack, category);
    }

    /** SignalControllerMod (masa300) 移植: 設定 GUI */
    public static void openSignalControllerScreen(Object controller) {
        if (controller instanceof jp.masa.signalcontrollermod.TileEntitySignalController te) {
            Minecraft.getInstance().setScreen(
                new com.portofino.realtrainmodunofficial.client.screen.SignalControllerScreen(te));
        }
    }

    /** 本家 GuiChangeOffset: バールで設置物を右クリック → 微調整 GUI */
    public static void openChangeOffsetScreen(Object blockEntity) {
        if (blockEntity instanceof InstalledObjectBlockEntity be) {
            Minecraft.getInstance().setScreen(
                new com.portofino.realtrainmodunofficial.client.screen.ChangeOffsetScreen(be));
        }
    }

    /** レールのカント設定: レンチでマーカーをシフト右クリック */
    public static void openMarkerOffsetScreen(Object marker) {
        if (marker instanceof jp.ngt.rtm.rail.TileEntityMarker te) {
            Minecraft.getInstance().setScreen(
                new com.portofino.realtrainmodunofficial.client.screen.MarkerOffsetScreen(te));
        }
    }

    public static void openMarkerCantScreen(Object marker) {
        if (marker instanceof jp.ngt.rtm.rail.TileEntityMarker te) {
            Minecraft.getInstance().setScreen(
                new com.portofino.realtrainmodunofficial.client.screen.MarkerCantScreen(te));
        }
    }

    public static void openSignalChangerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SignalChangerScreen(pos));
    }

    public static void openSignalReceiverScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SignalReceiverScreen(pos));
    }

    public static void openSignalValueScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SignalValueScreen(pos));
    }

    public static void openTrainDetectorScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new TrainDetectorScreen(pos));
    }

    public static void openMarkerConfigScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(
            new com.portofino.realtrainmodunofficial.client.screen.MarkerConfigScreen(pos));
    }

    public static void openSpeakerScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(
            new com.portofino.realtrainmodunofficial.client.screen.SpeakerScreen(pos));
    }

    public static void openSignboardScreen(BlockPos pos) {
        if (Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be) {
            Minecraft.getInstance().setScreen(
                new com.portofino.realtrainmodunofficial.client.screen.SignboardScreen(be));
        }
    }

    public static void openDetectorConfigScreen(BlockPos pos) {
        if (Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be) {
            Minecraft.getInstance().setScreen(
                new com.portofino.realtrainmodunofficial.client.screen.TrainDetectorConfigScreen(be));
        }
    }

    /** カメラ: 右クリックでファインダーモードを開閉 */
    public static void toggleCamera() {
        com.portofino.realtrainmodunofficial.client.camera.RtmCamera.INSTANCE.toggle();
    }

    /** 駅設定 GUI を開く (現在のタグビット付き)。 */
    public static void openStationScreen(net.minecraft.core.BlockPos pos, int bits) {
        Minecraft.getInstance().setScreen(
            new com.portofino.rtmupassenger.client.StationScreen(pos, bits));
    }

    /** 券売機 (本家 GuiTicketVendor): 切符 / 回数券 の2ボタン */
    public static void openTicketVendorScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(
            new com.portofino.realtrainmodunofficial.client.screen.TicketVendorScreen(pos));
    }

    /** 標識のテクスチャ変更 (本家 guiIdSelectTileEntityTexture) */
    public static void openRailroadSignScreen(BlockPos pos) {
        if (Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be) {
            ClientItemHelper.openRailroadSignScreen(be);
        }
    }

    public static void openScriptBlockScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new ScriptBlockScreen(pos));
    }

    public static void stopCrossingGateSound(Level level, BlockPos pos) {
        CrossingGateSoundManager.stop(level, pos);
    }

    public static void tickCrossingGateSound(InstalledObjectBlockEntity blockEntity) {
        CrossingGateSoundManager.tick(blockEntity);
    }

    public static void showScriptErrorMessage(String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || message == null || message.isBlank()) {
            return;
        }
        minecraft.player.displayClientMessage(Component.literal("[RTMU Script] " + message), false);
    }
}
