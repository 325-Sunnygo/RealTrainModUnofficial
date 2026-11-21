package jp.kaiz.atsassistmod.client;

import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import jp.kaiz.atsassistmod.ATSAssistCore;
import jp.kaiz.atsassistmod.controller.trainprotection.TrainProtectionType;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 本家 jp.kaiz.atsassistmod.render.TrainGuiRender の移植 (運転席 HUD)。
 * 一人称で制御車に乗っている間、ATO / TASC / 制限速度 / 保安装置を表示する。
 * データはサーバーの TrainController が列車の DataMap ("ATSAssist_HUD") に書く。
 */
@EventBusSubscriber(modid = ATSAssistCore.MODID, value = Dist.CLIENT)
public final class AtsaHudRenderer {

    private AtsaHudRenderer() {
    }

    /** 本家 TrainControllerClient.isNotShowHud (セレクター GUI から切替、クライアント設定)。 */
    private static boolean notShowHud;

    public static boolean isNotShowHud() {
        return notShowHud;
    }

    public static void setNotShowHud(boolean value) {
        notShowHud = value;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            return;
        }
        if (notShowHud) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            return;
        }
        if (!(mc.player.getVehicle() instanceof EntityTrainBase train) || !train.isControlCar()) {
            return;
        }
        String hud = train.getResourceState().getDataMap().getString("ATSAssist_HUD");
        if (hud == null || hud.isEmpty()) {
            return;
        }
        String[] parts = hud.split("\\|");
        if (parts.length < 6) {
            return;
        }
        String atoSpeed = parts[0];
        String tascSpeed = parts[1];
        String limitSpeed = parts[2];
        String tpSpeed = parts[3];
        TrainProtectionType tpType;
        try {
            tpType = TrainProtectionType.valueOf(parts[4]);
        } catch (IllegalArgumentException e) {
            tpType = TrainProtectionType.NONE;
        }
        boolean manualDrive = "1".equals(parts[5]);

        GuiGraphics g = event.getGuiGraphics();
        int width = g.guiWidth();
        int height = g.guiHeight();

        //notDisplayCab (運転台モデルなし) はレイアウトが変わる (本家仕様)
        VehicleDefinition def = VehicleRegistry.getById(train.getModelName());
        boolean notDisplayCab = def != null && def.isNotDisplayCab();

        if (!notDisplayCab) {
            //cab 表示あり: 画面中央右下
            int k = width / 2;
            int color = manualDrive ? 0xFF0000 : 0x00FF00;
            g.drawString(mc.font, "ATO : " + atoSpeed, k + 160, height - 40, color, true);
            g.drawString(mc.font, "TASC : " + tascSpeed, k + 160, height - 30, color, true);
            g.drawString(mc.font, "Limit: " + limitSpeed, k + 160, height - 20, 0x00FF00, true);
            if (tpType != TrainProtectionType.NONE) {
                g.drawString(mc.font, tpType.getDisplayName().getString() + ": " + tpSpeed,
                        k + 160, height - 10, 0x00FF00, true);
            }
        } else {
            //cab 表示なし: 左下
            int fixHeight = 50;
            int color = manualDrive ? 0xFF0000 : 0xFFFFFF;
            if (tpType != TrainProtectionType.NONE) {
                g.drawString(mc.font, tpType.getDisplayName().getString() + " : " + tpSpeed,
                        2, height - (fixHeight += 10), 0xFFFFFF, true);
            }
            g.drawString(mc.font, "Limit : " + limitSpeed, 2, height - (fixHeight += 10), 0xFFFFFF, true);
            g.drawString(mc.font, "TASC : " + tascSpeed, 2, height - (fixHeight += 10), color, true);
            g.drawString(mc.font, "ATO : " + atoSpeed, 2, height - (fixHeight += 10), color, true);
        }
    }
}
