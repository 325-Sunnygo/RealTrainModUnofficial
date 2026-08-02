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
    // 壁(横倒し)設置で上へ持ち上げる量(ブロック単位)。上げ足りない/上げすぎなら数値を調整する。
    private static final double WALL_MOUNT_RAISE = 0.5D;
    // 逆さ(180°)設置で上へ持ち上げる量(ブロック単位)。天井から吊るす高さ調整用。
    private static final double UPSIDE_DOWN_RAISE = 1.0D;

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
    private static float honkeRotation(net.minecraft.world.entity.player.Player player) {
        float interval = player.isShiftKeyDown() ? 1.0F : 15.0F;
        double a = jp.ngt.ngtlib.math.NGTMath.normalizeAngle(
                -player.getYRot() + 180.0D + (interval / 2.0D));
        int steps = net.minecraft.util.Mth.floor(a / interval);
        return (float) steps * interval;
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
        BlockPos placePos = context.getClickedPos().relative(clickedFace);
        BlockState state = level.getBlockState(placePos);
        if (!state.canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        // クリックした面で設置向きを決める(踏切などの設置系共通)。
        // ・ブロック下面(天井)に付けた → 逆さ(180°)
        // ・横面(壁)に付けた          → 横倒し(90°)、面から外向き(プレイヤー側)
        // ・上面/通常                 → プレイヤー向き(縦置き)
        // WIRE は専用描画、SIGNAL は柱への押し込み挙動を維持するため対象外。
        float placeYaw = player.getYRot();
        float placeMountPitch = 0.0F;
        boolean wallMounted = false;
        boolean upsideDown = false;
        // 碍子/看板: 本家 ItemInstalledObject 準拠 — クリック面 (meta 0-5) だけを保存し、
        // 描画は本家と同じ (ブロック中心ピボット+面回転)。
        // 持ち上げ/横倒しハックは廃止 (当たり判定に対してモデルがずれる原因だった)。
        boolean honkeFaceMount = category == InstalledObjectCategory.INSULATOR
                || category == InstalledObjectCategory.SIGNBOARD;
        // 本家で常に直立している設置物 (転轍機/券売機/標識)。壁挿し・逆さ設置はさせず、
        // 本家 setRotation(player, 15.0F, ...) と同じく向きを 15 度刻みに丸めるだけ。
        boolean uprightOnly = category == InstalledObjectCategory.POINT
                || category == InstalledObjectCategory.TICKET_VENDOR
                || category == InstalledObjectCategory.RAILROAD_SIGN;
        // 照明 (本家 LIGHT + rotateByMetadata、サーチライト等): 本家 ItemInstalledObject は
        // setBlock(..., sideIndex=クリック面, 3) + setRotation(player, 15.0F, true)。
        // だけを保存し、向きは 15 度刻みに丸める。
        boolean lightRotateByMeta = category == InstalledObjectCategory.LIGHT
                && definition.isRotateByMetadata();
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
        if (railSnap != null) {
            placeYaw = railSnap.yaw();
            placeMountPitch = railSnap.pitch();
        } else if (uprightOnly || lightRotateByMeta) {
            placeYaw = honkeRotation(player);
        } else if (fluorescent || gridAligned) {
            placeYaw = 0.0F;
        } else if (!honkeFaceMount && !railMounted
                && category != InstalledObjectCategory.WIRE && category != InstalledObjectCategory.SIGNAL) {
            if (clickedFace == net.minecraft.core.Direction.DOWN) {
                upsideDown = true;
                placeMountPitch = 180.0F;
            } else if (clickedFace.getAxis().isHorizontal()) {
                wallMounted = true;
                placeYaw = clickedFace.getOpposite().toYRot();
                placeMountPitch = 90.0F;
            }
        }
        // 設置物 (改札機/踏切/券売機/標識/看板/照明 等) の向き:
        // 通常設置 (Shift なし) = バニラの立て看板と同じ 22.5 度刻み (16 方向) にスナップ。
        if (railSnap == null && !gridAligned && !fluorescent && !wallMounted
                && category != InstalledObjectCategory.SIGNAL
                && category != InstalledObjectCategory.WIRE) {
            placeYaw = honkeRotation(player);
        }
        if (!level.isClientSide) {
            level.setBlock(placePos, RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof InstalledObjectBlockEntity blockEntity) {
                blockEntity.setDefinition(definition.getId(), category, placeYaw);
                blockEntity.setMountPitch(placeMountPitch);
                if (railSnap != null) {
                    // レール曲線上の点にモデルを載せる (レンダラの原点は placePos + (0.5, 0, 0.5))
                    blockEntity.setRenderOffset(railSnap.offX(), railSnap.offY(), railSnap.offZ());
                    blockEntity.setMountRoll(railSnap.roll());
                } else if (fluorescent) {
                    // 本家 ItemInstalledObject の蛍光灯: 取付方向 (0..7) だけを持たせる。
                    // RenderFluorescent.js が getDir を読んで自分で寄せて回す。
                    blockEntity.setFluorescentDir(fluorescentDir(clickedFace, player.getYRot()));
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                } else if (lightRotateByMeta) {
                    // 本家 meta = クリック面 (0-5)。RenderMachine と同じ面回転+向きで描くための保存。
                    blockEntity.setMountFace(clickedFace.ordinal());
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                } else if (honkeFaceMount) {
                    // 本家 meta = クリック面 (1.7.10 side と 1.21 Direction.ordinal は同一)
                    blockEntity.setMountFace(clickedFace.ordinal());
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                    if (category == InstalledObjectCategory.SIGNBOARD) {
                        // 本家 ItemInstalledObject: direction = 設置したプレイヤーの向き (0-3)。
                        blockEntity.setSignDirection(signDirectionOf(player.getYRot()));
                    }
                } else if (category == InstalledObjectCategory.PIPE) {
                    // パイプ: クリック面 (0-5) を保存する。
                    // 真っ直ぐ描き、RenderConnectablePipe.js はその面へ向かう腕を出す。
                    blockEntity.setMountFace(clickedFace.ordinal());
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                } else if (category == InstalledObjectCategory.SIGNAL) {
                    // 当たり判定はそのままで、見た目だけ「クリックした柱」の中へ押し込む。
                    // 本家は信号が柱ブロックを置き換えてそこに描くため、横面設置のみ押し込む。
                    if (clickedFace.getAxis().isHorizontal()) {
                        double yawRad = Math.toRadians(player.getYRot());
                        double faceX = context.getClickedFace().getStepX();
                        double faceZ = context.getClickedFace().getStepZ();
                        double facingDot = Math.abs((-Math.sin(yawRad) * faceX) + (Math.cos(yawRad) * faceZ));
                        double embedDepth = facingDot < 0.85D ? 0.905D : 0.92D;
                        blockEntity.setRenderOffset(-faceX * embedDepth, 0.0D, -faceZ * embedDepth);
                    } else {
                        blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                    }
                } else if (upsideDown) {
                    // 逆さ(180°)は反転でモデルが下へ出るので、1ブロック持ち上げて天井から吊るす。
                    blockEntity.setRenderOffset(0.0D, UPSIDE_DOWN_RAISE, 0.0D);
                } else if (wallMounted) {
                    // 横倒し(90°)でモデルが下にずれるので、少し上へ持ち上げる(接続点も一緒に上がる)。
                    blockEntity.setRenderOffset(0.0D, WALL_MOUNT_RAISE, 0.0D);
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
