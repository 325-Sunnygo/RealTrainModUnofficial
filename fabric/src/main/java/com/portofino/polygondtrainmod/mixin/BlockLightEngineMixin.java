package com.portofino.polygondtrainmod.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 置いた物の明るさを「その場所ごと」に決められるようにする。
 * 直した症状
 * 赤色灯・ミラーボール・蛍光灯・看板が周りを照らさない。
 */
@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {

    @Inject(method = "getEmission", at = @At("RETURN"), cancellable = true)
    private void rtmu$dynamicEmission(long packedPos, BlockState state,
                                      CallbackInfoReturnable<Integer> cir) {
        // RTMU のブロック以外は一切触らない (バニラの明るさ計算に負荷も影響も足さない)
        if (!(state.getBlock()
                instanceof com.portofino.realtrainmodunofficial.block.InstalledObjectBlock)) {
            return;
        }
        try {
            // chunkSource は親クラス側のフィールドなのでアクセサ経由 (LightEngineAccessor 参照)
            LightChunkGetter chunkSource = ((LightEngineAccessor) (Object) this).rtmu$getChunkSource();
            BlockGetter level = chunkSource == null ? null : chunkSource.getLevel();
            if (level == null) {
                return;
            }
            BlockPos pos = BlockPos.of(packedPos);
            // ★getBlockEntity を使うこと。getExistingBlockEntity 相当が無いので、
            // 読み込まれていない位置では null が返る。そのときはバニラの値のまま。
            if (level.getBlockEntity(pos)
                    instanceof com.portofino.realtrainmodunofficial.blockentity
                        .InstalledObjectBlockEntity be) {
                int emission = com.portofino.realtrainmodunofficial.block.InstalledObjectBlock
                    .dynamicLightEmission(be);
                if (emission > cir.getReturnValueI()) {
                    cir.setReturnValue(emission);
                }
            }
        } catch (Throwable ignored) {
            // 光源計算の内側なので握り潰す。明るさが 1 箇所ずれるより
            // ワールドが壊れる方がはるかに重い。
        }
    }
}
