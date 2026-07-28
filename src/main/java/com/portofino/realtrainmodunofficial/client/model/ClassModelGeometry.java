package com.portofino.realtrainmodunofficial.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.MinecartModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * 本家 RTM が Java で持っている車両モデル (*.class) の移植。
 * 本家は jp.ngt.rtm.modelpack.model.RTMClassModels に
 * ModelBogie / ModelTrain_kiha600 / ModelTrain_Minecart を持ち、
 * モデルパックの "modelFile": "ModelBogie.class" からこれを引いている。
 */
public final class ClassModelGeometry {

    /** 1 頂点あたり x, y, z, nx, ny, nz, u, v。MqoModelLoader.Batch と同じ並び。 */
    public static final int STRIDE = 8;

    private ClassModelGeometry() {
    }

    /** 移植済みの *.class モデルか。 */
    public static boolean isSupported(String modelFile) {
        return builtinName(modelFile) != null;
    }

    /** 既定テクスチャ (パック側の textures 指定が無い場合)。 */
    public static String defaultTexture(String modelFile) {
        String name = builtinName(modelFile);
        if (name == null) {
            return null;
        }
        return switch (name) {
            case "modelbogie.class" -> "textures/train/bogie.png";
            case "modeltrain_kiha600.class" -> "textures/train/kiha600/kiha600.png";
            // バニラの minecart テクスチャ。本家もこれを使う。
            case "modeltrain_minecart.class" -> "minecraft:textures/entity/minecart.png";
            default -> null;
        };
    }

    /** モデルの表示名 (バッチのグループ名)。スクリプトからの参照は無いので固定で良い。 */
    public static String groupName(String modelFile) {
        String name = builtinName(modelFile);
        if (name == null) {
            return "body";
        }
        return name.startsWith("modelbogie") ? "bogie" : "body";
    }

    /** 四角形 (4 頂点/面) の頂点配列を組み立てる。未対応なら null。 */
    public static float[] build(String modelFile) {
        String name = builtinName(modelFile);
        if (name == null) {
            return null;
        }
        return switch (name) {
            case "modelbogie.class" -> capture(bakeBogie(), 1.0F, false);
            case "modeltrain_kiha600.class" -> capture(bakeKiha600(), 1.0F, true);
            // 本家 ModelTrain_Minecart は 0.1875 スケール = 1/16 の 3 倍。
            case "modeltrain_minecart.class" -> capture(MinecartModel.createBodyLayer().bakeRoot(), 3.0F, true);
            default -> null;
        };
    }

    private static String builtinName(String modelFile) {
        if (modelFile == null || modelFile.isBlank()) {
            return null;
        }
        String normalized = modelFile.replace('\\', '/').trim().toLowerCase(Locale.ROOT);
        int slash = normalized.lastIndexOf('/');
        String leaf = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return switch (leaf) {
            case "modelbogie.class", "modeltrain_kiha600.class", "modeltrain_minecart.class" -> leaf;
            default -> null;
        };
    }

    /**
     * バニラに焼かせた ModelPart を描かせて頂点を掴む。
     * @param extraScale 追加スケール (minecart の 3 倍など)
     * @param rotateY90  車体側の glRotatef(90, 0, 1, 0) 相当を掛けるか
     */
    private static float[] capture(ModelPart root, float extraScale, boolean rotateY90) {
        PoseStack pose = new PoseStack();
        // 本家 MCModel.renderAll の glScalef(1, -1, -1)。X 軸 180° 回転と同じで裏表は反転しない。
        pose.scale(extraScale, -extraScale, -extraScale);
        if (rotateY90) {
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0F));
        }
        Capture capture = new Capture();
        root.render(pose, capture, 0, 0);
        return capture.toArray();
    }

    private static ModelPart bakeKiha600() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 本家 ModelTrain_kiha600.init。テクスチャは 1024x1024。
        addBox(root, "yukasita", 0, 208, -158.0F, 0.0F, -21.0F, 316, 16, 42);
        addBox(root, "yukasita2", 0, 351, -157.0F, 0.0F, -20.0F, 314, 10, 40);
        addBox(root, "yuka", 0, 266, -159.0F, -41.0F, -22.0F, 318, 41, 44);
        addBox(root, "syatai", 0, 0, -160.0F, -41.0F, -23.0F, 320, 41, 46);
        addBox(root, "yane1", 0, 87, -160.0F, -42.0F, -22.0F, 320, 1, 44);
        addBox(root, "yane2", 0, 132, -160.0F, -43.0F, -20.0F, 320, 1, 40);
        addBox(root, "yane3", 0, 173, -160.0F, -44.0F, -17.0F, 320, 1, 34);
        return LayerDefinition.create(mesh, 1024, 1024).bakeRoot();
    }

    private static ModelPart bakeBogie() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 本家 ModelBogie.init。全 44 箱・回転は全てゼロ・テクスチャは 256x256。
        int[] i = {0};
        for (float[] b : BOGIE_BOXES) {
            root.addOrReplaceChild("p" + i[0]++,
                CubeListBuilder.create()
                    .texOffs((int) b[0], (int) b[1])
                    .addBox(b[2], b[3], b[4], b[5], b[6], b[7]),
                PartPose.offset(b[8], b[9], b[10]));
        }
        return LayerDefinition.create(mesh, 256, 256).bakeRoot();
    }

    private static void addBox(PartDefinition root, String name, int tu, int tv,
                               float x, float y, float z, int w, int h, int d) {
        root.addOrReplaceChild(name,
            CubeListBuilder.create().texOffs(tu, tv).addBox(x, y, z, w, h, d),
            PartPose.ZERO);
    }

    /** {tu, tv, x, y, z, w, h, d, posX, posY, posZ} */
    private static final float[][] BOGIE_BOXES = {
        {0, 0, -8, 0, -24, 16, 9, 48, 0, 4, 0},
        {0, 60, 0, 0, -25, 4, 9, 50, 10, 4, 0},
        {0, 60, -4, 0, -25, 4, 9, 50, -10, 4, 0},
        {130, 20, -5, 0, -7, 5, 12, 14, 19, 2, 0},
        {130, 20, 0, 0, -7, 5, 12, 14, -19, 2, 0},
        {130, 0, -20, 0, -7, 40, 2, 14, 0, 0, 0},
        {170, 20, -1, 1, -8, 2, 2, 8, 0, 0, -37},
        {170, 32, -3, 0, -12, 6, 4, 4, 0, 0, -37},
        {0, 0, -1, -6, -2, 1, 12, 4, 10, 10, -18},
        {0, 0, -1, -5, -4, 1, 10, 8, 10, 10, -18},
        {0, 0, -1, -4, -5, 1, 8, 10, 10, 10, -18},
        {0, 0, -1, -2, -6, 1, 4, 12, 10, 10, -18},
        {0, 0, -1, -7, -2, 1, 14, 4, 9, 10, -18},
        {0, 0, -1, -6, -4, 1, 12, 8, 9, 10, -18},
        {0, 0, -1, -5, -5, 1, 10, 10, 9, 10, -18},
        {0, 0, -1, -4, -6, 1, 8, 12, 9, 10, -18},
        {0, 0, -1, -2, -7, 1, 4, 14, 9, 10, -18},
        {0, 0, -1, -6, -2, 1, 12, 4, 10, 10, 18},
        {0, 0, -1, -5, -4, 1, 10, 8, 10, 10, 18},
        {0, 0, -1, -4, -5, 1, 8, 10, 10, 10, 18},
        {0, 0, -1, -2, -6, 1, 4, 12, 10, 10, 18},
        {0, 0, -1, -7, -2, 1, 14, 4, 9, 10, 18},
        {0, 0, -1, -6, -4, 1, 12, 8, 9, 10, 18},
        {0, 0, -1, -5, -5, 1, 10, 10, 9, 10, 18},
        {0, 0, -1, -4, -6, 1, 8, 12, 9, 10, 18},
        {0, 0, -1, -2, -7, 1, 4, 14, 9, 10, 18},
        {0, 0, 0, -6, -2, 1, 12, 4, -10, 10, -18},
        {0, 0, 0, -5, -4, 1, 10, 8, -10, 10, -18},
        {0, 0, 0, -4, -5, 1, 8, 10, -10, 10, -18},
        {0, 0, 0, -2, -6, 1, 4, 12, -10, 10, -18},
        {0, 0, 0, -7, -2, 1, 14, 4, -9, 10, -18},
        {0, 0, 0, -6, -4, 1, 12, 8, -9, 10, -18},
        {0, 0, 0, -5, -5, 1, 10, 10, -9, 10, -18},
        {0, 0, 0, -4, -6, 1, 8, 12, -9, 10, -18},
        {0, 0, 0, -2, -7, 1, 4, 14, -9, 10, -18},
        {0, 0, 0, -6, -2, 1, 12, 4, -10, 10, 18},
        {0, 0, 0, -5, -4, 1, 10, 8, -10, 10, 18},
        {0, 0, 0, -4, -5, 1, 8, 10, -10, 10, 18},
        {0, 0, 0, -2, -6, 1, 4, 12, -10, 10, 18},
        {0, 0, 0, -7, -2, 1, 14, 4, -9, 10, 18},
        {0, 0, 0, -6, -4, 1, 12, 8, -9, 10, 18},
        {0, 0, 0, -5, -5, 1, 10, 10, -9, 10, 18},
        {0, 0, 0, -4, -6, 1, 8, 12, -9, 10, 18},
        {0, 0, 0, -2, -7, 1, 4, 14, -9, 10, 18},
    };

    /**
     * ModelPart#render が投げてくる頂点を掴むだけの VertexConsumer。
     * Cube#compile は 1 面 4 頂点を順に流すので、そのまま四角形として溜まる。
     */
    private static final class Capture implements VertexConsumer {
        private final List<float[]> vertices = new ArrayList<>();
        private float[] current;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.current = new float[STRIDE];
            this.current[0] = x;
            this.current[1] = y;
            this.current[2] = z;
            this.vertices.add(this.current);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            if (this.current != null) {
                float length = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
                if (length > 1.0E-5F) {
                    normalX /= length;
                    normalY /= length;
                    normalZ /= length;
                } else {
                    normalX = 0.0F;
                    normalY = 1.0F;
                    normalZ = 0.0F;
                }
                this.current[3] = normalX;
                this.current[4] = normalY;
                this.current[5] = normalZ;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (this.current != null) {
                this.current[6] = u;
                this.current[7] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        float[] toArray() {
            // 4 頂点 1 面。端数が出たら面として成立しないので切り捨てる。
            int faces = this.vertices.size() / 4;
            float[] out = new float[faces * 4 * STRIDE];
            for (int i = 0; i < faces * 4; i++) {
                System.arraycopy(this.vertices.get(i), 0, out, i * STRIDE, STRIDE);
            }
            return out;
        }
    }
}
