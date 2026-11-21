package jp.ngt.rtm.entity.fluid;

import jp.ngt.ngtlib.renderer.ModelSolid;

/**
 * 流体 1 粒の表面頂点。本家 {@code jp.ngt.rtm.entity.fluid.FluidVertexHolder} の移植。
 *
 * <p>近くの粒へ向かって頂点を膨らませることで、粒どうしが繋がって見える (メタボール)。
 */
public final class FluidVertexHolder {
    public static final int SPLIT_H = 4;
    public static final int SPLIT_W = 8;
    /** [vtx][x, y, z, color] */
    public final float[][] buffer = new float[(SPLIT_H + 1) * SPLIT_W][4];

    public void update(EntityFluid fluid) {
        boolean isSolid = (fluid.getFluidType().type == FluidType.Type.SOLID);
        int splitW = isSolid ? 4 : 8;
        for (int i = 0; i < SPLIT_H; ++i) {          //縦
            for (int j = 0; j < splitW; ++j) {       //横
                int i2 = (i + 1) * 2;
                int j3 = j * (16 / splitW);
                this.addVertex(fluid, (i + 1) * SPLIT_W + j, ModelSolid.sphere[i2 * 16 + j3]);
                i2 = i * 2;
                this.addVertex(fluid, i * SPLIT_W + j, ModelSolid.sphere[i2 * 16 + j3]);
            }
        }
    }

    private void addVertex(EntityFluid entity, int bufIndex, float[] vtx) {
        float scale = EntityFluid.R;
        double thresholdSq = (EntityFluid.SIZE * 1.2D) * (EntityFluid.SIZE * 1.2D);
        float metaballCoef = scale * 0.2F;

        float orgX = vtx[0] * scale;
        float orgY = vtx[1] * scale;
        float orgZ = vtx[2] * scale;
        float x = orgX;
        float y = orgY;
        float z = orgZ;

        if (entity.getFluidType().type != FluidType.Type.SOLID) {
            for (int i = 0; i < entity.nearFluids.size(); ++i) {
                EntityFluid target = entity.nearFluids.get(i);
                double dx = target.getX() - (entity.getX() + orgX);
                double dy = target.getY() - (entity.getY() + orgY);
                double dz = target.getZ() - (entity.getZ() + orgZ);
                double distanceSq = dx * dx + dy * dy + dz * dz;
                if (distanceSq < thresholdSq) {
                    double d0 = metaballCoef / distanceSq;
                    //normal * (1/距離) * 係数
                    x += (target.getX() - entity.getX()) * d0;
                    y += (target.getY() - entity.getY()) * d0;
                    z += (target.getZ() - entity.getZ()) * d0;
                }
            }

            double len = Math.sqrt(x * x + y * y + z * z);
            if (len > EntityFluid.SIZE) {   //サイズの急激な変化防止
                x *= EntityFluid.SIZE / len;
                y *= EntityFluid.SIZE / len;
                z *= EntityFluid.SIZE / len;
            }
        }

        float colorF = ((y / scale) + 1.0F) * 0.5F;
        colorF = colorF < 0.0F ? 0.0F : (Math.min(colorF, 1.0F));   //0.0~1.0
        if (entity.posDif > 0.0F) {
            float f0 = entity.posDif / EntityFluid.R;
            f0 = (Math.min(f0, 1.0F)) * 0.9F;
            colorF = colorF * (1.0F - f0) + f0;
        }

        this.buffer[bufIndex][0] = x;
        this.buffer[bufIndex][1] = y;
        this.buffer[bufIndex][2] = z;
        this.buffer[bufIndex][3] = colorF;
    }
}
