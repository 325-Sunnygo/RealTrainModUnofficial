package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import com.portofino.realtrainmodunofficial.item.EditorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 選択範囲の状態を画面隅に出す (neo mcte 追加)。
 * 本家も MCTEU も、いま何ブロック選んでいるのかはエディタ画面を開かないと分からない。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class SelectionHud {

    private SelectionHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        boolean holding = mc.player.getMainHandItem().getItem() instanceof EditorItem
            || mc.player.getOffhandItem().getItem() instanceof EditorItem;
        if (!holding) {
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        int x = 6;
        int y = 6;

        g.drawString(mc.font, Component.literal("neo mcte").withStyle(ChatFormatting.AQUA), x, y, 0xFFFFFF);
        y += 11;

        BlockPos p1 = ClientSelection.pos1();
        if (p1 == null) {
            g.drawString(mc.font, Component.translatable("hud.realtrainmodunofficial.editor.pick_first")
                .withStyle(ChatFormatting.GRAY), x, y, 0xA0A0A0);
            return;
        }

        AABB box = ClientSelection.box();
        int sx = (int) box.getXsize();
        int sy = (int) box.getYsize();
        int sz = (int) box.getZsize();
        long volume = (long) sx * sy * sz;

        if (!ClientSelection.hasEnd()) {
            g.drawString(mc.font, Component.translatable("hud.realtrainmodunofficial.editor.pick_second")
                .withStyle(ChatFormatting.YELLOW), x, y, 0xFFD24D);
            y += 11;
        }
        g.drawString(mc.font, Component.literal(sx + " x " + sy + " x " + sz), x, y, 0xFFFFFF);
        y += 11;
        // 10 万を超えると実行を断られるので、目安として色を変える
        int color = volume > jp.ngt.mcte.editor.filter.EditorOps.MAX_BLOCKS ? 0xFF6060 : 0xA0A0A0;
        g.drawString(mc.font, Component.literal(String.format("%,d blocks", volume)), x, y, color);
    }
}
