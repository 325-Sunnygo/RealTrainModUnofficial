package com.portofino.rtmupassenger.entity;

import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import jp.ngt.ngtlib.io.NGTFileLoader;
import jp.ngt.ngtlib.renderer.model.Face;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.ngtlib.renderer.model.ModelLoader;
import jp.ngt.ngtlib.renderer.model.PolygonModel;
import jp.ngt.ngtlib.renderer.model.Vertex;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 列車モデルの<b>実際のドア位置</b> (車体ローカル座標) を求める。
 *
 * <p>乗客 NPC を停止位置目標ではなく「本当のドア」に吸い付かせるために使う。ドアの位置は
 * 車両定義の {@code door_left/door_right} の {@code pos} が (0,0,0) で入っていないため使えず、
 * ドアのモデルグループ ({@code objects}) の<b>頂点重心</b>から算出する。モデル解析は純粋な
 * 幾何処理でサーバー側でも安全。車種ごとに一度だけ計算してキャッシュする。
 *
 * <p>返す座標は {@link EntityTrainBase#localToWorldVec} が受け取る車体ローカル系 (x=幅, y=高, z=長)。
 * 描画は model 座標に modelOffset+modelScale を掛けるので、ここでも同じ変換を適用して合わせる。
 */
public final class TrainDoorLocator {

    private static final Map<String, List<float[]>> CACHE = new ConcurrentHashMap<>();
    private static final List<float[]> NONE = List.of();

    private TrainDoorLocator() {
    }

    /** 列車の全ドアの車体ローカル位置 {x,y,z} のリスト。ドアが無い/取れない車種は空。 */
    public static List<float[]> doorsLocal(EntityTrainBase train) {
        String id = train.getModelName();
        if (id == null || id.isBlank()) {
            return NONE;
        }
        return CACHE.computeIfAbsent(id, TrainDoorLocator::compute);
    }

    /**
     * ワールド座標 (rx,rz) に最も近いドアの車体ローカル位置を返す。無ければ null。
     * 停止位置目標に一番近いドア = ホーム側のドア、を選ぶのに使う。
     */
    public static float[] nearestDoorLocal(EntityTrainBase train, double rx, double rz) {
        List<float[]> doors = doorsLocal(train);
        if (doors.isEmpty()) {
            return null;
        }
        float[] best = null;
        double bestSq = Double.MAX_VALUE;
        for (float[] d : doors) {
            Vec3 w = train.localToWorldVec(d[0], d[1], d[2]);
            double dx = w.x - rx;
            double dz = w.z - rz;
            double sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = d;
            }
        }
        return best == null ? null : best.clone();
    }

    private static List<float[]> compute(String id) {
        try {
            VehicleDefinition def = VehicleRegistry.getById(id);
            if (def == null) {
                com.portofino.rtmupassenger.PassengerMod.LOGGER.info(
                        "[PsgDiag] door detect: NO DEF for id='{}'", id);
                return NONE;
            }
            List<VehicleDefinition.DoorAnimationDefinition> doors = new ArrayList<>();
            if (def.getLeftDoors() != null) {
                doors.addAll(def.getLeftDoors());
            }
            if (def.getRightDoors() != null) {
                doors.addAll(def.getRightDoors());
            }
            PolygonModel model = loadModel(def);
            if (model == null || model.groupObjects == null || model.groupObjects.isEmpty()) {
                com.portofino.rtmupassenger.PassengerMod.LOGGER.info(
                        "[PsgDiag] door detect: model NOT LOADED for '{}' (file={}, doors defined={})",
                        id, def.getModelFile(), doors.size());
                return NONE;
            }
            Vec3 off = def.getModelOffset();
            float scale = def.getModelScale();
            List<float[]> result = new ArrayList<>();
            String source;
            if (!doors.isEmpty()) {
                //JSON にドア定義がある車両: そのドアグループ (objects) の重心を使う (正確)。
                source = "json";
                for (VehicleDefinition.DoorAnimationDefinition door : doors) {
                    float[] c = centroidOfGroups(model, door.objects());
                    if (c != null) {
                        result.add(toLocal(c, off, scale));
                    }
                }
            } else {
                //スクリプト制御でドア定義が無い車両 (E257 等): モデルから「ドア」グループを名前で探す。
                source = "model-scan";
                result = scanDoorsFromModel(model, off, scale);
            }
            com.portofino.rtmupassenger.PassengerMod.LOGGER.info(
                    "[PsgDiag] door detection for '{}': {} doors found (source={}, model={})",
                    id, result.size(), source, def.getModelFile());
            return result.isEmpty() ? NONE : List.copyOf(result);
        } catch (Throwable t) {
            com.portofino.rtmupassenger.PassengerMod.LOGGER.info(
                    "[PsgDiag] door detection FAILED for '{}': {}", id, String.valueOf(t));
            return NONE;
        }
    }

    private static float[] toLocal(float[] c, Vec3 off, float scale) {
        return new float[]{
            (float) off.x + scale * c[0],
            (float) off.y + scale * c[1],
            (float) off.z + scale * c[2]};
    }

    //「door」を含むが実際のスライドドアではない部品を弾く語 (ランプ/窓/取っ手/内装パネル/前面扉 等)。
    private static final String[] DOOR_BLOCKLIST = {
        "lamp", "window", "knob", "pole", "handle", "hundle", "panel", "frame", "joint",
        "glass", "[obj]", "sign", "switch", "light", "led", "silver", "cover", "front",
        "cab", "step", "guide", "mark", "hood", "roof", "floor", "ceiling", "seat", "hook",
        "rail", "motor", "box", "gangway", "renketsu", "huck", "stopper"
    };

    /**
     * JSON にドア定義が無い (スクリプト制御) 車両向け: モデルから<b>名前に「door」を含む外板の
     * スライドドアグループ</b>を探し、開口ごとにまとめて位置を返す。
     * <ol>
     *   <li>名前に door を含み、ランプ/窓/取っ手/内装等の語を含まないグループの重心を集める</li>
     *   <li>車体側面 (|x| が大きい方) のものだけ残す (内装扉・中央通路扉を除外)</li>
     *   <li>近いもの同士 (同じ扉の複数リーフ o/i・a/b) を 1 つにまとめる</li>
     * </ol>
     */
    private static List<float[]> scanDoorsFromModel(PolygonModel model, Vec3 off, float scale) {
        //① door を含む非除外グループの重心を<b>車体ローカル</b>で集める。
        List<float[]> local = new ArrayList<>();
        for (GroupObject g : model.groupObjects) {
            if (g.name == null || g.faces.isEmpty()) {
                continue;
            }
            String low = g.name.toLowerCase(java.util.Locale.ROOT);
            if (!low.contains("door")) {
                continue;
            }
            boolean blocked = false;
            for (String b : DOOR_BLOCKLIST) {
                if (low.contains(b)) {
                    blocked = true;
                    break;
                }
            }
            if (blocked) {
                continue;
            }
            float[] c = centroidOfGroup(g);
            if (c != null) {
                local.add(toLocal(c, off, scale));
            }
        }
        if (local.isEmpty()) {
            return new ArrayList<>();
        }
        //② 車体側面フィルタ: |x| が最大値の 0.5 倍以上 (=外板側) のものだけ残す (内装扉・中央通路扉を除外)。
        double maxAbsX = 0.0D;
        for (float[] c : local) {
            maxAbsX = Math.max(maxAbsX, Math.abs(c[0]));
        }
        double xThresh = maxAbsX * 0.5D;
        //③ 同じ開口の複数リーフ (a/b, o/i) を水平 1.2 ブロック以内でまとめる。左右 (反対側面) は
        //   |x| が離れているので別扱いになる。
        final double mergeSq = 1.2D * 1.2D;
        List<double[]> clusters = new ArrayList<>();  //{sumX,sumY,sumZ,count}
        for (float[] c : local) {
            if (Math.abs(c[0]) < xThresh) {
                continue;
            }
            double[] found = null;
            for (double[] cl : clusters) {
                double dx = cl[0] / cl[3] - c[0];
                double dz = cl[2] / cl[3] - c[2];
                if (dx * dx + dz * dz <= mergeSq) {
                    found = cl;
                    break;
                }
            }
            if (found == null) {
                clusters.add(new double[]{c[0], c[1], c[2], 1.0D});
            } else {
                found[0] += c[0];
                found[1] += c[1];
                found[2] += c[2];
                found[3] += 1.0D;
            }
        }
        List<float[]> result = new ArrayList<>();
        for (double[] cl : clusters) {
            result.add(new float[]{
                (float) (cl[0] / cl[3]), (float) (cl[1] / cl[3]), (float) (cl[2] / cl[3])});
        }
        return result;
    }

    /** 1 グループの全頂点の重心 (model 座標)。 */
    private static float[] centroidOfGroup(GroupObject g) {
        double sx = 0.0D;
        double sy = 0.0D;
        double sz = 0.0D;
        int n = 0;
        for (Face f : g.faces) {
            for (Vertex v : f.vertices) {
                sx += v.x;
                sy += v.y;
                sz += v.z;
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        return new float[]{(float) (sx / n), (float) (sy / n), (float) (sz / n)};
    }

    /** ドアグループ (複数) の全頂点の重心 (model 座標)。見つからなければ null。 */
    private static float[] centroidOfGroups(PolygonModel model, List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        double sx = 0.0D;
        double sy = 0.0D;
        double sz = 0.0D;
        int n = 0;
        for (String name : names) {
            for (GroupObject g : model.groupObjects) {
                if (g.name == null || !g.name.equalsIgnoreCase(name)) {
                    continue;
                }
                for (Face f : g.faces) {
                    for (Vertex v : f.vertices) {
                        sx += v.x;
                        sy += v.y;
                        sz += v.z;
                        n++;
                    }
                }
            }
        }
        if (n == 0) {
            return null;
        }
        return new float[]{(float) (sx / n), (float) (sy / n), (float) (sz / n)};
    }

    private static PolygonModel loadModel(VehicleDefinition def) throws Exception {
        String modelFile = def.getModelFile();
        if (modelFile == null || modelFile.isBlank()) {
            return null;
        }
        byte[] bytes = NGTFileLoader.findAsset("models/" + modelFile);
        if (bytes == null) {
            bytes = NGTFileLoader.findAsset(modelFile);
        }
        if (bytes == null) {
            return null;
        }
        return ModelLoader.parse(bytes, modelFile);
    }
}
