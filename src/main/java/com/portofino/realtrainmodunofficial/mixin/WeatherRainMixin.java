package com.portofino.realtrainmodunofficial.mixin;

import com.portofino.realtrainmodunofficial.client.render.VehicleRainLayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** バニラの雨が車両を貫通するのを防ぐ。 LevelRenderer.renderSnowAndRai… */
@Mixin(LevelRenderer.class)
public class WeatherRainMixin {
    @Redirect(
            method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int rtmu$blockRainThroughVehicles(Level level, Heightmap.Types type, int x, int z) {
        if (VehicleRainLayer.blocksRain(level, x, z)) {
            return Integer.MAX_VALUE;
        }
        return level.getHeight(type, x, z);
    }
}
