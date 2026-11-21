package jp.ngt.ngtlib.renderer;

import jp.ngt.mccompat.ItemStackCompat;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Inventory and item rendering bridge used by cargo and installed-object scripts. */
public final class LegacyItemRenderer {
    public static final LegacyItemRenderer INSTANCE=new LegacyItemRenderer();
    public float zLevel;
    private LegacyItemRenderer() {}
    private static final ThreadLocal<net.minecraft.client.gui.GuiGraphics> GUI=new ThreadLocal<>();
    public static LegacyRenderState.Scope guiContext(net.minecraft.client.gui.GuiGraphics graphics) {
        var previous=GUI.get(); GUI.set(graphics);
        return ()->{ if(previous==null) GUI.remove(); else GUI.set(previous); };
    }
    public void renderItemIntoGUI(Object font,Object textureManager,Object item,int x,int y) { guiItem(font,item,x,y,false,false,null); }
    public void renderItemAndEffectIntoGUI(Object font,Object textureManager,Object item,int x,int y) { guiItem(font,item,x,y,true,false,null); }
    public void renderItemOverlayIntoGUI(Object font,Object textureManager,Object item,int x,int y,String text) { guiItem(font,item,x,y,true,true,text); }
    public void renderItemIntoGUI(Object item,int x,int y) { guiItem(null,item,x,y,false,false,null); }
    public void renderItemAndEffectIntoGUI(Object item,int x,int y) { guiItem(null,item,x,y,true,false,null); }
    private void guiItem(Object font,Object item,int x,int y,boolean effects,boolean decorations,String text) {
        ItemStack stack=ItemStackCompat.unwrap(item);
        if(stack==null || stack.isEmpty()) return;
        stack=stack.copy();
        if(!effects) stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,false);
        var recorder=GLRecorder.active();
        if(recorder!=null) recorder.renderGuiItem(stack,x,y,decorations,text,font,zLevel);
        else {
            var graphics=GUI.get(); boolean own=graphics==null;
            if(own) { var minecraft=net.minecraft.client.Minecraft.getInstance(); graphics=new net.minecraft.client.gui.GuiGraphics(minecraft,minecraft.renderBuffers().bufferSource()); }
            drawGui(new GLRecorder.GuiItemDraw(stack,x,y,decorations,text,font,zLevel),graphics);
            if(own) graphics.flush();
        }
    }
    public void renderItemOverlayIntoGUI(Object font,Object textureManager,Object item,int x,int y) { renderItemOverlayIntoGUI(font,textureManager,item,x,y,null); }
    private static void drawGui(GLRecorder.GuiItemDraw draw,net.minecraft.client.gui.GuiGraphics graphics) {
        var minecraft=net.minecraft.client.Minecraft.getInstance();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0,0,draw.depth());
            if(draw.decorations()) graphics.renderItemDecorations(draw.font() instanceof net.minecraft.client.gui.Font font ? font : minecraft.font,draw.stack(),draw.x(),draw.y(),draw.text());
            else graphics.renderItem(draw.stack(),draw.x(),draw.y());
        } finally { graphics.pose().popPose(); }
    }
    public static void replayGui(GLRecorder.GuiItemDraw draw,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffer) {
        var minecraft=net.minecraft.client.Minecraft.getInstance();
        var source=buffer instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource nativeSource ? nativeSource : minecraft.renderBuffers().bufferSource();
        var graphics=new net.minecraft.client.gui.GuiGraphics(minecraft,source);
        graphics.pose().mulPose(pose.last().pose());
        drawGui(draw,graphics); graphics.flush();
    }
    public static Object inventoryItem(Object source,int slot) {
        if(source instanceof Player player) source=player.getInventory();
        if(slot<0) return null;
        ItemStack stack;
        if(source instanceof Container inventory) {
            if(slot>=inventory.getContainerSize()) return null;
            stack=inventory.getItem(slot);
        } else if(source instanceof IItemHandler inventory) {
            if(slot>=inventory.getSlots()) return null;
            stack=inventory.getStackInSlot(slot);
        } else return null;
        return stack==null || stack.isEmpty() ? null : new ItemStackCompat(stack);
    }
    public void renderItem(Object entity,Object item,int pass) {
        ItemStack stack=ItemStackCompat.unwrap(item);
        GLRecorder recorder=GLRecorder.active();
        if(recorder!=null && stack!=null && !stack.isEmpty()) {
            int seed=entity instanceof net.minecraft.world.entity.Entity nativeEntity ? nativeEntity.getId() : 0;
            recorder.renderItem(stack,seed);
        }
    }
}
