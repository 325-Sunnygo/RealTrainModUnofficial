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
 * <b>置いた物の明るさを「その場所ごと」に決められるようにする。</b>
 *
 * <h2>直した症状</h2>
 * 赤色灯・ミラーボール・蛍光灯・看板が<b>周りを照らさない</b>。
 *
 * <h2>なぜ起きるか</h2>
 * バニラの明るさは {@code BlockState} が持つ<b>固定値</b>で、置いた場所は関係しない。
 * ところが RTMU の設置物は「同じブロックに何を置いたか」で明るさが変わる。
 *
 * <pre>
 *   照明   … レッドストーンが来ていれば 15、来ていなければ 0
 *   蛍光灯 … 常時 15 (壊れていると 0/4/8/12 で明滅)
 *   看板   … パックの設定値
 * </pre>
 *
 * <p>NeoForge はこれ用に <b>3 引数の {@code getLightEmission(state, level, pos)}</b> を足しており、
 * 光源計算がそちらを呼ぶよう本体を書き換えている。Fabric には相当物が無いので、
 * <b>本家と同じ位置</b>を差し替えて同じことをする。
 *
 * <h2>安全側の作り</h2>
 * ここは光源計算の内側なので、<b>絶対に例外を出さないこと</b>。1 つでも投げると
 * その区画の明るさ計算が止まり、ワールドが真っ暗になったりチャンクが読めなくなる。
 * ブロックエンティティが無い・まだ読み込まれていない場合は<b>バニラの値をそのまま返す</b>。
 */
@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {

    @Inject(method = "getEmission", at = @At("RETURN"), cancellable = true)
    private void rtmu$dynamicEmission(long packedPos, BlockState state,
                                      CallbackInfoReturnable<Integer> cir) {
        //RTMU のブロック以外は一切触らない (バニラの明るさ計算に負荷も影響も足さない)
        if (!(state.getBlock()
                instanceof com.portofino.realtrainmodunofficial.block.InstalledObjectBlock)) {
            return;
        }
        try {
            //chunkSource は親クラス側のフィールドなのでアクセサ経由 (LightEngineAccessor 参照)
            LightChunkGetter chunkSource = ((LightEngineAccessor) (Object) this).rtmu$getChunkSource();
            BlockGetter level = chunkSource == null ? null : chunkSource.getLevel();
            if (level == null) {
                return;
            }
            BlockPos pos = BlockPos.of(packedPos);
            //★getBlockEntity を使うこと。getExistingBlockEntity 相当が無いので、
            //  読み込まれていない位置では null が返る。そのときはバニラの値のまま。
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
            //光源計算の内側なので握り潰す。明るさが 1 箇所ずれるより
            //ワールドが壊れる方がはるかに重い。
        }
    }
}
