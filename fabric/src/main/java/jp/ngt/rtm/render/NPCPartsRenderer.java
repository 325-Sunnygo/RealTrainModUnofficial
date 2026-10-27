package jp.ngt.rtm.render;

import jp.ngt.ngtlib.math.NGTMath;

/**
 * 本家 jp.ngt.rtm.render.NPCPartsRenderer の移植。
 * 人型 NPC (運転士・乗客) のモデルスクリプトが、各関節の角度を読んでパーツを回す。
 */
@SuppressWarnings("unused")
public class NPCPartsRenderer extends EntityPartsRenderer {
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
        if (entity == null)
            return;
        setupRotateCorpse(entity, partialTicks);
        float arg1 = getRendererArg1(entity, partialTicks);
        float arg2 = getRendererArg2(entity, partialTicks);
        float arg3 = getRendererArg3(entity, partialTicks);
        float arg4 = getRendererArg4(entity, partialTicks);
        float arg5 = getRendererArg5(entity, partialTicks);
        int heldItemRight = heldItemRight(entity);
        int heldItemLeft = heldItemLeft(entity);
        float radPIdiv10 = NGTMath.PI / 10.0F;
        this.headAngleY = NGTMath.toRadians(arg4);
        this.headAngleX = NGTMath.toRadians(arg5);
        this.rightArmAngleX = NGTMath.getCos(arg1 * 0.6662F + NGTMath.PI) * 2.0F * arg2 * 0.5F;
        this.leftArmAngleX = NGTMath.getCos(arg1 * 0.6662F) * 2.0F * arg2 * 0.5F;
        this.rightArmAngleZ = 0.0F;
        this.leftArmAngleZ = 0.0F;
        this.rightLegAngleX = NGTMath.getCos(arg1 * 0.6662F) * 1.4F * arg2;
        this.leftLegAngleX = NGTMath.getCos(arg1 * 0.6662F + NGTMath.PI) * 1.4F * arg2;
        this.rightLegAngleY = 0.0F;
        this.leftLegAngleY = 0.0F;
        if (isRiding(entity)) {
            this.rightArmAngleX += -(NGTMath.PI / 5.0F);
            this.leftArmAngleX += -(NGTMath.PI / 5.0F);
            this.rightLegAngleX = -(NGTMath.PI * 2.0F / 5.0F);
            this.leftLegAngleX = -(NGTMath.PI * 2.0F / 5.0F);
            this.rightLegAngleY = radPIdiv10;
            this.leftLegAngleY = -radPIdiv10;
        }
        if (heldItemLeft > 0)
            this.leftArmAngleX = this.leftArmAngleX * 0.5F - radPIdiv10 * heldItemLeft;
        this.rightArmAngleY = 0.0F;
        this.leftArmAngleY = 0.0F;
        switch (heldItemRight) {
            case 1:
                this.rightArmAngleX = this.rightArmAngleX * 0.5F - radPIdiv10 * heldItemRight;
                break;
            case 3:
                this.rightArmAngleX = this.rightArmAngleX * 0.5F - radPIdiv10 * heldItemRight;
                this.rightArmAngleY = -0.5235988F;
                break;
        }
        float swingProgress = getSwingProgress(entity, partialTicks);
        if (swingProgress > 0.0F) {
            this.bodyAngleY = NGTMath.getSin((float) (Math.sqrt(swingProgress) * NGTMath.PI * 2.0D)) * 0.2F;
            this.rightArmAngleY += this.bodyAngleY;
            this.leftArmAngleY += this.bodyAngleY;
            this.leftArmAngleX += this.bodyAngleY;
            float f = 1.0F - swingProgress;
            f = f * f * f * f;
            f = 1.0F - f;
            float f1 = NGTMath.getSin(f * NGTMath.PI);
            float f2 = NGTMath.getSin(swingProgress * NGTMath.PI) * -(this.headAngleX - 0.7F) * 0.75F;
            this.rightArmAngleX = (float) (this.rightArmAngleX - f1 * 1.2D + f2);
            this.rightArmAngleY += this.bodyAngleY * 2.0F;
            this.rightArmAngleZ += NGTMath.getSin(swingProgress * NGTMath.PI) * -0.4F;
        }
        if (isSneak(entity)) {
            this.bodyAngleX = 0.5F;
            this.rightArmAngleX += 0.4F;
            this.leftArmAngleX += 0.4F;
        } else {
            this.bodyAngleX = 0.0F;
        }
        this.rightArmAngleZ += NGTMath.getCos(arg3 * 0.09F) * 0.05F + 0.05F;
        this.leftArmAngleZ -= NGTMath.getCos(arg3 * 0.09F) * 0.05F + 0.05F;
        this.rightArmAngleX += NGTMath.getSin(arg3 * 0.067F) * 0.05F;
        this.leftArmAngleX -= NGTMath.getSin(arg3 * 0.067F) * 0.05F;
        if (aimedBow(entity)) {
            float f3 = 0.0F;
            float f4 = 0.0F;
            this.rightArmAngleZ = 0.0F;
            this.leftArmAngleZ = 0.0F;
            this.rightArmAngleY = -(0.1F - f3 * 0.6F) + this.headAngleY;
            this.leftArmAngleY = 0.1F - f3 * 0.6F + this.headAngleY + 0.4F;
            this.rightArmAngleX = -NGTMath.toRadians(90.0F) + this.headAngleX;
            this.leftArmAngleX = -NGTMath.toRadians(90.0F) + this.headAngleX;
            this.rightArmAngleX -= f3 * 1.2F - f4 * 0.4F;
            this.leftArmAngleX -= f3 * 1.2F - f4 * 0.4F;
            this.rightArmAngleZ += NGTMath.getCos(arg3 * 0.09F) * 0.05F + 0.05F;
            this.leftArmAngleZ -= NGTMath.getCos(arg3 * 0.09F) * 0.05F + 0.05F;
            this.rightArmAngleX += NGTMath.getSin(arg3 * 0.067F) * 0.05F;
            this.leftArmAngleX -= NGTMath.getSin(arg3 * 0.067F) * 0.05F;
        }
        float f0 = this.rightArmAngleY;
        this.rightArmAngleY = this.leftArmAngleY;
        this.leftArmAngleY = f0;
        f0 = this.rightArmAngleZ;
        this.rightArmAngleZ = this.leftArmAngleZ;
        this.leftArmAngleZ = f0;
        f0 = this.rightLegAngleX;
        this.rightLegAngleX = this.leftLegAngleX;
        this.leftLegAngleX = f0;
        f0 = this.rightLegAngleY;
        this.rightLegAngleY = this.leftLegAngleY;
        this.leftLegAngleY = f0;
        f0 = this.rightLegAngleZ;
        this.rightLegAngleZ = this.leftLegAngleZ;
        this.leftLegAngleZ = f0;
        this.headAngleY = -this.headAngleY;
        this.bodyAngleY = -this.bodyAngleY;
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
                && (e.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem
                    || e.getUseItem().getItem() instanceof jp.ngt.rtm.item.ItemGun);
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

    public void setupRotateCorpse(Object entity, float partialTicks) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            float yaw = interpolateRotation(living.yBodyRotO, living.yBodyRot, partialTicks);
            rotateCorpse(entity, handleRotationFloat(entity, partialTicks), yaw, partialTicks);
        }
    }

    public void rotateCorpse(Object entity, float age, float yaw, float partialTicks) {
        jp.ngt.ngtlib.renderer.GL11Facade.glRotatef(-yaw, 0, 1, 0);
        if (entity instanceof net.minecraft.world.entity.LivingEntity living && living.deathTime > 0) {
            float rotation = (float) NGTMath.firstSqrt((living.deathTime + partialTicks - 1.0F) / 20.0F * 1.6F);
            jp.ngt.ngtlib.renderer.GL11Facade.glRotatef(Math.min(rotation, 1.0F) * getDeathMaxRotation(entity), 0, 0, 1);
        }
    }

    public float getDeathMaxRotation(Object entity) {
        return 90.0F;
    }
}
