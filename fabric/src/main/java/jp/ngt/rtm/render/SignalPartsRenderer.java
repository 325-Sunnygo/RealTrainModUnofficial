package jp.ngt.rtm.render;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 本家 jp.ngt.rtm.render.SignalPartsRenderer の移植。
 * 信号機のスクリプト (RenderClock.js 等) が renderClass に指定する。
 * スクリプトは renderer.getSignal(entity) で現示 (1=停止 .. 6=高速進行) を取り、
 * 点灯パーツを切り替える。
 */
public class SignalPartsRenderer extends TileEntityPartsRenderer {

    public SignalPartsRenderer(String... par1) {
        super(par1);
    }

    /**
     * 本家: TileEntitySignal.getSignal() — 現在の現示レベル
     */
    public int getSignal(Object tile) {
        return tile instanceof InstalledObjectBlockEntity be ? be.getSignal() : 0;
    }

    /**
     * 本家: TileEntitySignal.getBlockDirection() / getRotation()
     * BER 側でブロックの向きを適用済みのため 0 (InstalledObjectBlockEntity も 0 を返す)
     */
    public float getBlockDirection(Object tile) {
        return tile instanceof InstalledObjectBlockEntity be ? be.getBlockDirection() : 0.0F;
    }

    public float getRotation(Object tile) {
        return tile instanceof InstalledObjectBlockEntity be ? be.getRotation() : 0.0F;
    }

    /**
     * 本家: 信号が埋まっているブロックが不透明か (時計の両面描画判定に使う)
     */
    public boolean isOpaqueCube(Object tile) {
        if (tile instanceof BlockEntity be && be.getLevel() != null) {
            return be.getLevel().getBlockState(be.getBlockPos()).isSolidRender(be.getLevel(), be.getBlockPos());
        }
        return false;
    }
}
