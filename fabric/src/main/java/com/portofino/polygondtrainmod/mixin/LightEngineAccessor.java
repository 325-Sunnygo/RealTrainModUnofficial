package com.portofino.polygondtrainmod.mixin;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link BlockLightEngineMixin} が世界を引くための入口。
 *
 * <p>★{@code chunkSource} は {@code BlockLightEngine} ではなく<b>親の {@code LightEngine}</b> が
 * 持っている。{@code @Shadow} は対象クラス自身のメンバしか探さないので、
 * 子クラス側で shadow すると<b>起動時に「field was not located」で落ちる</b> (実際に踏んだ)。
 * 親を対象にしたアクセサに分ける必要がある。
 */
@Mixin(LightEngine.class)
public interface LightEngineAccessor {

    @Accessor("chunkSource")
    LightChunkGetter rtmu$getChunkSource();
}
