package jp.ngt.rtm.render;

/**
 * 本家 jp.ngt.rtm.render.NPCPartsRenderer の移植。
 * 人型 NPC (運転士・乗客) のモデルスクリプトが、各関節の角度を読んでパーツを回す。
 *
 * <p>角度はラジアン。本家は {@code setRotationAngles} で歩行/腕振り/しゃがみを計算して
 * これらのフィールドへ書き、スクリプトは {@code renderer.rightArmAngleX} のように読む。
 */
@SuppressWarnings("unused")
public class NPCPartsRenderer extends PartsRenderer {
    public float headAngleX, headAngleY, headAngleZ;
    public float bodyAngleX, bodyAngleY, bodyAngleZ;
    public float leftArmAngleX, leftArmAngleY, leftArmAngleZ;
    public float rightArmAngleX, rightArmAngleY, rightArmAngleZ;
    public float leftLegAngleX, leftLegAngleY, leftLegAngleZ;
    public float rightLegAngleX, rightLegAngleY, rightLegAngleZ;

    public NPCPartsRenderer(String... args) {
        super(args);
    }

    /**
     * 本家 setRotationAngles: 歩行モーションから各関節角を求める。
     * 本家の getRendererArg1/2 は「歩行距離」と「歩幅」なので、1.21 では
     * walkAnimation から同じ値が取れる。
     */
    public void setRotationAngles(Object entity, float partialTicks) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
            return;
        }
        float walkPos = living.walkAnimation.position(partialTicks);
        float walkSpeed = living.walkAnimation.speed(partialTicks);
        float yawOffset = net.minecraft.util.Mth.rotLerp(partialTicks,
                living.yHeadRotO, living.yHeadRot)
                - net.minecraft.util.Mth.rotLerp(partialTicks, living.yBodyRotO, living.yBodyRot);
        float pitch = net.minecraft.util.Mth.lerp(partialTicks, living.xRotO, living.getXRot());

        this.headAngleY = (float) Math.toRadians(yawOffset);
        this.headAngleX = (float) Math.toRadians(pitch);

        //腕と脚は歩行位相で前後に振る (本家と同じ cos/ちょうど半周ずれ)
        this.rightArmAngleX = (float) Math.cos(walkPos * 0.6662D + Math.PI) * 2.0F * walkSpeed * 0.5F;
        this.leftArmAngleX = (float) Math.cos(walkPos * 0.6662D) * 2.0F * walkSpeed * 0.5F;
        this.rightLegAngleX = (float) Math.cos(walkPos * 0.6662D) * 1.4F * walkSpeed;
        this.leftLegAngleX = (float) Math.cos(walkPos * 0.6662D + Math.PI) * 1.4F * walkSpeed;

        this.rightArmAngleZ = 0.0F;
        this.leftArmAngleZ = 0.0F;

        if (living.isPassenger()) {
            //座っている姿勢
            this.rightArmAngleX = (float) Math.toRadians(-45.0D);
            this.leftArmAngleX = (float) Math.toRadians(-45.0D);
            this.rightLegAngleX = (float) Math.toRadians(-75.0D);
            this.leftLegAngleX = (float) Math.toRadians(-75.0D);
            this.rightLegAngleY = (float) Math.toRadians(10.0D);
            this.leftLegAngleY = (float) Math.toRadians(-10.0D);
        } else if (living.isCrouching()) {
            this.bodyAngleX = 0.5F;
            this.rightLegAngleZ = 0.0F;
            this.leftLegAngleZ = 0.0F;
        } else {
            this.bodyAngleX = 0.0F;
        }
    }

    public boolean isRiding(Object entity) {
        return entity instanceof net.minecraft.world.entity.Entity e && e.isPassenger();
    }

    public boolean isSneak(Object entity) {
        return entity instanceof net.minecraft.world.entity.LivingEntity e && e.isCrouching();
    }

    public boolean aimedBow(Object entity) {
        return entity instanceof net.minecraft.world.entity.LivingEntity e
                && e.isUsingItem()
                && e.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem;
    }

    /** 手に何か持っているか (0=無, 1=有)。 */
    public int heldItemRight(Object entity) {
        return entity instanceof net.minecraft.world.entity.LivingEntity e
                && !e.getMainHandItem().isEmpty() ? 1 : 0;
    }

    public int heldItemLeft(Object entity) {
        return entity instanceof net.minecraft.world.entity.LivingEntity e
                && !e.getOffhandItem().isEmpty() ? 1 : 0;
    }

    public float getSwingProgress(Object entity, float partialTicks) {
        return entity instanceof net.minecraft.world.entity.LivingEntity e
                ? e.getAttackAnim(partialTicks) : 0.0F;
    }

    /** 本家 getRendererArg1: 歩行距離 (位相)。 */
    public float getRendererArg1(Object entity, float partialTicks) {
        return entity instanceof net.minecraft.world.entity.LivingEntity e
                ? e.walkAnimation.position(partialTicks) : 0.0F;
    }

    /** 本家 getRendererArg2: 歩幅 (速さ)。 */
    public float getRendererArg2(Object entity, float partialTicks) {
        return entity instanceof net.minecraft.world.entity.LivingEntity e
                ? e.walkAnimation.speed(partialTicks) : 0.0F;
    }

    /** 本家 getRendererArg3: 経過 tick。 */
    public float getRendererArg3(Object entity, float partialTicks) {
        return entity instanceof net.minecraft.world.entity.Entity e
                ? e.tickCount + partialTicks : 0.0F;
    }

    /** 本家 getRendererArg4: 頭と体のヨー差 (度)。 */
    public float getRendererArg4(Object entity, float partialTicks) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity e)) {
            return 0.0F;
        }
        return net.minecraft.util.Mth.rotLerp(partialTicks, e.yHeadRotO, e.yHeadRot)
                - net.minecraft.util.Mth.rotLerp(partialTicks, e.yBodyRotO, e.yBodyRot);
    }

    /** 本家 getRendererArg5: ピッチ (度)。 */
    public float getRendererArg5(Object entity, float partialTicks) {
        return entity instanceof net.minecraft.world.entity.Entity e
                ? net.minecraft.util.Mth.lerp(partialTicks, e.xRotO, e.getXRot()) : 0.0F;
    }

    public float interpolateRotation(float prev, float cur, float partialTicks) {
        return net.minecraft.util.Mth.rotLerp(partialTicks, prev, cur);
    }

    public float handleRotationFloat(Object entity, float partialTicks) {
        return entity instanceof net.minecraft.world.entity.Entity e
                ? e.tickCount + partialTicks : 0.0F;
    }

    public float getDeathMaxRotation(Object entity) {
        return 90.0F;
    }
}
