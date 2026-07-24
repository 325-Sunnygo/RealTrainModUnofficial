package jp.ngt.rtm.render;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 本家 jp.ngt.rtm.render.MachinePartsRenderer (KaizPatchX) の移植。
 * 踏切等の設置物スクリプト (RenderCrossingGate01.js 等) が renderClass に指定する。
 */
public class MachinePartsRenderer extends TileEntityPartsRenderer {

    public MachinePartsRenderer(String... par1) {
        super(par1);
    }

    /**
     * 本家: 踏切=barMoveCount/90 (0..1)、改札=通行可否
     */
    public float getMovingCount(Object tile) {
        if (tile instanceof InstalledObjectBlockEntity be) {
            if (be.getCategory() == InstalledObjectCategory.CROSSING) {
                return (float) be.getBarMoveCount() / 90.0F;
            }
            if (be.getCategory() == InstalledObjectCategory.TICKET_GATE) {
                //本家 MachinePartsRenderer: canThrough() ? 0 : 1 — 通れる(開)=0, 閉=1。
                //MQO のデフォルトポーズが「開」で、state>0 の回転で「閉」になる。
                return be.isTicketGateOpen() ? 0.0F : 1.0F;
            }
            //本家 MachinePartsRenderer: 転轍機は isActivated() ? 1 : 0。
            //RenderPoint01.js が (count * 60 - 30) 度だけレバーを X 軸回転させるので、
            //OFF で -30 度、ON で +30 度に倒れる。
            if (be.getCategory() == InstalledObjectCategory.POINT) {
                return be.isPointActivated() ? 1.0F : 0.0F;
            }
        }
        return 0.0F;
    }

    /**
     * 本家: -1:OFF, 0 or 1:ON (踏切の交互点滅) / 照明は電源状態
     */
    public int getLightState(Object tile) {
        if (tile instanceof InstalledObjectBlockEntity be) {
            if (be.getCategory() == InstalledObjectCategory.CROSSING) {
                return be.getLightCount();
            }
            if (be.getCategory() == InstalledObjectCategory.LIGHT) {
                return be.isPowered() ? 1 : -1;
            }
        }
        return -1;
    }

    /**
     * 本家 MachinePartsRenderer.getLodState: 転轍機だけ move の符号を返す (move &gt; 0 ? 1 : -1)。
     * <p>
     * RenderPoint01.js は state &lt; 0 のとき本体 (body3) を +2.75 ずらす。つまりこれは LOD ではなく
     * 「転轍機が線路のどちら側に付くか」の切り替えで、バールの右クリックで符号が反転する。
     */
    public int getLodState(Object tile) {
        if (tile instanceof InstalledObjectBlockEntity be
                && be.getCategory() == InstalledObjectCategory.POINT) {
            return be.getPointMove() > 0.0F ? 1 : -1;
        }
        return 0;
    }

    /**
     * BER 側で回転適用済みのため 0 (本家は TE の向きをここで返す)
     */
    public float getPitch(Object tile) {
        return 0.0F;
    }

    public float getYaw(Object tile) {
        return 0.0F;
    }

    /** 本家getTick。 */
    public int getTick(Object tile) {
        markTimeAccessed();
        if (tile instanceof com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity io
                && io.getLevel() != null) {
            return (int) (io.getLevel().getGameTime() & 0x7FFFFFFF);
        }
        return 0;
    }

    /** 本家rotateVec: 設置向きに応じてベクトルを回す。 */
    public static void rotateVec(jp.ngt.ngtlib.math.Vec3 vec, int dir, float pitch, float yaw) {
        switch (dir) {
            case 0 -> {
                vec.rotateAroundX(jp.ngt.ngtlib.math.NGTMath.toRadians(pitch));
                vec.rotateAroundY(jp.ngt.ngtlib.math.NGTMath.toRadians(yaw));
            }
            case 1 -> {
                vec.rotateAroundX(jp.ngt.ngtlib.math.NGTMath.toRadians(-pitch));
                vec.rotateAroundY(jp.ngt.ngtlib.math.NGTMath.toRadians(yaw));
            }
            case 2 -> vec.rotateAroundX(jp.ngt.ngtlib.math.NGTMath.toRadians(pitch));
            case 3 -> vec.rotateAroundX(jp.ngt.ngtlib.math.NGTMath.toRadians(-pitch));
            case 4 -> vec.rotateAroundZ(jp.ngt.ngtlib.math.NGTMath.toRadians(pitch));
            case 5 -> vec.rotateAroundZ(jp.ngt.ngtlib.math.NGTMath.toRadians(-pitch));
            default -> {
            }
        }
    }

    /** 本家getNormal: 機器の向きベクトル。 */
    public jp.ngt.ngtlib.math.Vec3 getNormal(Object tile, float x, float y, float z, float pitch, float yaw) {
        jp.ngt.ngtlib.math.Vec3 vec = new jp.ngt.ngtlib.math.Vec3(x, y, z);
        vec.rotateAroundX(jp.ngt.ngtlib.math.NGTMath.toRadians(pitch));
        vec.rotateAroundY(jp.ngt.ngtlib.math.NGTMath.toRadians(yaw));
        return vec;
    }

    /** 本家getLightPos: 機器のライト位置(ワールド座標)。 */
    public double[] getLightPos(Object tile, float x, float y, float z, float pitch, float yaw) {
        if (!(tile instanceof BlockEntity be)) {
            return new double[3];
        }
        jp.ngt.ngtlib.math.Vec3 vec = new jp.ngt.ngtlib.math.Vec3(x, y, z);
        rotateVec(vec, 0, pitch, yaw);
        return new double[]{
            be.getBlockPos().getX() + 0.5D + vec.getX(),
            be.getBlockPos().getY() + 0.5D + vec.getY(),
            be.getBlockPos().getZ() + 0.5D + vec.getZ()};
    }

}
