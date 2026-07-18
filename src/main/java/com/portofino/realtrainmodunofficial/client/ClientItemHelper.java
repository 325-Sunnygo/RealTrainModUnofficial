package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialComponents;
import com.portofino.realtrainmodunofficial.client.screen.ModelSelectScreen;
import com.portofino.realtrainmodunofficial.client.screen.TrainFormationScreen;
import com.portofino.realtrainmodunofficial.compat.LegacyItemStackBridge;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectRegistry;
import com.portofino.realtrainmodunofficial.network.SelectModelPayload;
import com.portofino.realtrainmodunofficial.item.TrainItem;
import com.portofino.realtrainmodunofficial.rail.RailRegistry;
import com.portofino.realtrainmodunofficial.vehicle.VehicleDefinition;
import com.portofino.realtrainmodunofficial.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Comparator;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class ClientItemHelper {
    private static final String HIDDEN_TRAIN_PACK = "basic_train";

    private ClientItemHelper() {}

    /**
     * 選択を <b>サーバーへペイロード送信するだけ</b>。クライアント側の手持ちスタックには書かない。
     * <p>
     * <b>重要 (回帰対策):</b> 以前はここでクライアント側スタックにも
     * {@link LegacyItemStackBridge#setSelectedModelData} で書き込んでいたが、これが
     * <b>Bukkit系ハイブリッド専用サーバー (Youer/Mohist 等) のクリエイティブで全列車が
     * 223系5000番台Tc になる回帰</b>を起こした (1.0.8 で混入)。クライアント手持ちを書き換えると
     * クリエイティブのスロット同期 (SetCreativeModeSlot) が走り、その版が Bukkit の ItemStack 変換で
     * カスタムコンポーネントを剥がされたままサーバー側スタックを上書きし、選択が消えて既定
     * (先頭の ELECTRIC 車両 = 223系5000番台Tc) にフォールバックする。クライアントを書かなければ
     * この同期が誘発されず、ペイロードで確定したサーバー側の選択 (ServerVehicleSelection および
     * サーバースタックの custom_data) が生き残る。自動車 (payload のみ) が正常なのと同じ経路。
     * 1.0.7 の挙動に戻す。
     */
    private static void commitSelection(ItemStack stack, ModelSelectScreen.SelectionResult selection) {
        PacketDistributor.sendToServer(new SelectModelPayload(selection.modelId(), selection.dataMapValue()));
    }

    /** 手持ち (メイン→オフ) から選択式アイテムのスタックを探す。引数なし選択画面用。 */
    private static ItemStack findHeldSelectableStack() {
        Player p = Minecraft.getInstance().player;
        if (p == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = p.getMainHandItem();
        if (isSelectable(main)) {
            return main;
        }
        ItemStack off = p.getOffhandItem();
        if (isSelectable(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isSelectable(ItemStack s) {
        return s != null && !s.isEmpty() && (
            s.getItem() instanceof TrainItem
            || s.getItem() instanceof com.portofino.realtrainmodunofficial.item.RailItem
            || s.getItem() instanceof com.portofino.realtrainmodunofficial.item.CarItem
            || s.getItem() instanceof com.portofino.realtrainmodunofficial.item.ModelSelectableItem);
    }

    public static void openRailSelectScreen(Player player, ItemStack stack) {
        List<ModelSelectScreen.ModelInfo> infos = RailRegistry.getAll().stream()
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName(), d.getPackName(), d.getButtonTexture()))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.realtrainmodunofficial.select_rail"),
            infos,
            selection -> commitSelection(stack, selection),
            LegacyItemStackBridge.getSelectedModelId(stack),
            LegacyItemStackBridge.getSelectedDataMap(stack)
        ));
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack) {
        openTrainSelectScreen(player, stack, TrainItem.Category.ELECTRIC);
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack, TrainItem.Category category) {
        List<ModelSelectScreen.ModelInfo> infos = getVisibleTrainModels();
        infos = infos.stream()
            .filter(info -> TrainItem.accepts(category, VehicleRegistry.getById(info.id())))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.realtrainmodunofficial.select_train"),
            infos,
            selection -> commitSelection(stack, selection),
            LegacyItemStackBridge.getSelectedModelId(stack),
            LegacyItemStackBridge.getSelectedDataMap(stack)
        ));
    }

    public static void openTrainSelectScreen(TrainFormationScreen formationScreen) {
        List<ModelSelectScreen.ModelInfo> infos = getVisibleTrainModels();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.realtrainmodunofficial.select_train"),
            infos,
            selection -> {
                formationScreen.updateFormationWithVehicle(selection.modelId());
            },
            null,
            ""
        ));
    }

    public static void openTrainSelectScreen() {
        List<ModelSelectScreen.ModelInfo> infos = getVisibleTrainModels();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.realtrainmodunofficial.select_train"),
            infos,
            selection -> commitSelection(findHeldSelectableStack(), selection),
            null,
            ""
        ));
    }

    private static List<ModelSelectScreen.ModelInfo> getVisibleTrainModels() {
        // basic_train は初期確認用の内蔵車両なので、通常の選択画面には出さない。
        return VehicleRegistry.getAll().stream()
            .filter(ClientItemHelper::shouldShowTrainModel)
            .sorted(Comparator
                .comparing((VehicleDefinition d) -> safe(d.getVehicleType()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(d -> safe(d.getDisplayName()), String.CASE_INSENSITIVE_ORDER))
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName(), d.getPackName(), d.getButtonTexture(), d.getVehicleType()))
            .toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean shouldShowTrainModel(VehicleDefinition definition) {
        if (definition == null) {
            return false;
        }
        if (definition.isCarType()) {
            return false;
        }
        // 列車判定の本質: 台車(bogies)を持つこと。RTM の Wire/Insulator/Pipe/Signal/転轍機 等は
        // 台車を持たないため、これで非列車を一括除外できる。
        if (definition.getBogies() == null || definition.getBogies().isEmpty()) {
            return false;
        }
        // 内蔵の basic_train 系は packName だけでなく id / displayName 由来で残る場合がある。
        String packName = definition.getPackName() == null ? "" : definition.getPackName();
        String id = definition.getId() == null ? "" : definition.getId().toLowerCase(Locale.ROOT);
        String displayName = definition.getDisplayName() == null ? "" : definition.getDisplayName();
        String displayNameLower = displayName.toLowerCase(Locale.ROOT);
        String buttonTexture = definition.getButtonTexture() == null ? "" : definition.getButtonTexture().toLowerCase(Locale.ROOT);
        // [RTM]SL_D51_v1.2 contains stale DD51-498 entries whose button textures are not bundled.
        // The pack author confirmed they are not intended selectable vehicles.
        if ((id.startsWith("dd51-498") || displayNameLower.startsWith("dd51-498"))
                && buttonTexture.contains("button_dd51-498")) {
            return false;
        }
        return !HIDDEN_TRAIN_PACK.equalsIgnoreCase(packName)
            && !id.contains(HIDDEN_TRAIN_PACK)
            && !displayNameLower.contains(HIDDEN_TRAIN_PACK);
    }

    public static void openCarSelectScreen(Player player, ItemStack stack) {
        List<ModelSelectScreen.ModelInfo> infos = VehicleRegistry.getAll().stream()
            .filter(d -> d != null && d.isCarType())
            .sorted(Comparator.comparing(d -> safe(d.getDisplayName()), String.CASE_INSENSITIVE_ORDER))
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName(), d.getPackName(), d.getButtonTexture()))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable("screen.realtrainmodunofficial.select_car"),
            infos,
            selection -> PacketDistributor.sendToServer(new SelectModelPayload(selection.modelId(), selection.dataMapValue())),
            LegacyItemStackBridge.getSelectedModelId(stack),
            LegacyItemStackBridge.getSelectedDataMap(stack)
        ));
    }

    public static void openVehicleFormationScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new TrainFormationScreen(stack));
    }

    public static void openInstalledObjectSelectScreen(Player player, ItemStack stack, InstalledObjectCategory category) {
        List<ModelSelectScreen.ModelInfo> infos = InstalledObjectRegistry.getByCategory(category).stream()
            .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName(), d.getPackName(), d.getButtonTexture()))
            .toList();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable(getInstalledObjectTitleKey(category)),
            infos,
            selection -> PacketDistributor.sendToServer(new SelectModelPayload(selection.modelId(), selection.dataMapValue())),
            LegacyItemStackBridge.getSelectedModelId(stack),
            LegacyItemStackBridge.getSelectedDataMap(stack)
        ));
    }

    /**
     * 本家 guiIdSelectTileEntityTexture: 設置済みの標識を素手で右クリックしてテクスチャを変える。
     * <p>
     * 標識は 1 テクスチャ = 1 定義なので、モデル選択画面をそのまま使える
     * (ボタン画像に標識のテクスチャ自体が入っている)。
     */
    public static void openRailroadSignScreen(
            com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity blockEntity) {
        List<ModelSelectScreen.ModelInfo> infos =
            InstalledObjectRegistry.getByCategory(InstalledObjectCategory.RAILROAD_SIGN).stream()
                .map(d -> new ModelSelectScreen.ModelInfo(d.getId(), d.getDisplayName(), d.getPackName(), d.getButtonTexture()))
                .toList();
        net.minecraft.core.BlockPos pos = blockEntity.getBlockPos();
        Minecraft.getInstance().setScreen(new ModelSelectScreen(
            Component.translatable(getInstalledObjectTitleKey(InstalledObjectCategory.RAILROAD_SIGN)),
            infos,
            selection -> PacketDistributor.sendToServer(
                new com.portofino.realtrainmodunofficial.network.SetObjectModelPayload(pos, selection.modelId())),
            blockEntity.getDefinitionId(),
            ""
        ));
    }

    private static String getInstalledObjectTitleKey(InstalledObjectCategory category) {
        return switch (category) {
            case LIGHT -> "screen.realtrainmodunofficial.select_light";
            case SIGNBOARD -> "screen.realtrainmodunofficial.select_signboard";
            case INSULATOR -> "screen.realtrainmodunofficial.select_insulator";
            case PIPE -> "screen.realtrainmodunofficial.select_pipe";
            case OVERHEAD_LINE_POLE -> "screen.realtrainmodunofficial.select_overhead_line_pole";
            case WIRE -> "screen.realtrainmodunofficial.select_wire";
            case SIGNAL -> "screen.realtrainmodunofficial.select_signal";
            case CROSSING -> "screen.realtrainmodunofficial.select_crossing";
            case TICKET_GATE -> "screen.realtrainmodunofficial.select_ticket_gate";
            case SPEAKER -> "screen.realtrainmodunofficial.select_speaker";
            case TRAIN_DETECTOR -> "screen.realtrainmodunofficial.select_train_detector";
            case CONNECTOR_INPUT, CONNECTOR_OUTPUT -> "screen.realtrainmodunofficial.select_connector";
            case FLUORESCENT -> "screen.realtrainmodunofficial.select_fluorescent";
            case RAILROAD_SIGN -> "screen.realtrainmodunofficial.select_railroad_sign";
            case BUMPING_POST -> "screen.realtrainmodunofficial.select_bumping_post";
            case POINT -> "screen.realtrainmodunofficial.select_point_machine";
            case TICKET_VENDOR -> "screen.realtrainmodunofficial.select_ticket_vendor";
        };
    }
}
