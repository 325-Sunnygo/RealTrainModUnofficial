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

    public static void openEditorScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(
            new com.portofino.realtrainmodunofficial.client.screen.EditorScreen());
    }

    public static void openPainterSettingsScreen(ItemStack stack, boolean offHand) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
            new com.portofino.realtrainmodunofficial.client.screen.PainterSettingsScreen(
                offHand ? net.minecraft.world.InteractionHand.OFF_HAND
                        : net.minecraft.world.InteractionHand.MAIN_HAND, stack));
    }


    public static void openMiniatureSettingsScreen(ItemStack stack, boolean offHand) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
            new com.portofino.realtrainmodunofficial.client.screen.MiniatureSettingsScreen(
                offHand ? net.minecraft.world.InteractionHand.OFF_HAND
                        : net.minecraft.world.InteractionHand.MAIN_HAND, stack));
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

    /** 編成アイテムの編集画面。 */
    public static void openFormationScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(
            new com.portofino.realtrainmodunofficial.client.screen.FormationEditScreen(stack));
    }

    /** 駅設定 GUI を開く (現在のタグビット付き)。 */
    public static void openStationScreen(net.minecraft.core.BlockPos pos, int bits, int capacity) {
        Minecraft.getInstance().setScreen(
            new com.portofino.rtmupassenger.client.StationScreen(pos, bits, capacity));
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

    /**
     * 背景パネルの設定画面。今の設定を持って開く
     * (開いた瞬間に現在値が入っていないと、保存で既定値に戻ってしまう)。
     */
    public static void openNpcModelScreen(int entityId) {
        ClientItemHelper.openNpcModelScreen(entityId, false);
    }

    public static void openNpcItemModelScreen(boolean offHand) {
        ClientItemHelper.openNpcModelScreen(-1, offHand);
    }

    public static void openCargoModelScreen(int entityId) {
        ClientItemHelper.openCargoModelScreen(entityId, false);
    }

    public static void openCargoItemModelScreen(boolean offHand) {
        ClientItemHelper.openCargoModelScreen(-1, offHand);
    }

    /** NPC の商売画面 (本家 GuiSalesperson)。 */
    public static void openNpcTradeScreen(int entityId) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getEntity(entityId) instanceof jp.ngt.rtm.entity.npc.EntityNPC npc) {
            mc.setScreen(new com.portofino.realtrainmodunofficial.client.screen.NpcTradeScreen(npc));
        }
    }

    /** 装飾ブロックの編集画面 (本家 guiIdDecoration)。 */
    public static void openDecorationEditScreen() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new com.portofino.realtrainmodunofficial.client.screen.DecorationEditScreen(mc.player));
        }
    }

    public static void openMovingMachineScreen(BlockPos pos) {
        com.portofino.realtrainmodunofficial.client.screen.MovingMachineScreen.open(pos);
    }

    public static void openSignalConverterScreen(BlockPos pos) {
        com.portofino.realtrainmodunofficial.client.screen.SignalConverterScreen.open(pos);
    }

    public static void openStationCoreScreen(BlockPos pos) {
        com.portofino.realtrainmodunofficial.client.screen.StationCoreScreen.open(pos);
    }

    public static void openBackgroundPanelScreen(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        byte[] image = new byte[0];
        String name = "";
        float scale = com.portofino.realtrainmodunofficial.blockentity.BackgroundPanelBlockEntity.DEFAULT_SCALE;
        float offsetY = 0.0F;
        if (mc.level.getBlockEntity(pos)
                instanceof com.portofino.realtrainmodunofficial.blockentity.BackgroundPanelBlockEntity be) {
            image = be.getImage();
            name = be.getImageName();
            scale = be.getScale();
            offsetY = be.getOffsetY();
        }
        mc.setScreen(new com.portofino.realtrainmodunofficial.client.screen.BackgroundPanelScreen(
            pos, image, name, scale, offsetY));
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

    /**
     * 指定範囲のレールベースに道床を焼き直させる。
     *
     * <p>2 つ要る:
     * <ol>
     *   <li>{@code requestModelDataUpdate} — これを申請した BlockEntity だけが
     *       次のチャンク焼きで {@code getModelData()} を呼び直される</li>
     *   <li>{@code setBlocksDirty} — チャンクを焼き直させる</li>
     * </ol>
     * 片方だけだと、焼き直しても中身が古いまま / 中身は新しいのに焼かれないまま になる。
     */
    /** レール 1 本ぶんの焼き込みメッシュを捨てる (線形が変わった時)。 */
    public static void invalidateRailMesh(Level level, int x, int y, int z) {
        if (level == null || !level.isClientSide()) {
            return;
        }
        com.portofino.realtrainmodunofficial.client.render.RailScriptRenderers.invalidate(
            new net.minecraft.core.BlockPos(x, y, z));
    }

    public static void refreshRailBallast(Level level, int x0, int y0, int z0, int x1, int y1, int z1) {
        if (level == null || !level.isClientSide()) {
            return;
        }
        int minCX = x0 >> 4, maxCX = x1 >> 4;
        int minCZ = z0 >> 4, maxCZ = z1 >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                // ブロック総当たりではなくチャンクの BlockEntity 一覧を見る (敷設範囲は最大 64m)
                for (var be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof jp.ngt.rtm.rail.TileEntityLargeRailBase rail)) {
                        continue;
                    }
                    BlockPos p = rail.getBlockPos();
                    if (p.getX() < x0 || p.getX() > x1 || p.getY() < y0 || p.getY() > y1
                            || p.getZ() < z0 || p.getZ() > z1) {
                        continue;
                    }
                    rail.requestModelDataUpdate();
                }
            }
        }
        Minecraft.getInstance().levelRenderer.setBlocksDirty(x0, y0, z0, x1, y1, z1);
    }
}
