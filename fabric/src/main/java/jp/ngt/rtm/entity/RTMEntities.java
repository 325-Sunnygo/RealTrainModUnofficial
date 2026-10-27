package jp.ngt.rtm.entity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.entity.train.EntityBogie;
import jp.ngt.rtm.entity.train.EntityTrain;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * jp.ngt.rtm エンティティの登録 (Phase 2)。
 * メインクラスから REGISTER.register(modBus) される。
 */
public final class RTMEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(Registries.ENTITY_TYPE, RealTrainModUnofficial.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<EntityTrain>> TRAIN =
            REGISTER.register("rtm_train", () -> EntityType.Builder.<EntityTrain>of(EntityTrain::new, MobCategory.MISC)
                    .sized(EntityTrainBase.TRAIN_WIDTH, EntityTrainBase.TRAIN_HEIGHT)
                    .clientTrackingRange(10)
                    // ★毎tick送る。本家は VehicleTrackerEntry という専用トラッカーを
                    // 持たせて位置を毎tick配っている。3 tick に 1 回だと、走行中だけ
                    // 車ごとに更新の位相がずれて車間が開く (停車中は正常に見える)。
                    .updateInterval(1)
                    .build("rtm_train"));

    public static final DeferredHolder<EntityType<?>, EntityType<EntityBogie>> BOGIE =
            REGISTER.register("rtm_bogie", () -> EntityType.Builder.<EntityBogie>of(EntityBogie::new, MobCategory.MISC)
                    .sized(EntityTrainBase.TRAIN_WIDTH, EntityTrainBase.TRAIN_HEIGHT)
                    .clientTrackingRange(10)
                    // ★毎tick送る。本家は VehicleTrackerEntry という専用トラッカーを
                    // 持たせて位置を毎tick配っている。3 tick に 1 回だと、走行中だけ
                    // 車ごとに更新の位相がずれて車間が開く (停車中は正常に見える)。
                    .updateInterval(1)
                    .build("rtm_bogie"));

    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.train.parts.EntityFloor>> FLOOR =
            REGISTER.register("rtm_floor", () -> EntityType.Builder.<jp.ngt.rtm.entity.train.parts.EntityFloor>of(
                            jp.ngt.rtm.entity.train.parts.EntityFloor::new, MobCategory.MISC)
                    // 本家 setSize(1.25F, 0.0625F) — 高さは掴みやすいよう少し確保
                    .sized(1.25F, 0.35F)
                    .clientTrackingRange(8)
                    // ★毎tick送る。本家は VehicleTrackerEntry という専用トラッカーを
                    // 持たせて位置を毎tick配っている。3 tick に 1 回だと、走行中だけ
                    // 車ごとに更新の位相がずれて車間が開く (停車中は正常に見える)。
                    .updateInterval(1)
                    .build("rtm_floor"));

    // 本家 EntityMotorman (運転士)。setSize(0.6F, 1.8F)
    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.npc.EntityMotorman>> MOTORMAN =
            REGISTER.register("rtm_motorman", () -> EntityType.Builder.of(
                            jp.ngt.rtm.entity.npc.EntityMotorman::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("rtm_motorman"));

    /**
     * 本家 EntityBullet (銃の弾)。
     * 本家は {@code setSize(0.05F,0.05F)}、40cm 砲弾だけ 0.5F。当たり判定は自前で
     * AABB を膨らませて取るので、大きい方に合わせてある。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<EntityBullet>> BULLET =
            REGISTER.register("rtm_bullet", () -> EntityType.Builder.<EntityBullet>of(
                            EntityBullet::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("rtm_bullet"));

    /**
     * 本家 EntityFluid (溶けた金属 / コークスの粒)。本家 {@code setSize(SIZE, SIZE)}。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.fluid.EntityFluid>> FLUID =
            REGISTER.register("rtm_fluid", () -> EntityType.Builder.<jp.ngt.rtm.entity.fluid.EntityFluid>of(
                            jp.ngt.rtm.entity.fluid.EntityFluid::new, MobCategory.MISC)
                    .sized(jp.ngt.rtm.entity.fluid.EntityFluid.SIZE, jp.ngt.rtm.entity.fluid.EntityFluid.SIZE)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build("rtm_fluid"));

    /** 本家 EntityContainer。setSize(3.0F, 2.5F) */
    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.train.parts.EntityContainer>> CONTAINER =
            REGISTER.register("rtm_container", () -> EntityType.Builder.<jp.ngt.rtm.entity.train.parts.EntityContainer>of(
                            jp.ngt.rtm.entity.train.parts.EntityContainer::new, MobCategory.MISC)
                    .sized(3.0F, 2.5F).clientTrackingRange(10).updateInterval(1)
                    .build("rtm_container"));

    /** 本家 EntityArtillery。setSize(3.0F, 2.5F) */
    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.train.parts.EntityArtillery>> ARTILLERY =
            REGISTER.register("rtm_artillery", () -> EntityType.Builder.<jp.ngt.rtm.entity.train.parts.EntityArtillery>of(
                            jp.ngt.rtm.entity.train.parts.EntityArtillery::new, MobCategory.MISC)
                    .sized(3.0F, 2.5F).clientTrackingRange(10).updateInterval(1)
                    .build("rtm_artillery"));

    /** 本家 EntityTie。setSize(3.0F, 0.125F) */
    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.train.parts.EntityTie>> TIE =
            REGISTER.register("rtm_tie", () -> EntityType.Builder.<jp.ngt.rtm.entity.train.parts.EntityTie>of(
                            jp.ngt.rtm.entity.train.parts.EntityTie::new, MobCategory.MISC)
                    .sized(3.0F, 0.125F).clientTrackingRange(10).updateInterval(1)
                    .build("rtm_tie"));

    /** 移動装置が運ぶブロック 1 個ぶんの当たり判定。 */
    public static final DeferredHolder<EntityType<?>, EntityType<EntityMMBoundingBox>> MM_BOUNDING_BOX =
            REGISTER.register("rtm_mm_bounding_box", () -> EntityType.Builder.<EntityMMBoundingBox>of(
                            EntityMMBoundingBox::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(8).updateInterval(1)
                    .build("rtm_mm_bounding_box"));

    /** 本家 EntityNPC (モデル付きの人形)。setSize(0.6F, 1.8F) */
    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.npc.EntityNPC>> NPC =
            REGISTER.register("rtm_npc", () -> EntityType.Builder.<jp.ngt.rtm.entity.npc.EntityNPC>of(
                            jp.ngt.rtm.entity.npc.EntityNPC::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).updateInterval(3)
                    .build("rtm_npc"));

    /** ブロック製の車/船/飛行機 (乗り物生成機が作る)。 */
    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.vehicle.EntityCar>> NGTO_CAR =
            REGISTER.register("rtm_ngto_car", () -> EntityType.Builder.<jp.ngt.rtm.entity.vehicle.EntityCar>of(
                            jp.ngt.rtm.entity.vehicle.EntityCar::new, MobCategory.MISC)
                    .sized(1.5F, 1.5F).clientTrackingRange(10).updateInterval(3)
                    .build("rtm_ngto_car"));

    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.vehicle.EntityShip>> NGTO_SHIP =
            REGISTER.register("rtm_ngto_ship", () -> EntityType.Builder.<jp.ngt.rtm.entity.vehicle.EntityShip>of(
                            jp.ngt.rtm.entity.vehicle.EntityShip::new, MobCategory.MISC)
                    .sized(1.5F, 1.5F).clientTrackingRange(10).updateInterval(3)
                    .build("rtm_ngto_ship"));

    public static final DeferredHolder<EntityType<?>, EntityType<jp.ngt.rtm.entity.vehicle.EntityPlane>> NGTO_PLANE =
            REGISTER.register("rtm_ngto_plane", () -> EntityType.Builder.<jp.ngt.rtm.entity.vehicle.EntityPlane>of(
                            jp.ngt.rtm.entity.vehicle.EntityPlane::new, MobCategory.MISC)
                    .sized(1.5F, 1.5F).clientTrackingRange(10).updateInterval(3)
                    .build("rtm_ngto_plane"));

    private RTMEntities() {
    }
}
