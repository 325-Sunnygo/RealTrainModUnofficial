package jp.ngt.rtm.render;

import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.entity.vehicle.EntityVehicleBase;

/**
 * 本家 jp.ngt.rtm.render.VehiclePartsRenderer の忠実移植。
 * パックの車両スクリプトが renderClass に指定する。
 */
public class VehiclePartsRenderer extends EntityPartsRenderer {
    /**
     * 台車の場合はfalse
     */
    private boolean isvehicle;

    public VehiclePartsRenderer(String... par1) {
        super(par1);
        if (par1 != null && par1.length >= 1) {
            if ("true".equals(par1[0])) {
                this.isvehicle = true;
            } else if ("false".equals(par1[0])) {
                this.isvehicle = false;
            }
        }
    }

    /**
     * 本家 PartsRenderer.render: スクリプトの render(entity, pass, partialTick) を実行。
     * PICK パス (マウス操作) は未移植。
     */
    public void render(Object t, int pass, float partialTick) {
        this.currentPass = pass;
        //スクリプトが落ちたらフラグを立てる (呼び出し側が素のモデル描画へ戻せるように)。
        //黙って握りつぶすと「車体が丸ごと消える」ため。PartsRenderer.execRenderScript 参照。
        this.execRenderScript(t, pass, partialTick);
    }

    public float getWheelRotationR(Object entity) {
        if (entity == null) {
            return 0.0F;
        }
        if (!this.isvehicle && entity instanceof EntityBogie bogie) {
            EntityTrainBase train = bogie.getTrain();
            if (train != null) {
                return train.wheelRotationR * (bogie.getBogieId() == 0 ? 1.0F : -1.0F);
            }
        }
        return entity instanceof EntityVehicleBase<?> v ? v.wheelRotationR : 0.0F;
    }

    public float getWheelRotationL(Object entity) {
        if (entity == null) {
            return 0.0F;
        }
        if (!this.isvehicle && entity instanceof EntityBogie bogie) {
            EntityTrainBase train = bogie.getTrain();
            if (train != null) {
                return train.wheelRotationL * (bogie.getBogieId() == 0 ? 1.0F : -1.0F);
            }
        }
        return entity instanceof EntityVehicleBase<?> v ? v.wheelRotationL : 0.0F;
    }

    public float getDoorMovementR(Object entity) {
        if (!(entity instanceof EntityVehicleBase<?> v) || !this.isvehicle) {
            return 0.0F;
        }
        return (float) v.doorMoveR / (float) EntityVehicleBase.MAX_DOOR_MOVE;
    }

    public float getDoorMovementL(Object entity) {
        if (!(entity instanceof EntityVehicleBase<?> v) || !this.isvehicle) {
            return 0.0F;
        }
        return (float) v.doorMoveL / (float) EntityVehicleBase.MAX_DOOR_MOVE;
    }

    public float getPantographMovementFront(Object entity) {
        if (!(entity instanceof EntityVehicleBase<?> v) || !this.isvehicle) {
            return 0.0F;
        }
        return (float) v.pantograph_F / (float) EntityVehicleBase.MAX_PANTOGRAPH_MOVE;
    }

    public float getPantographMovementBack(Object entity) {
        if (!(entity instanceof EntityVehicleBase<?> v) || !this.isvehicle) {
            return 0.0F;
        }
        return (float) v.pantograph_B / (float) EntityVehicleBase.MAX_PANTOGRAPH_MOVE;
    }
}
