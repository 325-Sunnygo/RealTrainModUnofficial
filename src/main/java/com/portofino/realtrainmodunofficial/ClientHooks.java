package com.portofino.realtrainmodunofficial;

import com.portofino.realtrainmodunofficial.installedobject.InstalledObjectCategory;
import com.portofino.realtrainmodunofficial.item.TrainItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class ClientHooks {
    private static final String CLIENT_HOOKS_CLASS = "com.portofino.realtrainmodunofficial.client.ClientHooksClient";

    private ClientHooks() {
    }

    public static void openRailSelectScreen(Player player, ItemStack stack) {
        invokeClient("openRailSelectScreen", new Class<?>[]{Player.class, ItemStack.class}, player, stack);
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack, TrainItem.Category category) {
        invokeClient("openTrainSelectScreen", new Class<?>[]{Player.class, ItemStack.class, TrainItem.Category.class}, player, stack, category);
    }

    public static void openTrainSelectScreen(Player player, ItemStack stack) {
        invokeClient("openTrainSelectScreen", new Class<?>[]{Player.class, ItemStack.class}, player, stack);
    }

    public static void openVehicleFormationScreen(ItemStack stack) {
        invokeClient("openVehicleFormationScreen", new Class<?>[]{ItemStack.class}, stack);
    }

    public static void openCarSelectScreen(Player player, ItemStack stack) {
        invokeClient("openCarSelectScreen", new Class<?>[]{Player.class, ItemStack.class}, player, stack);
    }

    public static void openInstalledObjectSelectScreen(Player player, ItemStack stack, InstalledObjectCategory category) {
        invokeClient("openInstalledObjectSelectScreen", new Class<?>[]{Player.class, ItemStack.class, InstalledObjectCategory.class}, player, stack, category);
    }

    /** SignalControllerMod (masa300) 移植: 設定 GUI */
    public static void openSignalControllerScreen(Object controller) {
        invokeClient("openSignalControllerScreen", new Class<?>[]{Object.class}, controller);
    }

    /** 本家 GuiChangeOffset: バールで設置物を右クリック → 微調整 GUI */
    public static void openChangeOffsetScreen(Object blockEntity) {
        invokeClient("openChangeOffsetScreen", new Class<?>[]{Object.class}, blockEntity);
    }

    /** レールのカント設定: レンチでマーカーをシフト右クリック */
    /** マーカーの位置調整 (レンチのモード 12): ブロック未満のずれを入れる */
    public static void openMarkerOffsetScreen(Object marker) {
        invokeClient("openMarkerOffsetScreen", new Class<?>[]{Object.class}, marker);
    }

    public static void openMarkerCantScreen(Object marker) {
        invokeClient("openMarkerCantScreen", new Class<?>[]{Object.class}, marker);
    }

    public static void openSignalChangerScreen(BlockPos pos) {
        invokeClient("openSignalChangerScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openSignalReceiverScreen(BlockPos pos) {
        invokeClient("openSignalReceiverScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openSignalValueScreen(BlockPos pos) {
        invokeClient("openSignalValueScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openTrainDetectorScreen(BlockPos pos) {
        invokeClient("openTrainDetectorScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openMarkerConfigScreen(BlockPos pos) {
        invokeClient("openMarkerConfigScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openSpeakerScreen(BlockPos pos) {
        invokeClient("openSpeakerScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    public static void openScriptBlockScreen(BlockPos pos) {
        invokeClient("openScriptBlockScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    /** 看板エディタ: 看板を素手で右クリック (本家 GuiSignboard) */
    public static void openSignboardScreen(BlockPos pos) {
        invokeClient("openSignboardScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    /** 列車検知器の設定: 検知器を素手で右クリック */
    public static void openDetectorConfigScreen(BlockPos pos) {
        invokeClient("openDetectorConfigScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    /** 券売機: 素手で右クリック (本家 GuiTicketVendor) */
    public static void openTicketVendorScreen(BlockPos pos) {
        invokeClient("openTicketVendorScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    /** 標識のテクスチャ変更: 素手で右クリック (本家 guiIdSelectTileEntityTexture) */
    public static void openRailroadSignScreen(BlockPos pos) {
        invokeClient("openRailroadSignScreen", new Class<?>[]{BlockPos.class}, pos);
    }

    /** カメラ: 右クリックでファインダーモードを開閉 (本家 GuiCamera 相当) */
    public static void toggleCamera() {
        invokeClient("toggleCamera", new Class<?>[]{});
    }

    /** レンズを持って右クリック → そのレンズを装着 (id = CameraLens.id) */
    public static void mountCameraLens(String lensId) {
        invokeClient("mountCameraLens", new Class<?>[]{String.class}, lensId);
    }

    /** テレコンを持って右クリック → 装着 (id = Teleconverter.id) */
    public static void attachTeleconverter(String tcId) {
        invokeClient("attachTeleconverter", new Class<?>[]{String.class}, tcId);
    }

    /** 駅ブロック右クリック → 現在のタグを添えて駅設定 GUI を開く (client のみ)。 */
    public static void openStationScreen(BlockPos pos, int bits) {
        invokeClient("openStationScreen", new Class<?>[]{BlockPos.class, int.class}, pos, bits);
    }

    public static void stopCrossingGateSound(Level level, BlockPos pos) {
        invokeClient("stopCrossingGateSound", new Class<?>[]{Level.class, BlockPos.class}, level, pos);
    }

    public static void tickCrossingGateSound(com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity blockEntity) {
        invokeClient("tickCrossingGateSound", new Class<?>[]{com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity.class}, blockEntity);
    }

    public static void showScriptErrorMessage(String message) {
        invokeClient("showScriptErrorMessage", new Class<?>[]{String.class}, message);
    }

    //軽量化: 解決済み Class / Method をキャッシュする。踏切・スピーカー等の走行音つき設置物は
    //tickCrossingGateSound を毎 client tick 通るため、その都度 Class.forName + getMethod (リフレクション
    //走査) を回すのは無駄。このクラス内に「同名・別引数数」のメソッドは無いので name+引数数で一意にキー化できる。
    private static volatile Class<?> hooksClass;
    private static final java.util.Map<String, java.lang.reflect.Method> METHOD_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static void invokeClient(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            java.lang.reflect.Method method = resolveClientMethod(methodName, parameterTypes);
            method.invoke(null, args);
        } catch (Exception e) {
        }
    }

    private static java.lang.reflect.Method resolveClientMethod(String methodName, Class<?>[] parameterTypes)
            throws ReflectiveOperationException {
        String key = methodName + "/" + parameterTypes.length;
        java.lang.reflect.Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Class<?> hooks = hooksClass;
        if (hooks == null) {
            hooks = Class.forName(CLIENT_HOOKS_CLASS);
            hooksClass = hooks;
        }
        java.lang.reflect.Method method = hooks.getMethod(methodName, parameterTypes);
        METHOD_CACHE.put(key, method);
        return method;
    }
}
