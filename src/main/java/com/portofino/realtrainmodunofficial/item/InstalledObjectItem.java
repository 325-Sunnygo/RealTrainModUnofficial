package com.portofino.realtrainmodunofficial.item;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.ClientHooks;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectDefinition;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

// jp.ngt.rtm.item.ItemInstalledObject を継承し、NGTO Builder の Wire スクリプトの
// `instanceof ItemInstalledObject`(リレー/碍子判定) が真になるようにする。
public class InstalledObjectItem extends jp.ngt.rtm.item.ItemInstalledObject implements ModelSelectableItem {
    private final InstalledObjectCategory category;

    public InstalledObjectItem(InstalledObjectCategory category) {
        super(new Properties());
        this.category = category;
    }

    /**
     * 本家 KaizPatchX の customIconTexture 用の描画器。
     * 実際に使われるのは、選択中のモデルが customIconTexture を持つときだけ
     * (com.portofino.realtrainmodunofficial.client.renderer.CustomIconItemModel が判定する)。
     */
    @Override
    public void initializeClient(java.util.function.Consumer<
            net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private com.portofino.realtrainmodunofficial.client.renderer.CustomIconItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.portofino.realtrainmodunofficial.client.renderer.CustomIconItemRenderer();
                }
                return renderer;
            }
        });
    }

    public InstalledObjectCategory getCategory() {
        return category;
    }

    // --- NGTO Builder の Wire ツール互換 (本家 ItemWithModel の API 名) ---
    // スクリプトは ItemStackCompat(ラッパー) を渡すので Object で受けて unwrap する。

    /** 選択中モデルの bare name (本家の定義名)。スクリプトが "baru_insulator_xx_l" 等と比較する。 */
    public String getModelName(Object stackLike) {
        InstalledObjectDefinition def = selectedDefinition(stackLike);
        return def == null ? "" : def.getBareName();
    }

    /** 選択中モデルの connectorType ("Relay" 等)。Wire ツールのリレー碍子判定に使う。 */
    public String getSubType(Object stackLike) {
        InstalledObjectDefinition def = selectedDefinition(stackLike);
        return def == null ? "" : def.getSubType();
    }

    private InstalledObjectDefinition selectedDefinition(Object stackLike) {
        ItemStack stack = jp.ngt.mccompat.ItemStackCompat.unwrap(stackLike);
        if (stack == null) {
            return null;
        }
        String id = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(stack);
        return InstalledObjectRegistry.getById(id);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // コネクタはモデル固定 (本家 Input01/Output01) — 選択画面を出さない
        if (category == InstalledObjectCategory.CONNECTOR_INPUT
                || category == InstalledObjectCategory.CONNECTOR_OUTPUT) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }
        if (level.isClientSide) {
            ClientHooks.openInstalledObjectSelectScreen(player, player.getItemInHand(hand), category);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    /** コネクタのデフォルト定義 (Input01/Output01 優先、無ければ同カテゴリの先頭) */
    private static InstalledObjectDefinition findDefaultConnector(InstalledObjectCategory category) {
        String defaultName = category == InstalledObjectCategory.CONNECTOR_INPUT ? "input01" : "output01";
        InstalledObjectDefinition fallback = null;
        for (InstalledObjectDefinition def : InstalledObjectRegistry.getByCategory(category)) {
            if (fallback == null) {
                fallback = def;
            }
            // 定義 ID は "category:pack:name" 形式のため末尾名で判定
            String id = def.getId().toLowerCase(java.util.Locale.ROOT);
            if (id.endsWith(":" + defaultName) || id.equals(defaultName)) {
                return def;
            }
        }
        return fallback;
    }

    /**
     * レールに載せる設置物 (列車検知器) の姿勢。
     * @param yaw            レールの向き
     * @param pitch          レールの勾配 (yaw の後に掛ける = モデル局所のX回転)
     * @param roll           レールのカント (yaw の後に掛ける = モデル局所のZ回転)
     * @param offX/offY/offZ 描画原点 (placePos + (0.5, 0, 0.5)) からレール上の点までの差分
     */
    private record RailSnap(float yaw, float pitch, float roll, double offX, double offY, double offZ) {
    }

    /**
     * 本家 ItemInstalledObject.setEntityOnRail の移植。
     * クリックしたブロックがレールなら、そのレール曲線上で最も近い点を求め、そこへモデルを
     * 載せる (位置・向き・勾配・カント)。
     */
    @javax.annotation.Nullable
    private static RailSnap computeRailSnap(Level level, BlockPos railPos, BlockPos placePos, Player player) {
        jp.ngt.rtm.rail.util.RailMap rm = jp.ngt.rtm.rail.TileEntityLargeRailBase.getRailMapFromCoordinates(
                level, null, railPos.getX(), railPos.getY(), railPos.getZ());
        if (rm == null) {
            return null;
        }
        final int split = 128;
        int index = rm.getNearlestPoint(split, railPos.getX() + 0.5D, railPos.getZ() + 0.5D);
        if (index < 0) {
            index = 0;
        }
        double[] rpos = rm.getRailPos(split, index);
        // 本家: getRailPos は {z, x} の順。レール面から少しだけ浮かせる。
        double posX = rpos[1];
        double posZ = rpos[0];
        double posY = rm.getRailHeight(split, index) + 0.0625D;

        // 本家: プレイヤーの向きとレールの向きが 90°以上ずれていたら 180°反転させる
        // (勾配とカントの符号も一緒に反転する)。
        float railYaw = rm.getRailYaw(split, index);
        float playerFacing = -player.getYRot() + 180.0F;
        boolean invert = Math.abs(net.minecraft.util.Mth.wrapDegrees(railYaw - playerFacing)) > 90.0F;
        float sign = invert ? -1.0F : 1.0F;
        if (invert) {
            railYaw += 180.0F;
        }

        // レール角と設置物レンダラの角度は座標系が違う。
        // 列車 (レールに正しく沿う): YP(railYaw) → XP(-railPitch) → ZP(cant)
        // 設置物:                    YP(180 - yaw) → XP(mountPitch) → ZP(mountRoll)
        // 同じ姿勢にするには yaw = 180 - railYaw を渡す (そのまま渡すと 180°ずれる)。
        float yaw = 180.0F - railYaw;
        float pitch = -rm.getRailPitch(split, index) * sign;
        float roll = rm.getRailRoll(split, index) * sign;

        return new RailSnap(yaw, pitch, roll,
                posX - (placePos.getX() + 0.5D),
                posY - placePos.getY(),
                posZ - (placePos.getZ() + 0.5D));
    }

    /**
     * 本家 {@code TileEntityPlaceable.setRotation(player, interval, sync)} と同じ向きの出し方。
     *
     * <pre>yaw = floor(normalizeAngle(-playerYaw + 180 + interval/2) / interval) * interval</pre>
     *
     * <p>刻みは本家と同じで <b>通常 15 度 / スニークで 1 度</b>。
     * RTMU は 22.5 度刻み (スニークで生の角度) にしていたので、斜めに置いたときの向きが
     * 本家とずれていた。符号と +180 も本家に無いと 180 度反対を向く。
     */
    /**
     * 本家でエンティティ実装になっている設置物 (ATC / 列車検知器 / 車止め) のエンティティを作る。
     * それ以外は null (従来どおり設置物ブロックとして置く)。
     */
    private static jp.ngt.rtm.entity.EntityInstalledObject createWiringEntity(
            InstalledObjectCategory category, net.minecraft.world.level.Level level) {
        var entities = com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.class;
        return switch (category) {
            case ATC -> new jp.ngt.rtm.entity.EntityATC(
                com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.ATC.get(), level);
            case TRAIN_DETECTOR -> new jp.ngt.rtm.entity.EntityTrainDetector(
                com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities.TRAIN_DETECTOR.get(),
                level);
            case BUMPING_POST -> new jp.ngt.rtm.entity.EntityBumpingPost(
                com.portofino.realtrainmodunofficial.registry.RealTrainModUnofficialEntities
                    .BUMPING_POST_ENTITY.get(),
                level);
            default -> null;
        };
    }

    private static float honkeRotation(net.minecraft.world.entity.player.Player player) {
        float interval = player.isShiftKeyDown() ? 1.0F : 15.0F;
        return honkeRotation(player, interval);
    }

    /** 本家 setRotation(player, interval) の刻みを指定できる版 (改札は 90)。 */
    private static float honkeRotation(net.minecraft.world.entity.player.Player player, float interval) {
        double a = jp.ngt.ngtlib.math.NGTMath.normalizeAngle(
                -player.getYRot() + 180.0D + (interval / 2.0D));
        int steps = net.minecraft.util.Mth.floor(a / interval);
        return (float) steps * interval;
    }

    /**
     * 本家 {@code Block.onBlockPlacedBy} と同じ 4 方位。
     * 足場/階段は 15 度刻みではなく、プレイヤーの向きを 90 度単位に丸める。
     */
    private static float vanillaDirYaw(net.minecraft.world.entity.player.Player player) {
        int dir = net.minecraft.util.Mth.floor(player.getYRot() * 4.0F / 360.0F + 0.5F) & 3;
        return dir * 90.0F;
    }

    /**
     * 信号の向き。<b>本家 ItemSignal はプレイヤーの向きではなくクリックした面で決める。</b>
     *
     * <pre>int dir = par7 == 2 ? 2 : (par7 == 4 ? 3 : (par7 == 3 ? 0 : 1));  // 1.7.10 の面番号
     * // 描画は RenderSignal が getBlockDirection() = dir * 90 度で回す</pre>
     *
     * つまり北面をクリック→180 度・南面→0 度・西面→270 度・東面→90 度。
     * RTM の yaw は MC の {@code Direction.toYRot()} と X 軸が逆なので、
     * {@code toYRot()} をそのまま使うと東西が入れ替わる。
     *
     * <p>本家は上面/下面クリックでは信号を置かせない (即 return)。RTMU は置けるままにしてあるので、
     * その場合だけプレイヤーの向きを 90 度刻みにして代用する。
     *
     * <p>★以前はプレイヤーの生の yaw をそのまま入れていた。本家の量子化 (-yaw + 180) を
     * 通していないので、正面から置くと 180 度反対を向いていた。
     */
    private static float signalYaw(net.minecraft.core.Direction clickedFace,
                                   net.minecraft.world.entity.player.Player player) {
        return switch (clickedFace) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 270.0F;
            case EAST -> 90.0F;
            // 上下面: 本家に対応が無いので、本家の量子化式を 90 度刻みで使う
            default -> {
                double a = jp.ngt.ngtlib.math.NGTMath.normalizeAngle(-player.getYRot() + 180.0D + 45.0D);
                yield (float) (net.minecraft.util.Mth.floor(a / 90.0D) * 90);
            }
        };
    }

    /**
     * 本家 ItemInstalledObject の看板向き:
     * floor(normalizeAngle(yaw + 180) / 90 + 0.5) & 3
     */
    private static byte signDirectionOf(float yaw) {
        float a = (yaw + 180.0F) % 360.0F;
        if (a < 0.0F) {
            a += 360.0F;
        }
        return (byte) (net.minecraft.util.Mth.floor(a / 90.0F + 0.5F) & 3);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // コンポーネントが失われても CUSTOM_DATA から復元する(碍子等の選択がワールド再入場で
        // 消える対策)。setSelectedModelData が両方へ書いているのでフォールバックで確実に読める。
        String selectedId = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(stack);
        InstalledObjectDefinition definition = InstalledObjectRegistry.getById(selectedId);
        if (definition == null || definition.getCategory() != category) {
            // コネクタは本家デフォルトモデル (Input01/Output01) 固定 — 選択画面は出さない
            if (category == InstalledObjectCategory.CONNECTOR_INPUT
                    || category == InstalledObjectCategory.CONNECTOR_OUTPUT) {
                definition = findDefaultConnector(category);
                if (definition == null) {
                    if (!level.isClientSide) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "コネクタのモデル (Input01/Output01) が見つかりません"), true);
                    }
                    return InteractionResult.FAIL;
                }
            } else {
                if (level.isClientSide) {
                    ClientHooks.openInstalledObjectSelectScreen(player, stack, category);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        net.minecraft.core.Direction clickedFace = context.getClickedFace();
        boolean signal = category == InstalledObjectCategory.SIGNAL;
        // ★本家 ItemSignal: <b>クリックしたブロック自体を信号に置き換える</b> (隣の空気ではない)。
        //   上面/下面クリックでは置かない (本家は即 return する)。
        if (signal && clickedFace.getAxis().isVertical()) {
            return InteractionResult.PASS;
        }
        // 本家 ItemInstalledObject: 踏切/転轍機/券売機は<b>上面クリック (par7==1) のみ</b>設置できる。
        boolean topOnly = category == InstalledObjectCategory.CROSSING
                || category == InstalledObjectCategory.POINT
                || category == InstalledObjectCategory.TICKET_VENDOR;
        if (topOnly && clickedFace != net.minecraft.core.Direction.UP) {
            return InteractionResult.PASS;
        }
        BlockPos placePos = signal
                ? context.getClickedPos()
                : context.getClickedPos().relative(clickedFace);
        BlockState state = level.getBlockState(placePos);
        if (!signal && !state.canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        // 本家 ItemSignal: `if (target == RTMBlock.signal) return true;`
        // = 置こうとした場所が<b>既に信号</b>のときだけ何もしない。
        // ★架線柱 (OVERHEAD_LINE_POLE) など他の設置物は<b>置き換え対象</b>なので拒否しない
        //   (本家はクリックした柱ブロックを信号に置き換える)。
        if (signal
                && level.getBlockEntity(placePos) instanceof InstalledObjectBlockEntity existing
                && existing.getCategory() == InstalledObjectCategory.SIGNAL) {
            return InteractionResult.FAIL;
        }
        // クリックした面で設置向きを決める(踏切などの設置系共通)。
        // ・ブロック下面(天井)に付けた → 逆さ(180°)
        // ・横面(壁)に付けた          → 横倒し(90°)、面から外向き(プレイヤー側)
        // ・上面/通常                 → プレイヤー向き(縦置き)
        // WIRE は専用描画、SIGNAL は柱への押し込み挙動を維持するため対象外。
        float placeYaw = player.getYRot();
        float placeMountPitch = 0.0F;
        // 碍子/看板: 本家 ItemInstalledObject 準拠 — クリック面 (meta 0-5) だけを保存し、
        // 描画は本家と同じ (ブロック中心ピボット+面回転)。
        // 持ち上げ/横倒しハックは廃止 (当たり判定に対してモデルがずれる原因だった)。
        //★コネクタ入出力も本家は meta = クリック面 (BlockUtil.setBlock(..., sideIndex, 3))。
        //  レンダラは isConnectorCategory で面回転を読むのに、ここで MountFace を保存して
        //  いなかったため常に既定 1 (上向き) になり、壁に横向きで付けられなかった。
        boolean honkeFaceMount = category == InstalledObjectCategory.INSULATOR
                || category == InstalledObjectCategory.SIGNBOARD
                || category == InstalledObjectCategory.CONNECTOR_INPUT
                || category == InstalledObjectCategory.CONNECTOR_OUTPUT;
        // 本家で常に直立している設置物 (転轍機/券売機/標識)。壁挿し・逆さ設置はさせず、
        // 本家 setRotation(player, 15.0F, ...) と同じく向きを 15 度刻みに丸めるだけ。
        boolean uprightOnly = category == InstalledObjectCategory.POINT
                || category == InstalledObjectCategory.TICKET_VENDOR
                || category == InstalledObjectCategory.RAILROAD_SIGN;
        // 照明 (本家 LIGHT + rotateByMetadata、サーチライト等): 本家 ItemInstalledObject は
        // setBlock(..., sideIndex=クリック面, 3) + setRotation(player, 15.0F, true)。
        // だけを保存し、向きは 15 度刻みに丸める。
        // ★rotateByMetadata は本家では「機械共通の設定フラグ」。カテゴリで絞らない。
        // 以前は照明だけ通していたので、同じフラグを持つスピーカーが面回転を受けず、
        // 本家と違う場所・向きに描かれていた (同梱で該当するのは照明4種とスピーカー2種)。
        boolean rotateByMeta = definition.isRotateByMetadata();
        // 蛍光灯: 本家 ItemInstalledObject は取付方向 (0..7) だけを持たせ、平行移動と回転は
        // レンダースクリプト側でやる。汎用の壁挿し/逆さ設置には乗せない。
        boolean fluorescent = category == InstalledObjectCategory.FLUORESCENT;
        // 架線柱: 本家 ItemInstalledObject は LINEPOLE に setRotation を一切呼ばず、
        // ブロックと同じくグリッドに揃えて置くだけ。
        // partXP / partXN / partZP / partZN をワールド軸で出し分けるため、少しでも回すと
        // 腕の向きが実際の接続方向とずれる。よって斜め置きも壁挿しもさせない。
        boolean gridAligned = category == InstalledObjectCategory.OVERHEAD_LINE_POLE
                || category == InstalledObjectCategory.PIPE;
        // 列車検知器・車止め・ATC 地上子: 本家 ItemInstalledObject.setEntityOnRail 準拠で、クリックした
        // レールの曲線上に載せる (位置・向き・勾配・カント)。汎用の壁挿し/逆さ設置には乗せない。
        // ATC 地上子は真下のレールに信号を書くので、検知器と同じくレールに吸着させる。
        boolean railMounted = category == InstalledObjectCategory.TRAIN_DETECTOR
                || category == InstalledObjectCategory.BUMPING_POST
                || category == InstalledObjectCategory.ATC;
        RailSnap railSnap = railMounted
                ? computeRailSnap(level, context.getClickedPos(), placePos, player)
                : null;
        // 本家 setEntityOnRail: 上面クリック (par7==1) でレール上に載るときだけ設置する。
        if (railMounted && (clickedFace != net.minecraft.core.Direction.UP || railSnap == null)) {
            return InteractionResult.PASS;
        }
        // ★本家準拠: 壁面/天面でも横倒し・逆さにせず、常に直立。
        //   向きは本家 setRotation(player, interval) と同じで、カテゴリごとの刻みで丸める。
        if (railSnap != null) {
            placeYaw = railSnap.yaw();
            placeMountPitch = railSnap.pitch();
        } else if (category == InstalledObjectCategory.SIGNAL) {
            // 本家 ItemSignal: dir = クリック面 → dir*90 度
            placeYaw = signalYaw(clickedFace, player);
        } else if (category == InstalledObjectCategory.WIRE || gridAligned || fluorescent) {
            placeYaw = 0.0F;
        } else if (category == InstalledObjectCategory.TICKET_GATE) {
            // 本家 TileEntityTurnstile: setRotation(player, 90.0F)
            placeYaw = honkeRotation(player, 90.0F);
        } else if (category == InstalledObjectCategory.SCAFFOLD
                || category == InstalledObjectCategory.STAIR) {
            // 本家 onBlockPlacedBy: プレイヤーの向きを 4 方位に丸める
            placeYaw = vanillaDirYaw(player);
        } else {
            // 本家 setRotation(player, 15.0F)
            placeYaw = honkeRotation(player);
        }
        // ★本家: ATC / 列車検知器 / 車止め は設置物<b>ブロックではなくエンティティ</b>
        //   (EntityInstalledObject) としてレール上に出す。定義モデルは同じものを使う。
        jp.ngt.rtm.entity.EntityInstalledObject wiringEntity = createWiringEntity(category, level);
        if (wiringEntity != null) {
            if (!level.isClientSide) {
                wiringEntity.setModelName(definition.getId());
                // ★本家 ItemInstalledObject.setEntityOnRail:
                //     double posX = rm.getRailPos(split, i)[1];
                //     double posY = rm.getRailHeight(split, i) + 0.0625D;
                //     double posZ = rm.getRailPos(split, i)[0];
                //     entity.setPosition(posX, posY, posZ);
                //   引数の y (par5 - 1) は getRailMapFromCoordinates でレールを引くためだけに使い、
                //   最終座標はレールマップから直接出す。つまりレール面 + 1/16 が絶対座標。
                //   computeRailSnap は placePos からの相対値 (off = pos - placePos) を返すので、
                //   placePos + off が本家の posX/posY/posZ と厳密に一致する。
                //   (以前はここで -1.0 していたため posY - 1.0 = 1 ブロック埋まっていた)
                double offX = railSnap != null ? railSnap.offX() : 0.0D;
                double offY = railSnap != null ? railSnap.offY() : 0.0D;
                double offZ = railSnap != null ? railSnap.offZ() : 0.0D;
                wiringEntity.moveTo(placePos.getX() + 0.5D + offX, placePos.getY() + offY,
                    placePos.getZ() + 0.5D + offZ, placeYaw, 0.0F);
                level.addFreshEntity(wiringEntity);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            // 本家 TileEntitySignal.origTileEntity: 置き換える元タイルの NBT を setBlock の前に退避する
            // (架線柱/碍子の配線を、信号を壊して元に戻したとき失わないため)。
            net.minecraft.nbt.CompoundTag signalOrigTileNbt = null;
            if (signal && level.getBlockEntity(placePos)
                    instanceof net.minecraft.world.level.block.entity.BlockEntity old) {
                signalOrigTileNbt = old.saveWithFullMetadata(level.registryAccess());
            }
            level.setBlock(placePos, RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof InstalledObjectBlockEntity blockEntity) {
                blockEntity.setDefinition(definition.getId(), category, placeYaw);
                blockEntity.setMountPitch(placeMountPitch);
                if (signal) {
                    // 本家 TileEntitySignal.setOrigBlock: 置き換えた元ブロック (柱など) を覚えておき、
                    // 信号を壊したら元に戻す。
                    blockEntity.setSignalOrigBlock(state);
                    if (signalOrigTileNbt != null) {
                        blockEntity.setSignalOrigTileNbt(signalOrigTileNbt);
                    }
                    // 本家 TileEntitySignal.setRotation(player, スニーク?1:15):
                    // ヘッドだけプレイヤー向きに斜めに置ける (柱はクリック面の4方位のまま)。
                    blockEntity.setSignalBodyYaw(honkeRotation(player));
                }
                if (railSnap != null) {
                    // レール曲線上の点にモデルを載せる (レンダラの原点は placePos + (0.5, 0, 0.5))
                    blockEntity.setRenderOffset(railSnap.offX(), railSnap.offY(), railSnap.offZ());
                    blockEntity.setMountRoll(railSnap.roll());
                } else if (fluorescent) {
                    // 本家 ItemInstalledObject の蛍光灯: 取付方向 (0..7) だけを持たせる。
                    // RenderFluorescent.js が getDir を読んで自分で寄せて回す。
                    blockEntity.setFluorescentDir(fluorescentDir(clickedFace, player.getYRot()));
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                } else if (rotateByMeta) {
                    // 本家 meta = クリック面 (0-5)。RenderMachine と同じ面回転+向きで描くための保存。
                    blockEntity.setMountFace(clickedFace.ordinal());
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                } else if (honkeFaceMount) {
                    // 本家 meta = クリック面 (1.7.10 side と 1.21 Direction.ordinal は同一)
                    blockEntity.setMountFace(clickedFace.ordinal());
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                    //★本家 ItemInstalledObject: コネクタはクリックしたブロックへ DIRECT 接続を張る。
                    //  出力コネクタはそこから信号を読み、入力コネクタはそこへ信号を書く。
                    if (category == InstalledObjectCategory.CONNECTOR_INPUT
                            || category == InstalledObjectCategory.CONNECTOR_OUTPUT) {
                        blockEntity.setDirectTarget(context.getClickedPos());
                    }
                    if (category == InstalledObjectCategory.SIGNBOARD) {
                        // 本家 ItemInstalledObject: direction = 設置したプレイヤーの向き (0-3)。
                        blockEntity.setSignDirection(signDirectionOf(player.getYRot()));
                    }
                } else if (category == InstalledObjectCategory.PIPE) {
                    // パイプ: クリック面 (0-5) を保存する。
                    // 真っ直ぐ描き、RenderConnectablePipe.js はその面へ向かう腕を出す。
                    blockEntity.setMountFace(clickedFace.ordinal());
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                } else {
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                }
                level.sendBlockUpdated(placePos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 本家 ItemInstalledObject の蛍光灯の取付方向 (TileEntityFluorescent.dirF, 0..7)。
     * クリックした面で「どこに貼るか」が決まり、天井/床のときだけプレイヤーの向きで
     * 蛍光灯を走らせる軸 (Z か X か) が決まる。
     */
    private static byte fluorescentDir(net.minecraft.core.Direction clickedFace, float playerYaw) {
        // 本家: floor(yaw * 4 / 360 + 0.5) & 3 — プレイヤーの向きを4方位に丸める。
        // 偶数 (南/北) なら Z 軸、奇数 (西/東) なら X 軸に蛍光灯を寝かせる。
        int quadrant = net.minecraft.util.Mth.floor(playerYaw * 4.0F / 360.0F + 0.5F) & 3;
        boolean alongZ = quadrant == 0 || quadrant == 2;
        return switch (clickedFace) {
            case DOWN -> (byte) (alongZ ? 0 : 4);   //天井から吊る
            case UP -> (byte) (alongZ ? 2 : 6);     //床に置く
            case NORTH -> (byte) 1;
            case SOUTH -> (byte) 3;
            case WEST -> (byte) 5;
            case EAST -> (byte) 7;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        //本家 usage.item.istlobj.connector_in / connector_out
        if (category == InstalledObjectCategory.CONNECTOR_INPUT) {
            lines.add(Component.translatable("usage.realtrainmodunofficial.connector_in")
                .withStyle(ChatFormatting.GRAY));
        } else if (category == InstalledObjectCategory.CONNECTOR_OUTPUT) {
            lines.add(Component.translatable("usage.realtrainmodunofficial.connector_out")
                .withStyle(ChatFormatting.GRAY));
        }
        String selectedId = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedModelId(stack);
        if (selectedId != null && !selectedId.isBlank()) {
            InstalledObjectDefinition def = InstalledObjectRegistry.getById(selectedId);
            String name = def != null ? def.getDisplayName() : selectedId;
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.model.selected", name).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.realtrainmodunofficial.model.none").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public List<SelectableModelInfo> getSelectableModels() {
        return InstalledObjectRegistry.getByCategory(category).stream()
            .map(def -> new SelectableModelInfo(def.getId(), def.getDisplayName(), def.getPackName(), def.getButtonTexture()))
            .toList();
    }
}
