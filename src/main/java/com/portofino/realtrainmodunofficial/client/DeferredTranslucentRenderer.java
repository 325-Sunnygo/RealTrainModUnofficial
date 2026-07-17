package com.portofino.realtrainmodunofficial.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 列車の半透明バッチ (窓ガラス等) 専用の遅延描画バッファ。
 *
 * <p>問題: 車両ごとに「不透明 → ガラス」を描くと、エンティティ用の非固定 RenderType は
 * 次の RenderType 要求時に途中フラッシュされるため、A車のガラスが B車の座席より先に
 * 深度付きで描かれることがある。エンティティの描画順は毎フレーム変わるので、
 * 「別の車両 (や同編成の隣の車) のガラス越しに座席が見えたり消えたりする」チラつきになる。
 *
 * <p>対策: 列車のガラスはこの専用バッファに溜め、地形の半透明 (ガラスブロック等) まで
 * 全て描き終わった AFTER_TRANSLUCENT_BLOCKS ステージで一括描画する。
 * これで座席 (不透明) は常にガラスより先に描かれ、どの角度・順序でも透けて見える。
 * シェーダーパック使用時は RenderType の挙動が変わるため従来経路のまま。
 */
@EventBusSubscriber(modid = RealTrainModUnofficial.MODID, value = Dist.CLIENT)
public final class DeferredTranslucentRenderer {

    private static final MultiBufferSource.BufferSource BUFFER =
            MultiBufferSource.immediate(new ByteBufferBuilder(1536));

    /**
     * 現在描画中の車両。VehicleScriptRenderers が車両描画の間だけセットする。
     * <p>
     * 車両描画は GLRecorder に記録して replay する経路があり、その replay 内で
     * renderNamedGroups が呼ばれるときは scriptRenderer=null → entity=null になる。
     * そのままだと {@link #shouldDefer} が false を返し、ガラスが遅延バッファに載らず
     * pass1 (レール描画より前) で描かれてしまう。すると後から描くレールがガラスの
     * <b>上</b>に乗り「色付きガラス越しなのにレールに色が付かず明るいまま」になる。
     * この参照を entity のフォールバックにすることで replay 経路でも正しく遅延できる。
     */
    private static Object currentVehicle;

    private DeferredTranslucentRenderer() {
    }

    /** 車両描画の開始/終了で呼ぶ (finally で必ず null に戻すこと)。 */
    public static void setCurrentVehicle(Object entity) {
        currentVehicle = entity;
    }

    /** この entity の半透明バッチを遅延バッファへ回すか。 */
    public static boolean shouldDefer(Object entity) {
        if (ShaderCompat.isShaderPackInUse()) {
            return false;
        }
        Object e = entity != null ? entity : currentVehicle;
        return e instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase
                || e instanceof com.portofino.realtrainmodunofficial.entity.TrainEntity
                || e instanceof com.portofino.realtrainmodunofficial.entity.CarEntity
                //信号/踏切等の設置物も同じ問題 (色付きレンズ越しのレール/地形に色が付かない)。
                //1.21 は設置物 (block entity) → レール (AFTER_BLOCK_ENTITIES) の順なので、
                //設置物の半透明も遅延してレールの後に描く。
                || e instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
    }

    public static MultiBufferSource buffer() {
        return BUFFER;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        //地形の半透明の後 = 画面内の不透明物すべての後。ここでガラスを一括描画。
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            BUFFER.endBatch();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            //安全網: 何らかの理由で上のステージが飛んでも残さない
            BUFFER.endBatch();
            //「このフレームの pass0 で描いたバッチか」判定用のフレーム番号を進める
            com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.advanceRenderFrame();
        }
    }
}
