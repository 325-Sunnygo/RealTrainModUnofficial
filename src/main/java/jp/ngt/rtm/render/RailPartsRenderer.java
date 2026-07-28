package jp.ngt.rtm.render;

import jp.ngt.ngtlib.io.ScriptUtil;
import jp.ngt.rtm.rail.TileEntityLargeRailCore;
import jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore;

/**
 * 本家 jp.ngt.rtm.render.RailPartsRenderer の移植。
 * レールスクリプトの renderRailStatic/renderRailDynamic/shouldRenderObject を仲介する。
 */
public class RailPartsRenderer extends PartsRenderer {
    /** 現在描画中のレール index (0=メイン, 1..=subRails)。スクリプトがリフレクション参照。 */
    public int currentRailIndex;

    /**
     * 分岐コア用: renderStaticParts が描画する RailMap の差し替え。
     * getRailMap(null) は分岐で先頭マップしか返さないため、呼び出し側
     * (RailScriptRenderers) が getAllRailMaps の各マップを設定してマップごとに回す。
     */
    public jp.ngt.rtm.rail.util.RailMap renderMapOverride;

    public boolean isSwitchRail(Object tile) {
        return tile instanceof TileEntityLargeRailSwitchCore;
    }

    public void renderRailStatic(TileEntityLargeRailCore tile, double x, double y, double z, float partialTicks, int pass) {
        if (this.script != null) {
            ScriptUtil.doScriptIgnoreError(this.script, "renderRailStatic", tile, x, y, z, partialTicks, pass);
        }
    }

    public void renderRailDynamic(TileEntityLargeRailCore tile, double x, double y, double z, float partialTicks, int pass) {
        if (this.script != null) {
            ScriptUtil.doScriptIgnoreError(this.script, "renderRailDynamic", tile, x, y, z, partialTicks, pass);
        }
    }

    /** 通常パイプラインの各オブジェクトを描画するかどうか (端のトリミング等)。 */
    public boolean shouldRenderObject(TileEntityLargeRailCore tile, String objName, double len, double pos) {
        if (this.script == null) {
            return true;
        }
        Object result = ScriptUtil.doScriptIgnoreError(this.script, "shouldRenderObject", tile, objName, len, pos);
        if (result instanceof Boolean b) {
            return b;
        }
        return true;
    }

    /**
     * 描画対象モデルのオブジェクト名一覧 (renderStaticParts 用)。
     * クライアント側 (RailScriptRenderers) が呼出前に設定する。
     */
    public java.util.Set<String> modelGroupNames = java.util.Set.of();

    /**
     * 本家のデフォルトレール描画。
     * renderer.renderStaticParts(tileEntity, x, y, z) として呼ばれる。
     */
    /**
     * 本家 renderRailMapStatic の忠実移植 (GLRecorder 記録)。
     * BRE 系スクリプトが分岐の非可動側レールを描くのに使う:
     */
    public void renderRailMapStatic(Object tileObj, Object railMapObj, int max, int startIndex, int endIndex, Parts... pArray) {
        if (!(tileObj instanceof jp.ngt.rtm.rail.TileEntityLargeRailSwitchCore tileEntity)
                || !(railMapObj instanceof jp.ngt.rtm.rail.util.RailMap rm)) {
            return;
        }
        jp.ngt.ngtlib.renderer.GLRecorder rec = jp.ngt.ngtlib.renderer.GLRecorder.active();
        if (rec == null || max < 1) {
            return;
        }
        double[] origPos = rm.getRailPos(max, 0);
        double origHeight = rm.getRailHeight(max, 0);
        int[] startPos = tileEntity.getStartPoint();
        float[] revXZ = jp.ngt.rtm.rail.util.RailPosition.REVISION[tileEntity.getRailPositions()[0].direction];
        // レール全体の始点からの移動差分 (本家式)
        float moveX = (float) (origPos[1] - ((double) startPos[0] + 0.5D + (double) revXZ[0]));
        float moveZ = (float) (origPos[0] - ((double) startPos[2] + 0.5D + (double) revXZ[1]));

        for (int i = startIndex; i <= endIndex; i++) {
            double[] p1 = rm.getRailPos(max, i);
            double h = rm.getRailHeight(max, i);
            float x0 = moveX + (float) (p1[1] - origPos[1]);
            float y0 = (float) (h - origHeight);
            float z0 = moveZ + (float) (p1[0] - origPos[0]);
            float yaw = rm.getRailRotation(max, i);
            float pitch = rm.getRailPitch(max, i);
            rec.push();
            rec.translate(x0, y0, z0);
            rec.rotate(yaw, 0.0F, 1.0F, 0.0F);
            rec.rotate(-pitch, 1.0F, 0.0F, 0.0F);
            for (Parts parts : pArray) {
                if (parts != null) {
                    parts.render(this);
                }
            }
            rec.pop();
        }
    }

    /**
     * レール 1 区間の明るさ。レール面のすぐ上で拾う。
     * そこが完全に真っ暗 (= ブロックの中でサンプリングしてしまった) の時だけ 1〜2 段上へ
     * 逃がす。
     */
    private static int sampleBrightness(TileEntityLargeRailCore tile, double wx, double wy, double wz) {
        net.minecraft.world.level.Level level = tile.getLevel();
        if (level == null) {
            return 0;
        }
        net.minecraft.core.BlockPos sp = net.minecraft.core.BlockPos.containing(wx, wy + 0.25D, wz);
        int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, sp);
        if (light == 0) {
            light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, sp.above());
        }
        if (light == 0) {
            light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, sp.above(2));
        }
        return light;
    }

    /**
     * 本家 RailPartsRendererBase.createRailPos の忠実移植。
     * 全 RailMap の 0.5m 刻み各点を {x, y, z, yaw, -pitch, cant} で返す。
     */
    public static float[][] createRailPos(TileEntityLargeRailCore tile) {
        jp.ngt.rtm.rail.util.RailMap[] rms = tile.getAllRailMaps();
        if (rms == null || rms.length == 0) {
            return null;
        }
        float[] rev = jp.ngt.rtm.rail.util.RailPosition.REVISION[tile.getRailPositions()[0].direction];
        int[] start = tile.getStartPoint();
        java.util.List<float[]> list = new java.util.ArrayList<>();
        for (jp.ngt.rtm.rail.util.RailMap rm : rms) {
            if (rm == null) {
                continue;
            }
            int max = (int) (rm.getLength() * 2.0D);
            if (max < 1) {
                max = 1;
            }
            double[] stPoint = rm.getRailPos(max, 0);
            double startH = rm.getStartRP().posY;
            float moveX = (float) (stPoint[1] - ((double) start[0] + 0.5D + (double) rev[0]));
            float moveZ = (float) (stPoint[0] - ((double) start[2] + 0.5D + (double) rev[1]));
            for (int i = 0; i <= max; ++i) {
                double[] cur = rm.getRailPos(max, i);
                list.add(new float[]{
                    moveX + (float) (cur[1] - stPoint[1]),
                    (float) (rm.getRailHeight(max, i) - startH),
                    moveZ + (float) (cur[0] - stPoint[0]),
                    rm.getRailRotation(max, i),
                    -rm.getRailPitch(max, i),
                    rm.getCant(max, i)});
            }
        }
        return list.isEmpty() ? null : list.toArray(new float[0][]);
    }

    /**
     * 本家 getRailBrightness の忠実移植。各点の位置で明るさを拾う。
     * ★コアブロック 1 点で代表させてはいけない。
     */
    public static int[] getRailBrightness(net.minecraft.world.level.Level level,
                                          net.minecraft.core.BlockPos origin, float[][] rp) {
        int[] out = new int[rp.length];
        if (level == null) {
            return out;
        }
        for (int i = 0; i < rp.length; i++) {
            int x = origin.getX() + net.minecraft.util.Mth.floor(rp[i][0]);
            int y = origin.getY() + net.minecraft.util.Mth.floor(rp[i][1]);
            int z = origin.getZ() + net.minecraft.util.Mth.floor(rp[i][2]);
            out[i] = brightnessAt(level, x, y, z);
        }
        return out;
    }

    /** 本家 getBrightness: 0 なら 1 段上へ逃がす。 */
    private static int brightnessAt(net.minecraft.world.level.Level level, int x, int y, int z) {
        net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(x, y, z);
        int b = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, p);
        if (b <= 0) {
            b = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, p.above());
        }
        return b;
    }

    /**
     * 本家 KaizPatchX createStaticRenderKey の忠実移植。
     * レール形状・各点の明るさ・モデル (オブジェクト名/テクスチャ) をまとめた 1 個の int。
     */
    public static int createStaticRenderKey(float[][] fa, int[] brightness, int modelKey) {
        int key = 31 * java.util.Arrays.deepHashCode(fa) + java.util.Arrays.hashCode(brightness);
        return 31 * key + modelKey;
    }

    public void renderStaticParts(Object tileObj, double x, double y, double z) {
        if (!(tileObj instanceof TileEntityLargeRailCore tile)) {
            return;
        }
        jp.ngt.ngtlib.renderer.GLRecorder rec = jp.ngt.ngtlib.renderer.GLRecorder.active();
        if (rec == null) {
            return;
        }
        // ★本家 createRailPos と同じく全 RailMap を 1 回の呼び出しで回す。
        // 分岐は RailMap を複数持つので、1 本しか見ないと分岐側のルートに土台 (道床/枕木) が
        // 付かない。
        jp.ngt.rtm.rail.util.RailMap[] maps;
        if (this.renderMapOverride != null) {
            maps = new jp.ngt.rtm.rail.util.RailMap[]{this.renderMapOverride};
        } else {
            maps = tile.getAllRailMaps();
            if (maps == null || maps.length == 0) {
                jp.ngt.rtm.rail.util.RailMap single = tile.getRailMap(null);
                if (single == null) {
                    return;
                }
                maps = new jp.ngt.rtm.rail.util.RailMap[]{single};
            }
        }
        net.minecraft.core.BlockPos origin = tile.getBlockPos();
        for (jp.ngt.rtm.rail.util.RailMap map : maps) {
        if (map == null) {
            continue;
        }
        double length = map.getLength();
        int max = (int) Math.floor(length * 2.0D);
        if (max < 1) {
            max = 1;
        }
        for (int i = 0; i <= max; i++) {
            java.util.Set<String> allowed = new java.util.LinkedHashSet<>();
            for (String name : this.modelGroupNames) {
                if (this.shouldRenderObject(tile, name, max, i)) {
                    allowed.add(name.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
            if (allowed.isEmpty()) {
                continue;
            }
            double[] p1 = map.getRailPos(max, i);
            double h = map.getRailHeight(max, i);
            float yaw = map.getRailYaw(max, i);
            float pitch = map.getRailPitch(max, i);
            float roll = map.getRailRoll(max, i);

            float relX = (float) (x + p1[1] - origin.getX());
            float relY = (float) (y + h - origin.getY() - 0.0625D);
            float relZ = (float) (z + p1[0] - origin.getZ());

            // 本家 renderRailMapStatic と同じく、明るさは区間ごとにサンプリングする。
            // ★コアブロック 1 点で全長を代表させてはいけない。
            rec.brightness(sampleBrightness(tile, origin.getX() + relX, origin.getY() + relY, origin.getZ() + relZ));
            rec.push();
            rec.translate(relX, relY, relZ);
            rec.rotate(yaw, 0.0F, 1.0F, 0.0F);
            rec.rotate(-pitch, 1.0F, 0.0F, 0.0F);
            rec.rotate(roll, 0.0F, 0.0F, 1.0F);
            rec.renderGroups(allowed);
            rec.pop();
        }
        }
    }
}
