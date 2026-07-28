package jp.ngt.ngtlib.renderer;

/**
 * 本家 jp.ngt.ngtlib.renderer.NGTRenderer のスクリプト互換。
 * NGTO Builder の Prop/Liner ツールがミニチュア (NGTObject) のゴーストプレビューに使う。
 */
@SuppressWarnings("unused")
public final class NGTRenderer {
    private NGTRenderer() {
    }

    /** NGTObject のプレビュー描画 (isOldVer 分岐: renderNGTObject(ngto, true))。 */
    public static void renderNGTObject(Object ngto, boolean translucent) {
        GLRecorder rec = GLRecorder.active();
        if (rec == null || !(ngto instanceof jp.ngt.ngtlib.block.NGTObject obj)) {
            return;
        }
        for (jp.ngt.ngtlib.block.BlockSet set : obj.blockList) {
            if (set == null) {
                continue;
            }
            net.minecraft.world.level.block.state.BlockState state = set.getState();
            if (state == null || state.isAir()) {
                continue;
            }
            rec.renderBlock(state, set.x, Math.max(set.y, 0), set.z);
        }
    }

    /**
     * 本家 renderNGTObject(world, ngto, changeLighting, mode, pass)。
     * RTMU は world/mode/pass を使わず、ブロック列をそのまま記録する。
     */
    public static void renderNGTObject(Object world, Object ngto, boolean changeLighting, int mode, int pass) {
        renderNGTObject(ngto, changeLighting);
    }

    /**
     * 本家 renderPole: 半径 r・長さ length の円柱を tessellator へ積む。
     * 球テーブルの赤道 (行 64〜79) を輪切りとして使う。
     */
    public static void renderPole(Object tessellator, double r, double length, boolean useTexture) {
        if (!(tessellator instanceof NGTTessellator tess)) {
            return;
        }
        float[][] sp = ModelSolid.sphere;
        int steps = (int) (length * 16.0D);
        for (int l = 0; l < steps; ++l) {
            double y0 = l * 0.0625D;
            double y1 = y0 + 0.0625D;
            double minV = l * 0.0625D;
            double maxV = (l + 1) * 0.0625D;
            for (int i = 0; i < 16; ++i) {
                double minU = i * 0.0625D;
                double maxU = (i + 1) * 0.0625D;
                int a = 64 + i;
                int b = 64 + (i + 1) % 16;
                addPoleVertex(tess, useTexture, sp[a][0] * r, sp[a][1] * r + y0, sp[a][2] * r, maxU, maxV);
                addPoleVertex(tess, useTexture, sp[a][0] * r, sp[a][1] * r + y1, sp[a][2] * r, maxU, minV);
                addPoleVertex(tess, useTexture, sp[b][0] * r, sp[b][1] * r + y1, sp[b][2] * r, minU, minV);
                addPoleVertex(tess, useTexture, sp[b][0] * r, sp[b][1] * r + y0, sp[b][2] * r, minU, maxV);

                addPoleVertex(tess, useTexture, sp[b][0] * r, sp[b][1] * r + y0, sp[b][2] * r, maxU, maxV);
                addPoleVertex(tess, useTexture, sp[b][0] * r, sp[b][1] * r + y1, sp[b][2] * r, maxU, minV);
                addPoleVertex(tess, useTexture, sp[a][0] * r, sp[a][1] * r + y1, sp[a][2] * r, minU, minV);
                addPoleVertex(tess, useTexture, sp[a][0] * r, sp[a][1] * r + y0, sp[a][2] * r, minU, maxV);
            }
        }
    }

    private static void addPoleVertex(NGTTessellator tess, boolean useTexture,
                                      double x, double y, double z, double u, double v) {
        if (useTexture) {
            tess.addVertexWithUV(x, y, z, u, v);
        } else {
            tess.addVertex(x, y, z);
        }
    }

    /** 本家 renderSphere: 半径 r の球を tessellator へ積む (8 段 × 16 分割)。 */
    public static void renderSphere(Object tessellator, double r) {
        if (!(tessellator instanceof NGTTessellator tess)) {
            return;
        }
        float[][] sp = ModelSolid.sphere;
        for (int row = 0; row < 8; ++row) {
            for (int col = 0; col < 16; ++col) {
                double minU = col * 0.0625D;
                double maxU = (col + 1) * 0.0625D;
                double minV = row * 0.125D;
                double maxV = (row + 1) * 0.125D;
                int i0 = row * 16 + col;
                int i1 = (row + 1) * 16 + col;
                int i2 = (row + 1) * 16 + (col + 1) % 16;
                int i3 = row * 16 + (col + 1) % 16;

                tess.addVertexWithUV(sp[i0][0] * r, sp[i0][1] * r, sp[i0][2] * r, maxU, maxV);
                tess.addVertexWithUV(sp[i1][0] * r, sp[i1][1] * r, sp[i1][2] * r, maxU, minV);
                tess.addVertexWithUV(sp[i2][0] * r, sp[i2][1] * r, sp[i2][2] * r, minU, minV);
                tess.addVertexWithUV(sp[i3][0] * r, sp[i3][1] * r, sp[i3][2] * r, minU, maxV);

                tess.addVertexWithUV(sp[i3][0] * r, sp[i3][1] * r, sp[i3][2] * r, maxU, maxV);
                tess.addVertexWithUV(sp[i2][0] * r, sp[i2][1] * r, sp[i2][2] * r, maxU, minV);
                tess.addVertexWithUV(sp[i1][0] * r, sp[i1][1] * r, sp[i1][2] * r, minU, minV);
                tess.addVertexWithUV(sp[i0][0] * r, sp[i0][1] * r, sp[i0][2] * r, minU, maxV);
            }
        }
    }

    /** 本家 renderFrame: 直方体の 12 辺を線で描く (選択枠)。 */
    public static void renderFrame(double minX, double minY, double minZ,
                                   double width, double height, double depth, int color, int alpha) {
        double maxX = minX + width;
        double maxY = minY + height;
        double maxZ = minZ + depth;
        NGTTessellator t = NGTTessellator.instance;
        t.startDrawing(1);//GL_LINES
        t.setColorRGBA_I(color, alpha);

        edge(t, minX, minY, minZ, maxX, minY, minZ);
        edge(t, minX, minY, maxZ, maxX, minY, maxZ);
        edge(t, minX, minY, minZ, minX, minY, maxZ);
        edge(t, maxX, minY, minZ, maxX, minY, maxZ);

        edge(t, minX, minY, minZ, minX, maxY, minZ);
        edge(t, maxX, minY, minZ, maxX, maxY, minZ);
        edge(t, minX, minY, maxZ, minX, maxY, maxZ);
        edge(t, maxX, minY, maxZ, maxX, maxY, maxZ);

        edge(t, minX, maxY, minZ, maxX, maxY, minZ);
        edge(t, minX, maxY, maxZ, maxX, maxY, maxZ);
        edge(t, minX, maxY, minZ, minX, maxY, maxZ);
        edge(t, maxX, maxY, minZ, maxX, maxY, maxZ);

        t.draw();
    }

    private static void edge(NGTTessellator t, double x0, double y0, double z0, double x1, double y1, double z1) {
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y1, z1);
    }

    /**
     * 本家 renderBlock: RenderBlocks でブロックを描く。
     * 1.21 に RenderBlocks が無いので、BlockState を GLRecorder へ記録する形に読み替える。
     */
    public static void renderBlock(double x, double y, double z, double par4, double par5, double par6,
                                   Object renderer, Object block) {
        recordBlock(block, x, y, z);
    }

    public static void renderBlock(double x, double y, double z, double par4, double par5, double par6,
                                   Object renderer, Object block, int meta) {
        recordBlock(block, x, y, z);
    }

    public static void renderBlock(double x, double y, double z, double par4, double par5, double par6,
                                   boolean inWorld, Object renderer, Object block, int bx, int by, int bz) {
        recordBlock(block, x, y, z);
    }

    public static void renderBlock(double x, double y, double z, double par4, double par5, double par6,
                                   boolean inWorld, Object renderer, Object block, int bx, int by, int bz, int meta) {
        recordBlock(block, x, y, z);
    }

    private static void recordBlock(Object block, double x, double y, double z) {
        GLRecorder rec = GLRecorder.active();
        if (rec == null) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state = null;
        if (block instanceof net.minecraft.world.level.block.state.BlockState bs) {
            state = bs;
        } else if (block instanceof net.minecraft.world.level.block.Block b) {
            state = b.defaultBlockState();
        }
        if (state != null && !state.isAir()) {
            rec.renderBlock(state, (float) x, (float) y, (float) z);
        }
    }

    /**
     * 本家 renderTileEntities / renderEntities: NGTWorld 内の TE/エンティティを描く。
     * RTMU のミニチュアプレビューはブロックのみを描くため、実体は持たない。
     */
    public static void renderTileEntities(Object world, float partialTicks, int pass) {
    }

    public static void renderTileEntityByRenderer(Object tile, double x, double y, double z, float partialTicks, int pass) {
    }

    public static void renderEntities(Object world, float partialTicks, int pass) {
    }

    public static void renderEntityByRenderer(Object entity, float partialTicks, int pass) {
    }
}
