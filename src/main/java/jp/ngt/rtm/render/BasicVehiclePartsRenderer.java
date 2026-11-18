package jp.ngt.rtm.render;

import jp.ngt.ngtlib.renderer.GLRecorder;
import jp.ngt.ngtlib.renderer.model.GroupObject;
import jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig;
import jp.ngt.rtm.modelpack.cfg.VehicleBaseConfig.VehicleParts;
import jp.ngt.rtm.modelpack.modelset.ModelSetVehicleBaseClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** 本家 jp.ngt.rtm.render.BasicVehiclePartsRenderer (K… */
@OnlyIn(Dist.CLIENT)
public class BasicVehiclePartsRenderer extends VehiclePartsRenderer {
    private Parts body;
    private PartsWithChildren[] doorLeft, doorRight;
    private PartsWithChildren[] pantographFront, pantographBack;

    public BasicVehiclePartsRenderer(String... args) {
        super(args);
    }

    @Override
    public void init(Object modelSet, Object modelObject) {
        if (!(modelSet instanceof ModelSetVehicleBaseClient<?> set)
                || !(modelObject instanceof ModelObject mo) || mo.model == null) {
            super.init(modelSet, modelObject);
            return;
        }
        VehicleBaseConfig cfg = set.getConfig();
        List<String> used = new ArrayList<>();

        this.doorLeft = cfg.door_left != null ? this.getParts(used, cfg.door_left) : new PartsWithChildren[0];
        this.doorRight = cfg.door_right != null ? this.getParts(used, cfg.door_right) : new PartsWithChildren[0];
        this.pantographFront = cfg.pantograph_front != null
                ? this.getParts(used, cfg.pantograph_front) : new PartsWithChildren[0];
        this.pantographBack = cfg.pantograph_back != null
                ? this.getParts(used, cfg.pantograph_back) : new PartsWithChildren[0];

        List<GroupObject> groups = mo.model.getGroupObjects();
        List<String> bodyParts = groups.stream()
                .map(group -> group.name)
                .filter(name -> !used.contains(name))
                .collect(Collectors.toList());
        this.body = new Parts(bodyParts.toArray(new String[0]));

        this.partsList.addAll(Arrays.asList(this.doorLeft));
        this.partsList.addAll(Arrays.asList(this.doorRight));
        this.partsList.addAll(Arrays.asList(this.pantographFront));
        this.partsList.addAll(Arrays.asList(this.pantographBack));
        this.partsList.add(this.body);

        super.init(modelSet, modelObject);
    }

    private PartsWithChildren[] getParts(List<String> used, VehicleParts[] parts) {
        PartsWithChildren[] out = new PartsWithChildren[parts.length];
        IntStream.range(0, parts.length).forEach(i -> {
            VehicleParts part = parts[i];
            if (part != null) {
                part.initParts();
            }
            out[i] = new PartsWithChildren(part == null ? new String[0] : part.objects);
            if (part != null && part.objects != null) {
                used.addAll(Arrays.asList(part.objects));
            }
            if (part != null && part.childParts != null) {
                out[i].childParts.addAll(Arrays.asList(this.getParts(used, part.childParts)));
            }
        });
        return out;
    }

    @Override
    public void render(Object entity, int pass, float partialTick) {
        this.currentPass = pass;
        if (this.body != null) {
            this.body.render(this);
        }
        if (!(this.modelSet instanceof ModelSetVehicleBaseClient<?> set)) {
            return;
        }
        VehicleBaseConfig cfg = set.getConfig();

        if (cfg.door_left != null) {
            float move = this.getDoorMovementL(entity);
            IntStream.range(0, Math.min(cfg.door_left.length, this.doorLeft.length))
                    .forEach(j -> this.renderParts(this.sigmoid(move), cfg.door_left[j], this.doorLeft[j]));
        }
        if (cfg.door_right != null) {
            float move = this.getDoorMovementR(entity);
            IntStream.range(0, Math.min(cfg.door_right.length, this.doorRight.length))
                    .forEach(j -> this.renderParts(this.sigmoid(move), cfg.door_right[j], this.doorRight[j]));
        }
        if (cfg.pantograph_front != null) {
            float move = this.getPantographMovementFront(entity);
            IntStream.range(0, Math.min(cfg.pantograph_front.length, this.pantographFront.length))
                    .forEach(j -> this.renderParts(this.sigmoid(move), cfg.pantograph_front[j], this.pantographFront[j]));
        }
        if (cfg.pantograph_back != null) {
            float move = this.getPantographMovementBack(entity);
            IntStream.range(0, Math.min(cfg.pantograph_back.length, this.pantographBack.length))
                    .forEach(j -> this.renderParts(this.sigmoid(move), cfg.pantograph_back[j], this.pantographBack[j]));
        }
    }

    private void renderParts(float move, VehicleParts part, PartsWithChildren parts) {
        if (part == null || parts == null) {
            return;
        }
        GLRecorder recorder = GLRecorder.active();
        if (recorder == null) {
            return;
        }
        if (part.pos == null || part.pos.length != 3) {
            part.initParts();
        }
        recorder.push();
        recorder.translate(part.pos[0], part.pos[1], part.pos[2]);
        if (part.transform != null) {
            for (float[] fa : part.transform) {
                if (fa == null) {
                    continue;
                }
                if (fa.length == 3) {
                    recorder.translate(fa[0] * move, fa[1] * move, fa[2] * move);
                } else if (fa.length == 4) {
                    recorder.rotate(fa[0] * move, fa[1], fa[2], fa[3]);
                }
            }
        }
        recorder.translate(-part.pos[0], -part.pos[1], -part.pos[2]);
        parts.render(this);

        if (part.childParts != null) {
            IntStream.range(0, Math.min(part.childParts.length, parts.childParts.size()))
                    .forEach(i -> this.renderParts(move, part.childParts[i],
                            (PartsWithChildren) parts.childParts.get(i)));
        }
        recorder.pop();
    }
}
