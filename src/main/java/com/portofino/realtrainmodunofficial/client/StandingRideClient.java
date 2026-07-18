package com.portofino.realtrainmodunofficial.client;

import jp.ngt.rtm.entity.train.EntityTrainBase;
import jp.ngt.rtm.modelpack.cfg.TrainConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 立ち乗り (standing-ride): 走行中の列車の床に立っているプレイヤーを、列車の移動・旋回に
 * 合わせて毎 tick 運ぶ「動く床 (moving platform)」。座席に着く (mount/startRiding) のではなく
 * <b>位置だけを追従</b>させるので、プレイヤーは車内を自由に歩き回れる。
 *
 * <p>元は AppleExtended (fixRTM 派生) の {@code jp.apple.train.TrainPlatformHandler}。それを
 * 1.21.1 / NeoForge のクライアント権限モデルに移植し、次の点を改良した:
 * <ul>
 *   <li><b>クライアント権限</b>: 1.21 はプレイヤー位置がクライアント権限。<b>ローカル
 *       プレイヤーだけを自分のクライアントで運ぶ</b> (各クライアントが自機を運ぶ)。結果は
 *       通常の移動パケットでサーバー→他クライアントへ伝播する。apple は両サイドで全
 *       プレイヤーを動かしていたが、1.21 ではサーバー側の移動はクライアント予測に
 *       上書きされて成立しない。</li>
 *   <li><b>自前の前 tick 位置キャッシュ</b> ({@link #PREV}) で列車の per-tick 差分を算出。
 *       {@code xOld} の更新タイミングに依存しない。</li>
 *   <li><b>連結対策</b>: 1 game tick に複数車両が同じローカルプレイヤーを運ぶ二重加算を
 *       防ぐ ({@link #lastCarriedGameTime})。連結車は同じ差分で動くので最初の 1 両で足りる。</li>
 *   <li><b>ヒステリシス</b>: 取得は厳しめ ({@link #SURFACE_MARGIN})・保持は緩め
 *       ({@link #RIDE_MARGIN}) で、段差や車体の揺れで不意に落ちない。</li>
 * </ul>
 *
 * <p>クライアント専用クラス ({@code Minecraft} を参照) なので、{@link EntityTrainBase#tick()}
 * からは {@code level().isClientSide()} ガード下でのみ呼ぶ。専用サーバーではこのクラスは
 * 一切ロードされない ({@code EntityVehicleBase.rotateRiders} が {@code FreeCameraController} を
 * 同様に呼ぶ前例と同じ)。
 */
public final class StandingRideClient {

    /** 取得判定: 床面から上下この範囲に足があれば「乗った」とみなす (歩いて乗り込む用)。 */
    private static final double SURFACE_MARGIN = 0.3D;
    /** 保持判定: 既に乗っている間は下方向に緩める (段差・小さな揺れで落ちない)。 */
    private static final double RIDE_MARGIN = 1.0D;
    /** 車体幅方向の余裕。 */
    private static final double SIDE_MARGIN = 0.15D;
    /** 床面 Y への吸着補正係数と 1 tick あたり最大補正量。 */
    private static final double Y_CORRECTION_FACTOR = 0.35D;
    private static final double MAX_Y_CORRECTION = 0.6D;

    /** 列車ごとの前 tick 位置 {x, y, z, yaw}。WeakHashMap なのでアンロード時に自動破棄。 */
    private static final Map<EntityTrainBase, double[]> PREV = new WeakHashMap<>();
    /** この game tick で既にローカルプレイヤーを運んだ車両があるか (二重加算防止)。 */
    private static long lastCarriedGameTime = -1000L;
    /** ローカルプレイヤーを最後に運んだ game tick (ヒステリシス判定用)。 */
    private static long lastRideGameTime = -1000L;

    private StandingRideClient() {
    }

    /**
     * {@link EntityTrainBase#tick()} (クライアント) から毎 tick 呼ぶ。この車両の床に
     * ローカルプレイヤーが立っていれば、車両の移動・旋回ぶんだけプレイヤーを運ぶ。
     */
    public static void tick(EntityTrainBase train) {
        double curX = train.getX();
        double curY = train.getY();
        double curZ = train.getZ();
        float curYaw = train.getYRot();

        double[] prev = PREV.get(train);
        if (prev == null) {
            //初回は差分ゼロ。次 tick から追従開始。
            PREV.put(train, new double[]{curX, curY, curZ, curYaw});
            return;
        }
        double dx = curX - prev[0];
        double dy = curY - prev[1];
        double dz = curZ - prev[2];
        float dYaw = Mth.wrapDegrees(curYaw - (float) prev[3]);
        double prevX = prev[0];
        double prevZ = prev[2];
        prev[0] = curX;
        prev[1] = curY;
        prev[2] = curZ;
        prev[3] = curYaw;

        Player player = Minecraft.getInstance().player;
        if (player == null || !player.isAlive() || player.isPassenger()
                || player.isSpectator() || player.getAbilities().flying
                || player.level() != train.level()) {
            return;
        }

        long now = train.level().getGameTime();
        boolean rideHold = (now - lastRideGameTime) <= 2L;
        double yMargin = rideHold ? RIDE_MARGIN : SURFACE_MARGIN;

        if (!isStandingOn(train, player, rideHold, yMargin)) {
            return;
        }
        //連結: 同じ game tick で別車両が既に運んでいたら二重加算しない。
        if (lastCarriedGameTime == now) {
            return;
        }
        lastCarriedGameTime = now;
        lastRideGameTime = now;

        carry(player, train, prevX, prevZ, dx, dy, dz, dYaw);
    }

    /** プレイヤーの足が、この車両の床面フットプリント内にあるか。 */
    private static boolean isStandingOn(EntityTrainBase train, Player player, boolean rideHold, double yMargin) {
        double relX = player.getX() - train.getX();
        double relZ = player.getZ() - train.getZ();

        double rad = Math.toRadians(train.getYRot());
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        //車体ローカル座標: length = 進行方向, width = 幅方向
        double localLength = relX * sin + relZ * cos;
        double localWidth = relX * cos - relZ * sin;

        double halfWidth = EntityTrainBase.TRAIN_WIDTH / 2.0D + SIDE_MARGIN;
        double halfLength = getHalfLength(train);
        double widthMargin = rideHold ? halfWidth + 0.5D : halfWidth;
        double lengthMargin = rideHold ? halfLength + 0.3D : halfLength;

        if (Math.abs(localWidth) > widthMargin || Math.abs(localLength) > lengthMargin) {
            return false;
        }

        double feetY = player.getBoundingBox().minY;
        double floorY = train.getInteriorFloorY();
        return feetY >= floorY - yMargin && feetY <= floorY + 0.5D;
    }

    /** 車体中心から端までの長さ (config trainDistance)。取れなければ幅の半分でフォールバック。 */
    private static double getHalfLength(EntityTrainBase train) {
        TrainConfig cfg = train.getConfig();
        if (cfg != null && cfg.trainDistance > 0.0F) {
            return cfg.trainDistance;
        }
        return EntityTrainBase.TRAIN_WIDTH / 2.0D;
    }

    /** 列車の並進・旋回ぶんをローカルプレイヤーに加える (床に吸着＋落下無効化)。 */
    private static void carry(Player player, EntityTrainBase train, double prevX, double prevZ,
                             double dx, double dy, double dz, float dYaw) {
        double addX;
        double addZ;
        if (dYaw != 0.0F) {
            //車体中心 (前 tick) まわりにプレイヤー相対位置を回す。並進 (dx/dz) も
            //現在の車体中心を基準に取ることで同時に含まれる。
            double relX = player.getX() - prevX;
            double relZ = player.getZ() - prevZ;
            double rad = Math.toRadians(dYaw);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double rotX = relX * cos + relZ * sin;
            double rotZ = relZ * cos - relX * sin;
            addX = (train.getX() + rotX) - player.getX();
            addZ = (train.getZ() + rotZ) - player.getZ();
        } else {
            addX = dx;
            addZ = dz;
        }

        double floorY = train.getInteriorFloorY();
        double yError = floorY - player.getY();
        double yCorrection = Mth.clamp(yError * Y_CORRECTION_FACTOR, -MAX_Y_CORRECTION, MAX_Y_CORRECTION);

        //SELF: プレイヤー自身の歩行入力 (別途 travel で適用済み) に上乗せして運ぶ。
        player.move(MoverType.SELF, new Vec3(addX, dy + yCorrection, addZ));

        //床に貼り付け: 落下加速を殺し、落下ダメージ・浮遊を防ぐ。
        Vec3 dm = player.getDeltaMovement();
        player.setDeltaMovement(dm.x, 0.0D, dm.z);
        player.fallDistance = 0.0F;
        player.setOnGround(true);
    }
}
