package com.portofino.realtrainmodunofficial.client.render;

import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import com.portofino.realtrainmodunofficial.rail.*;
import jp.ngt.rtm.rail.BlockMarker;
import jp.ngt.rtm.rail.util.RailProperty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;

public final class LegacyRailPropertyRegression {
    private static int checks;
    private static void check(boolean value,String why) { checks++;if(!value) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        var originalRegistry=RailRegistry.getAll();
        var loadedField=RailPackLoader.class.getDeclaredField("LOADED");loadedField.setAccessible(true);
        @SuppressWarnings("unchecked") var loaded=(List<RailDefinition>)loadedField.get(null);
        var originalLoaded=new ArrayList<>(loaded);
        Method parse=RailPackLoader.class.getDeclaredMethod("parseRailJson",byte[].class,String.class);parse.setAccessible(true);
        try {
            loaded.clear();
            Path reference=Path.of("../reference/KaizPatchX-master/src/main/resources/assets/minecraft/models/json");
            int types=0;
            try(var files=Files.list(reference)) {
                for(Path source:files.filter(f->f.getFileName().toString().startsWith("ModelRail_")).toList()) {
                    Path current=Path.of("src/main/resources/assets/minecraft/models/json").resolve(source.getFileName());
                    check(Arrays.equals(Files.readAllBytes(source),Files.readAllBytes(current)),"original rail type bytes: "+source.getFileName());
                    parse.invoke(null,Files.readAllBytes(current),"original-regression");types++;
                }
            }
            RailRegistry.setDefinitions(loaded);
            check(types==24 && RailRegistry.getAll().size()==24,"all 24 original rail types registered");
            var wood=BlockMarker.propertyFor("1067mm_Wood",null);
            check(wood.block==Blocks.GRAVEL && wood.blockHeight==0.0625F,"wood default is gravel at original height");
            var snow=BlockMarker.propertyFor("1067mm_Wood","minecraft:snow");
            check(snow.block==Blocks.SNOW && snow.blockHeight==0.24F,"snow variant retains configured height");
            check(BlockMarker.propertyFor("1067mm_Wood","minecraft:air").block==Blocks.AIR,"explicit no-ballast choice is preserved");
            check(BlockMarker.propertyFor("1067mm_Wood","minecraft:stone").block==Blocks.STONE,"block selection is independent of the model variant list");
            var fallback=RailProperty.getDefaultProperty();
            check(fallback.railModel.equals("1067mm_Wood") && fallback.block==Blocks.GRAVEL,"default item property matches original");
            CompoundTag nbt=new CompoundTag();snow.writeToNBT(nbt);var restored=RailProperty.readFromNBT(nbt);
            check(restored.railModel.equals(snow.railModel) && restored.block==snow.block
                    && restored.blockHeight==snow.blockHeight,"complete ballast property survives NBT");
            var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE);
            var variant=new RailProperty("1067mm_Wood",Blocks.STONE,2,0.375F);
            jp.ngt.rtm.item.ItemRail.writePropToItem(variant,stack);
            var held=jp.ngt.rtm.item.ItemRail.getProperty(stack);
            check(held.blockMetadata==2 && held.blockHeight==0.375F && held.block==Blocks.STONE,
                    "rail item preserves same-block variants with distinct metadata and height");
            var tram=BlockMarker.propertyFor("1067mm_Tram",null);
            check(tram.block==Blocks.GRAY_CARPET && tram.blockMetadata==7,"original tram carpet metadata resolves to gray carpet");
            CompoundTag oldCarpet=new CompoundTag();oldCarpet.putString("RailModel","1067mm_Tram");
            oldCarpet.putString("BlockName","minecraft:carpet");oldCarpet.putInt("BlockMetadata",7);oldCarpet.putFloat("BlockHeight",0.0625F);
            check(RailProperty.readFromNBT(oldCarpet).blockState().getBlock()==Blocks.GRAY_CARPET,"original rail NBT carpet remains visible after loading");
            check(RailProperty.resolveBlock("minecraft:grass",0)==Blocks.GRASS_BLOCK,"legacy grass means grass block rather than a plant");
            check(RailProperty.resolveBlock("unknown_mod:gravel",0)==Blocks.AIR,"unknown mod ID must not be replaced with vanilla gravel");
            float[] corners={-0.25F,1.25F,0.2F,0.8F};
            float[] heights=BallastGeometry.finiteHeights(corners,0.0625F);
            check(Arrays.equals(corners,heights),"slope and cant corners must not be clipped to the owner unit cube");
            heights[0]=3;check(corners[0]==-0.25F,"model data owns an independent corner array");
            check(BallastGeometry.finiteHeights(new float[]{Float.NaN,0,1,Float.POSITIVE_INFINITY},0.0625F)[0]==0.0625F,
                    "invalid heights fall back without affecting valid geometry");
            System.out.println("Legacy rail property regressions: "+checks+" passed");
        } finally { loaded.clear();loaded.addAll(originalLoaded);RailRegistry.setDefinitions(originalRegistry); }
    }
}
