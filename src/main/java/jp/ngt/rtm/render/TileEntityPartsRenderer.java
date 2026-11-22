package jp.ngt.rtm.render;

import jp.ngt.ngtlib.io.ScriptUtil;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 本家 jp.ngt.rtm.render.TileEntityPartsRenderer の移植。 */
public abstract class TileEntityPartsRenderer extends PartsRenderer {

    public TileEntityPartsRenderer(String... par1) {
        super(par1);
    }

    /** 本家 render: スクリプトの render(tileEntity, pass, partia… */
    public void render(Object t, int pass, float partialTick) {
        this.currentPass = pass;
        // スクリプトが落ちたらフラグを立てる (呼び出し側が素のモデル描画へ戻せるように)。
        this.execRenderScript(t, pass, partialTick);
    }

    /** 本家: TileEntityMachineBase.tick — ワールド時間で代替 */
    public int getTick(Object tile) {
        if (tile instanceof BlockEntity be && be.getLevel() != null) {
            return (int) be.getLevel().getGameTime();
        }
        return 0;

    }
    // ---- 本家 KaizPatchX TileEntityPartsRenderer.java:15-35 のスクリプト API ----

    /** 本家 getMetadata: 1.7.10 の数値メタデータ。1.21 ではブロック状態から復元する
     *  (レールは RailShape 列挙順 = 1.7.10 メタデータ 0..5 と一致)。 */
    public int getMetadata(Object tile) {
        if (tile instanceof net.minecraft.world.level.block.entity.BlockEntity be
                && be.getBlockState() != null
                && be.getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE)) {
            return ((net.minecraft.world.level.block.state.properties.RailShape)
                    be.getBlockState().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.RAIL_SHAPE)).ordinal();
        }
        return 0;
    }

    /** 本家 getWorld: entity.getWorldObj() 相当。 */
    @Override
    public net.minecraft.world.level.Level getWorld(Object tile) {
        if (tile instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            return be.getLevel();
        }
        return null;
    }

    /** 本家 getX: entity.xCoord 相当。 */
    public int getX(Object tile) {
        return tile instanceof net.minecraft.world.level.block.entity.BlockEntity be
            ? be.getBlockPos().getX() : 0;
    }

    /** 本家 getY: entity.yCoord 相当。 */
    public int getY(Object tile) {
        return tile instanceof net.minecraft.world.level.block.entity.BlockEntity be
            ? be.getBlockPos().getY() : 0;
    }

    /** 本家 getZ: entity.zCoord 相当。 */
    public int getZ(Object tile) {
        return tile instanceof net.minecraft.world.level.block.entity.BlockEntity be
            ? be.getBlockPos().getZ() : 0;
    }


    // ==== ★GraalJS 互換オーバーロード (tools/generate_graaljs_overloads.py が生成) ====
    /** ★GraalJS 互換オーバーロード (生成) */
    public void render(Object t, double pass, double partialTick) {
    this.render(t, (int) pass, (float) partialTick);
    }

}
