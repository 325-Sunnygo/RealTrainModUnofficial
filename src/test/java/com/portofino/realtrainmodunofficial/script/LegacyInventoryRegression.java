package com.portofino.realtrainmodunofficial.script;

import jp.ngt.ngtlib.renderer.*;
import jp.ngt.rtm.render.OrnamentPartsRenderer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class LegacyInventoryRegression {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    private static final class Capture implements com.mojang.blaze3d.vertex.VertexConsumer {
        int vertices; float firstX; int red,alpha;
        public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x,float y,float z) { if(vertices++==0) firstX=x; return this; }
        public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r,int g,int b,int a) { red=r; alpha=a; return this; }
        public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u,float v) { return this; }
        public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u,int v) { return this; }
        public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u,int v) { return this; }
        public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
    public static void main(String[] args) throws Exception {
        // Standalone registry tests have no discovered mods; feature flags still require the loader list.
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        LegacyVehicleConfigRegression.main(args);
        LegacyMutableMeshRegression.main(args);
        LegacyNativePrecisionRegression.main(args);
        com.portofino.realtrainmodunofficial.client.render.LegacyRailPropertyRegression.main(args);
        com.portofino.realtrainmodunofficial.client.render.LegacyDeferredAlphaRegression.main(args);
        var itemIds=com.portofino.realtrainmodunofficial.RealTrainModUnofficialItems.ITEMS.getEntries().stream()
            .map(entry -> entry.getId().getPath()).collect(java.util.stream.Collectors.toSet());
        check(!itemIds.contains("formation") && !itemIds.contains("formation_crowbar"),"original-only item registry removes custom formation tools");
        check(itemIds.contains("camera") && itemIds.contains("train") && itemIds.contains("car"),"original RTM tools remain registered");
        try (var original=com.portofino.realtrainmodunofficial.util.BundledResourceStreams.open(
                LegacyInventoryRegression.class,"/assets/minecraft/models/ModelRail_1067mm.mqo")) {
            check(original!=null,"legacy uppercase bundled model resolves after modern path canonicalization");
        }
        var renderer=new OrnamentPartsRenderer();
        var container=new SimpleContainer(2);
        container.setItem(0,new ItemStack(Items.STONE,37));
        check(renderer.getInventoryItem(container,1)==null,"empty slots preserve legacy null");
        check(renderer.getInventoryItem(container,-1)==null && renderer.getInventoryItem(container,2)==null,"slot bounds");
        Object item=renderer.getInventoryItem(container,0);
        check(renderer.getStackSize(item)==37 && renderer.getStackSize(ItemStack.EMPTY)==0,"real counts and empty stack");
        check(renderer.getStackSize(null)==0,"null stack count");
        var handler=new ItemStackHandler(2); handler.setStackInSlot(1,new ItemStack(Items.DIRT,12));
        check(renderer.getStackSize(renderer.getInventoryItem(handler,1))==12,"NeoForge item handler");
        var alternate=new TrainScriptSystem.ScriptModelRenderer(null,"test");
        alternate.beginRecording(123);
        check(alternate.getStackSize(alternate.getInventoryItem(container,0))==37,"alternate renderer shares inventory behavior");
        alternate.endRecording(true);
        check(!alternate.tryReplayCachedScript(123),"inventory access prevents stale replay cache");
        var recorder=new GLRecorder(); GLRecorder.activate(recorder);
        try {
            renderer.renderItem(null,item);
            var command=recorder.getCommands().get(0);
            var draw=(GLRecorder.ItemDraw)command.payload;
            check(command.op==GLRecorder.Op.RENDER_ITEM && recorder.hasGeometry() && draw.stack().is(Items.STONE),"real item render command");
            container.getItem(0).setCount(2);
            check(draw.stack().getCount()==37,"recording snapshots mutable stack");
            renderer.renderItem(null,null); renderer.renderItem(null,ItemStack.EMPTY);
            check(recorder.getCommands().size()==1,"empty items do not draw");
            var engine=jp.ngt.ngtlib.io.ScriptUtil.createEngine(); engine.put("renderer",renderer); engine.put("container",container);
            engine.eval("var item=renderer.getInventoryItem(container,0); if(renderer.getStackSize(item)!==2) throw 'count'; renderer.renderItem(container,item);");
            check(recorder.getCommands().size()==2,"Nashorn inventory-to-render path");
            GL11Facade.glDisable(GL11Facade.GL_DEPTH_TEST); renderer.renderItem(null,item);
            check(!recorder.getCommands().get(2).state.enabled(GL11Facade.GL_DEPTH_TEST,true),"item draw captures legacy state");
            var gui=NGTRenderHelper.getItemRenderer();
            container.getItem(0).set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);
            gui.zLevel=25; gui.renderItemIntoGUI(null,null,item,12,34); gui.zLevel=0;
            var plain=(GLRecorder.GuiItemDraw)recorder.getCommands().get(3).payload;
            check(plain.x()==12 && plain.y()==34 && plain.depth()==25 && !plain.decorations(),"old GUI coordinates and command");
            check(Boolean.FALSE.equals(plain.stack().get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) && Boolean.TRUE.equals(container.getItem(0).get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),"GUI without effects preserves source stack");
            gui.renderItemAndEffectIntoGUI(null,null,item,1,2);
            var effect=(GLRecorder.GuiItemDraw)recorder.getCommands().get(4).payload;
            check(Boolean.TRUE.equals(effect.stack().get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),"effect GUI preserves glint");
            gui.renderItemOverlayIntoGUI(null,null,item,3,4,"abc");
            var overlay=(GLRecorder.GuiItemDraw)recorder.getCommands().get(5).payload;
            check(overlay.decorations() && overlay.text().equals("abc"),"GUI overlay retains explicit text");
            engine.eval("var I=Java.type('jp.ngt.ngtlib.renderer.NGTRenderHelper').getItemRenderer(); I.renderItemIntoGUI(null,null,item,5,6); I.renderItemOverlayIntoGUI(null,null,item,5,6,null);");
            check(recorder.getCommands().size()==8,"Nashorn old GUI overloads");
            GL11Facade.glPushAttrib(GL11Facade.GL_POINT_BIT); GL11Facade.glPointSize(7);
            check(recorder.state().snapshot().pointSize()==7,"point size recorded");
            GL11Facade.glPopAttrib(); check(recorder.state().snapshot().pointSize()==null,"point attrib restoration");
        } finally { GLRecorder.deactivate(); }
        Capture capture=new Capture();
        final com.mojang.blaze3d.vertex.VertexFormat.Mode[] mode={null};
        net.minecraft.client.renderer.MultiBufferSource buffer=type->{ mode[0]=type.mode(); return capture; };
        var drawTess=com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.class.getDeclaredMethod("drawTess",GLRecorder.TessDraw.class,com.mojang.blaze3d.vertex.PoseStack.class,net.minecraft.client.renderer.MultiBufferSource.class,int.class,int.class,net.minecraft.resources.ResourceLocation.class);
        drawTess.setAccessible(true);
        float[] vertices={0,0,0,0,0,1,1,1,1, 1,0,0,0,0,1,1,1,1, 1,1,0,0,0,1,1,1,1};
        drawTess.invoke(null,new GLRecorder.TessDraw(2,vertices),new com.mojang.blaze3d.vertex.PoseStack(),buffer,0,0,null);
        check(capture.vertices==6 && mode[0]==com.mojang.blaze3d.vertex.VertexFormat.Mode.DEBUG_LINES,"line loop emits three native line segments");
        capture.vertices=0;
        var replay=com.portofino.realtrainmodunofficial.client.render.VehicleScriptRenderers.class.getDeclaredMethod("replay",GLRecorder.class,com.mojang.blaze3d.vertex.PoseStack.class,net.minecraft.client.renderer.MultiBufferSource.class,int.class,int.class,com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.MqoModel.class,jp.ngt.ngtlib.renderer.model.PolygonModel.class);
        replay.setAccessible(true);
        var matrixRecorder=new GLRecorder();
        matrixRecorder.multMatrix(new org.joml.Matrix4f().translation(2,0,0).get(new float[16]));
        float[] translated=vertices.clone(); translated[0]=1;
        matrixRecorder.drawTess(new GLRecorder.TessDraw(4,translated));
        replay.invoke(null,matrixRecorder,new com.mojang.blaze3d.vertex.PoseStack(),buffer,0,0,null,null);
        check(capture.vertices==4 && capture.firstX==3,"normal replay applies glMultMatrix to emitted vertices");
        capture.vertices=0;
        drawTess.invoke(null,new GLRecorder.TessDraw(0,vertices),new com.mojang.blaze3d.vertex.PoseStack(),buffer,0,0,null);
        check(capture.vertices==12 && mode[0]==com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,"point centers emit four corners each");
        matrixRecorder.clear(); matrixRecorder.color(0.5F,1,1,0.25F);
        matrixRecorder.drawTess(new GLRecorder.TessDraw(4,vertices,false,false,null,true));
        replay.invoke(null,matrixRecorder,new com.mojang.blaze3d.vertex.PoseStack(),buffer,0,0,null,null);
        check(capture.red==128 && capture.alpha==64,"legacy current color reaches uncolored geometry");
        matrixRecorder.clear(); matrixRecorder.color(0.5F,1,1,0.25F);
        matrixRecorder.drawTess(new GLRecorder.TessDraw(4,vertices));
        replay.invoke(null,matrixRecorder,new com.mojang.blaze3d.vertex.PoseStack(),buffer,0,0,null,null);
        check(capture.red==255 && capture.alpha==255,"explicit vertex colors do not receive a second tint");
        var wrapped=new jp.ngt.mccompat.ItemStackCompat(new ItemStack(Items.STONE,12));
        var tag=new jp.ngt.mccompat.nbt.NBTTagCompound(); wrapped.setTagCompound(tag);
        check(wrapped.hasTagCompound(),"empty assigned NBT remains present");
        tag.setInteger("value",7);
        check(wrapped.stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getInt("value")==7,"assigned NBT mutations update native components");
        var live=wrapped.getTagCompound(); live.setInteger("value",8);
        check(wrapped.getTagCompound().getInteger("value")==8,"getTagCompound returns writable data");
        var child=new jp.ngt.mccompat.nbt.NBTTagCompound(); live.setTag("child",child); child.setString("name","nested");
        check(wrapped.getTagCompound().getCompoundTag("child").getString("name").equals("nested"),"nested assigned compound writes through");
        var list=new jp.ngt.mccompat.nbt.NBTTagList(); live.setTag("list",list);
        var entry=new jp.ngt.mccompat.nbt.NBTTagCompound(); list.appendTag(entry); entry.setLong("number",42);
        live.getTagList("list",10).getCompoundTagAt(0).setLong("number",43);
        check(wrapped.getTagCompound().getTagList("list",10).getCompoundTagAt(0).getLong("number")==43,"nested list entries write through");
        var copied=live.copy(); copied.setInteger("value",99);
        check(wrapped.getTagCompound().getInteger("value")==8,"copy edits stay independent");
        var legacy=jp.ngt.ngtlib.io.ScriptUtil.createEngine(); legacy.put("stack",wrapped);
        legacy.eval("stack.stackSize=9; if(stack.field_77994_a!==9) throw 'size'; stack.field_77994_a=8; stack.getTagCompound().setInteger('js',32);");
        check(wrapped.stack.getCount()==8 && wrapped.getTagCompound().getInteger("js")==32,"Nashorn live stack fields and NBT aliases");
        check(wrapped.getTagCompound()==tag && new jp.ngt.mccompat.ItemStackCompat(wrapped.stack).getTagCompound()==tag,"live tag identity is shared across wrappers");
        wrapped.setTagCompound(null); check(!wrapped.hasTagCompound(),"null NBT assignment removes component");
        tag.setInteger("detached",1); check(!wrapped.hasTagCompound(),"detached old tag cannot restore removed data");
        var tool=new jp.ngt.mccompat.ItemStackCompat(new ItemStack(Items.IRON_PICKAXE));
        tool.func_77964_b(17);
        check(tool.getItemDamage()==17 && tool.func_77960_j()==17 && tool.isItemDamaged() && tool.getMaxDamage()>17,"legacy damage API reaches native item data");
        var extra=new jp.ngt.mccompat.nbt.NBTTagCompound(); extra.setString("merged","yes");
        tag.merge(extra); check(tag.func_74779_i("merged").equals("yes") && tag.func_150299_b("merged")==8 && tag.func_74732_a()==10,"compound merge and legacy tag IDs");
        var nestedTag=(jp.ngt.mccompat.nbt.NBTTagCompound)tag.func_74781_a("child");
        check(nestedTag.getString("name").equals("nested"),"getTag returns a usable legacy compound");
        var numbers=new jp.ngt.mccompat.nbt.NBTTagList();
        numbers.appendTag(net.minecraft.nbt.DoubleTag.valueOf(1.25));
        check(numbers.func_150303_d()==6 && numbers.func_150309_d(0)==1.25 && numbers.func_74732_a()==9,"legacy numeric list access");
        numbers.func_150304_a(0,net.minecraft.nbt.DoubleTag.valueOf(2.5));
        var numberCopy=numbers.func_74737_b(); numbers.removeTag(0);
        check(numberCopy.getDoubleAt(0)==2.5 && numbers.tagCount()==0,"list replacement, removal and independent copy");
        System.out.println("Legacy inventory regressions: "+checks+" passed");
    }
}
