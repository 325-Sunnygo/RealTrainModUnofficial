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

// jp.ngt.rtm.item.ItemInstalledObject を継承し、NGTO Bui…
public class InstalledObjectItem extends jp.ngt.rtm.item.ItemInstalledObject implements ModelSelectableItem {
    private final InstalledObjectCategory category;

    public InstalledObjectItem(InstalledObjectCategory category) {
        super(new Properties());
        this.category = category;
    }

    /** 本家 KaizPatchX の customIconTexture 用の描画器。 */
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

    // --- NGTO Builder の Wire ツール互換 (本家 ItemWithModel の…

    /** 選択中モデルの bare name (本家の定義名)。 */
    public String getModelName(Object stackLike) {
        InstalledObjectDefinition def = selectedDefinition(stackLike);
        return def == null ? "" : def.getBareName();
    }

    /** 選択中モデルの connectorType ("Relay" 等)。 */
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

    /** レールに載せる設置物 (列車検知器) の姿勢。 */
    private record RailSnap(float yaw, float pitch, float roll, double offX, double offY, double offZ) {
    }

    /** 本家 ItemInstalledObject.setEntityOnRail の移植。 */
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

        // 本家: プレイヤーの向きとレールの向きが 90°以上ずれていたら 180°反転させる (勾配とカン…
        float railYaw = rm.getRailYaw(split, index);
        float playerFacing = -player.getYRot() + 180.0F;
        boolean invert = Math.abs(net.minecraft.util.Mth.wrapDegrees(railYaw - playerFacing)) > 90.0F;
        float sign = invert ? -1.0F : 1.0F;
        if (invert) {
            railYaw += 180.0F;
        }

        // 本家 ItemInstalledObject.setEntityOnRail と同じく、レールの…
        float yaw = railYaw;
        float pitch = -rm.getRailPitch(split, index) * sign;
        float roll = rm.getRailRoll(split, index) * sign;

        return new RailSnap(yaw, pitch, roll,
                posX - (placePos.getX() + 0.5D),
                posY - placePos.getY(),
                posZ - (placePos.getZ() + 0.5D));
    }

    /** 本家 TileEntityPlaceable.setRotation(player, interv… */
    /** 本家でエンティティ実装になっている設置物 (ATC / 列車検知器 / 車止め) のエンティティを… */
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

    /** 本家 ItemWithModel.getModelState: アイテムに保存された DataMa… */
    private static void applySelectedState(ItemStack stack, jp.ngt.rtm.modelpack.state.ResourceState state) {
        if (state == null) {
            return;
        }
        String dataMap = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedDataMap(stack);
        if (dataMap != null && !dataMap.isBlank()) {
            state.setArg(dataMap, true);
        }
        state.color = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedColor(stack);
        String customName = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedCustomName(stack);
        if (customName != null && !customName.isBlank()) {
            state.setName(customName);
        }
    }

    /** ブロック設置物版: scriptData (DataMap) と色を引き継ぐ。 */
    private static void applySelectedStateToBlock(ItemStack stack,
                                                  com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity blockEntity) {
        if (blockEntity == null) {
            return;
        }
        String dataMap = com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedDataMap(stack);
        if (dataMap != null && !dataMap.isBlank()) {
            for (String[] entry : jp.ngt.rtm.modelpack.state.DataMap.convertArg(dataMap)) {
                if (entry != null && entry.length >= 3) {
                    blockEntity.putScriptData(entry[0], entry[2]);
                }
            }
        }
        blockEntity.setWireColor(
                com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge.getSelectedColor(stack));
    }

    private static float honkeRotation(net.minecraft.world.entity.player.Player player) {
        float interval = player.isShiftKeyDown() ? 1.0F : 15.0F;
        return honkeRotation(player, interval);
    }

    /** 本家 setRotation(player, interval) の刻みを指定できる版 (改札は… */
    private static float honkeRotation(net.minecraft.world.entity.player.Player player, float interval) {
        double a = jp.ngt.ngtlib.math.NGTMath.normalizeAngle(
                -player.getYRot() + 180.0D + (interval / 2.0D));
        int steps = net.minecraft.util.Mth.floor(a / interval);
        return (float) steps * interval;
    }

    /** 本家 Block.onBlockPlacedBy と同じ 4 方位。 */
    private static float vanillaDirYaw(net.minecraft.world.entity.player.Player player) {
        int dir = net.minecraft.util.Mth.floor(player.getYRot() * 4.0F / 360.0F + 0.5F) & 3;
        return dir * 90.0F;
    }

    /** 信号の向き。本家 ItemSignal はプレイヤーの向きではなくクリックした面で決める。 */
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

    /** 本家 ItemInstalledObject の看板向き: floor(normalizeAngl… */
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
        // コンポーネントが失われても CUSTOM_DATA から復元する(碍子等の選択がワールド再入場で…
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
        // ★本家 ItemSignal: クリックしたブロック自体を信号に置き換える (隣の空気ではない)。
        if (signal && clickedFace.getAxis().isVertical()) {
            return InteractionResult.PASS;
        }
        // 本家 ItemInstalledObject: 踏切/転轍機/券売機は上面クリック (par7==…
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
        // 本家 ItemSignal: `if (target == RTMBlock.signal) re…
        if (signal
                && level.getBlockEntity(placePos) instanceof InstalledObjectBlockEntity existing
                && existing.getCategory() == InstalledObjectCategory.SIGNAL) {
            return InteractionResult.FAIL;
        }
        // クリックした面で設置向きを決める(踏切などの設置系共通)。
        float placeYaw = player.getYRot();
        float placeMountPitch = 0.0F;
        // 碍子/看板: 本家 ItemInstalledObject 準拠 — クリック面 (meta 0-…
        boolean honkeFaceMount = category == InstalledObjectCategory.INSULATOR
                || category == InstalledObjectCategory.SIGNBOARD
                || category == InstalledObjectCategory.CONNECTOR_INPUT
                || category == InstalledObjectCategory.CONNECTOR_OUTPUT;
        // 本家で常に直立している設置物 (転轍機/券売機/標識)。
        boolean uprightOnly = category == InstalledObjectCategory.POINT
                || category == InstalledObjectCategory.TICKET_VENDOR
                || category == InstalledObjectCategory.RAILROAD_SIGN;
        // 照明 (本家 LIGHT + rotateByMetadata、サーチライト等): 本家 Item…
        boolean rotateByMeta = definition.isRotateByMetadata();
        // 蛍光灯: 本家 ItemInstalledObject は取付方向 (0..7) だけを持たせ、平…
        boolean fluorescent = category == InstalledObjectCategory.FLUORESCENT;
        // 架線柱: 本家 ItemInstalledObject は LINEPOLE に setRotat…
        boolean gridAligned = category == InstalledObjectCategory.OVERHEAD_LINE_POLE
                || category == InstalledObjectCategory.PIPE;
        // 列車検知器・車止め・ATC 地上子: 本家 ItemInstalledObject.setEnti…
        boolean railMounted = category == InstalledObjectCategory.TRAIN_DETECTOR
                || category == InstalledObjectCategory.BUMPING_POST
                || category == InstalledObjectCategory.ATC;
        RailSnap railSnap = railMounted
                ? computeRailSnap(level, context.getClickedPos(), placePos, player)
                : null;
        // 本家 setEntityOnRail: 上面クリック (par7==1) でレール上に載るときだけ…
        if (railMounted && (clickedFace != net.minecraft.core.Direction.UP || railSnap == null)) {
            return InteractionResult.PASS;
        }
        // ★本家準拠: 壁面/天面でも横倒し・逆さにせず、常に直立。
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
        // ★本家: ATC / 列車検知器 / 車止め は設置物ブロックではなくエンティティ (Entity…
        jp.ngt.rtm.entity.EntityInstalledObject wiringEntity = createWiringEntity(category, level);
        if (wiringEntity != null) {
            if (!level.isClientSide) {
                wiringEntity.setModelName(definition.getId());
                // ★本家 ItemInstalledObject.setEntityOnRail: double p…
                double offX = railSnap != null ? railSnap.offX() : 0.0D;
                double offY = railSnap != null ? railSnap.offY() : 0.0D;
                double offZ = railSnap != null ? railSnap.offZ() : 0.0D;
                float placePitch = railSnap != null ? railSnap.pitch() : 0.0F;
                float placeRoll = railSnap != null ? railSnap.roll() : 0.0F;
                wiringEntity.moveTo(placePos.getX() + 0.5D + offX, placePos.getY() + offY,
                    placePos.getZ() + 0.5D + offZ, placeYaw, placePitch);
                // 本家 ItemInstalledObject.setEntityOnRail: rotationR…
                wiringEntity.rotationRoll = placeRoll;
                applySelectedState(stack, wiringEntity.getResourceState());
                level.addFreshEntity(wiringEntity);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            // 本家 TileEntitySignal.origTileEntity: 置き換える元タイルの NB…
            net.minecraft.nbt.CompoundTag signalOrigTileNbt = null;
            if (signal && level.getBlockEntity(placePos)
                    instanceof net.minecraft.world.level.block.entity.BlockEntity old) {
                signalOrigTileNbt = old.saveWithFullMetadata(level.registryAccess());
            }
            level.setBlock(placePos, RealTrainModUnofficialBlocks.INSTALLED_OBJECT.get().defaultBlockState(), 3);
            if (level.getBlockEntity(placePos) instanceof InstalledObjectBlockEntity blockEntity) {
                blockEntity.setDefinition(definition.getId(), category, placeYaw);
                applySelectedStateToBlock(stack, blockEntity);
                blockEntity.setMountPitch(placeMountPitch);
                if (signal) {
                    // 本家 TileEntitySignal.setOrigBlock: 置き換えた元ブロック (柱など…
                    blockEntity.setSignalOrigBlock(state);
                    if (signalOrigTileNbt != null) {
                        blockEntity.setSignalOrigTileNbt(signalOrigTileNbt);
                    }
                    // 本家 TileEntitySignal.setRotation(player, スニーク?1:15…
                    blockEntity.setSignalBodyYaw(honkeRotation(player));
                }
                if (railSnap != null) {
                    // レール曲線上の点にモデルを載せる (レンダラの原点は placePos + (0.5, 0, 0.…
                    blockEntity.setRenderOffset(railSnap.offX(), railSnap.offY(), railSnap.offZ());
                    blockEntity.setMountRoll(railSnap.roll());
                } else if (fluorescent) {
                    // 本家 ItemInstalledObject の蛍光灯: 取付方向 (0..7) だけを持たせる。
                    blockEntity.setFluorescentDir(fluorescentDir(clickedFace, player.getYRot()));
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                } else if (rotateByMeta) {
                    // 本家 meta = クリック面 (0-5)。
                    blockEntity.setMountFace(clickedFace.ordinal());
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                } else if (honkeFaceMount) {
                    // 本家 meta = クリック面 (1.7.10 side と 1.21 Direction.ord…
                    blockEntity.setMountFace(clickedFace.ordinal());
                    blockEntity.setRenderOffset(0.0D, 0.0D, 0.0D);
                    // ★本家 ItemInstalledObject: コネクタはクリックしたブロックへ DIRECT…
                    if (category == InstalledObjectCategory.CONNECTOR_INPUT
                            || category == InstalledObjectCategory.CONNECTOR_OUTPUT) {
                        blockEntity.setDirectTarget(context.getClickedPos());
                    }
                    if (category == InstalledObjectCategory.SIGNBOARD) {
                        // 本家 ItemInstalledObject: direction = 設置したプレイヤーの向き…
                        blockEntity.setSignDirection(signDirectionOf(player.getYRot()));
                    }
                } else if (category == InstalledObjectCategory.PIPE) {
                    // パイプ: クリック面 (0-5) を保存する。
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

    /** 本家 ItemInstalledObject の蛍光灯の取付方向 (TileEntityFluor… */
    private static byte fluorescentDir(net.minecraft.core.Direction clickedFace, float playerYaw) {
        // 本家: floor(yaw * 4 / 360 + 0.5) & 3 — プレイヤーの向きを4方位…
        int quadrant = net.minecraft.util.Mth.floor(playerYaw * 4.0F / 360.0F + 0.5F) & 3;
        boolean alongZ = quadrant == 0 || quadrant == 2;
        return switch (clickedFace) {
            case DOWN -> (byte) (alongZ ? 0 : 4);   // 天井から吊る
            case UP -> (byte) (alongZ ? 2 : 6);     // 床に置く
            case NORTH -> (byte) 1;
            case SOUTH -> (byte) 3;
            case WEST -> (byte) 5;
            case EAST -> (byte) 7;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        // 本家 usage.item.istlobj.connector_in / connector_out
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
