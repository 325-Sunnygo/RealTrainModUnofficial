package jp.ngt.rtm.render;

import jp.ngt.ngtlib.io.ScriptUtil;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 本家 jp.ngt.rtm.render.TileEntityPartsRenderer の移植。
 */
public abstract class TileEntityPartsRenderer extends PartsRenderer {

    public TileEntityPartsRenderer(String... par1) {
        super(par1);
    }

    /**
     * 本家 render: スクリプトの render(tileEntity, pass, partialTick) を実行
     * (踏切/改札の MachinePartsRenderer と信号の SignalPartsRenderer で共通)
     */
    public void render(Object t, int pass, float partialTick) {
        this.currentPass = pass;
        //スクリプトが落ちたらフラグを立てる (呼び出し側が素のモデル描画へ戻せるように)。
        //PartsRenderer.execRenderScript 参照。
        this.execRenderScript(t, pass, partialTick);
    }

    /**
     * 本家: TileEntityMachineBase.tick — ワールド時間で代替
     */
    public int getTick(Object tile) {
        if (tile instanceof BlockEntity be && be.getLevel() != null) {
            return (int) be.getLevel().getGameTime();
        }
        return 0;
    }
}
