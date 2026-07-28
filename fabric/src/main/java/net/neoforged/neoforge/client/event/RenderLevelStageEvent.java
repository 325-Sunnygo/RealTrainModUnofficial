package net.neoforged.neoforge.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.neoforged.bus.api.Event;
import org.joml.Matrix4f;

public class RenderLevelStageEvent extends Event {
    private final Stage stage;
    private final PoseStack poseStack;
    private final Matrix4f projectionMatrix;
    private final Camera camera;
    private final float partialTick;

    public RenderLevelStageEvent(Stage stage, PoseStack poseStack, Matrix4f projectionMatrix,
                                 Camera camera, float partialTick) {
        this.stage = stage;
        this.poseStack = poseStack;
        this.projectionMatrix = projectionMatrix;
        this.camera = camera;
        this.partialTick = partialTick;
    }

    public Stage getStage() {
        return stage;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public Camera getCamera() {
        return camera;
    }

    public float getPartialTick() {
        return partialTick;
    }

    /** NeoForge の Stage 定数のうち RTMU が使うものだけ。 */
    public static final class Stage {
        public static final Stage AFTER_SKY = new Stage("after_sky");
        public static final Stage AFTER_SOLID_BLOCKS = new Stage("after_solid_blocks");
        public static final Stage AFTER_ENTITIES = new Stage("after_entities");
        public static final Stage AFTER_BLOCK_ENTITIES = new Stage("after_block_entities");
        public static final Stage AFTER_TRANSLUCENT_BLOCKS = new Stage("after_translucent_blocks");
        public static final Stage AFTER_PARTICLES = new Stage("after_particles");
        public static final Stage AFTER_WEATHER = new Stage("after_weather");
        public static final Stage AFTER_LEVEL = new Stage("after_level");

        private final String name;

        private Stage(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
