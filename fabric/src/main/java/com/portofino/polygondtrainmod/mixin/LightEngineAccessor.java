package com.portofino.polygondtrainmod.mixin;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * BlockLightEngineMixin が世界を引くための入口。
 * ★chunkSource は BlockLightEngine ではなく親の LightEngine が
 * 持っている。
 */
@Mixin(LightEngine.class)
public interface LightEngineAccessor {

    @Accessor("chunkSource")
    LightChunkGetter rtmu$getChunkSource();
}
