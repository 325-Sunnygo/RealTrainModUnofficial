package jp.kaiz.atsassistmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** クライアント処理の入口 (共通コードから isClientSide ガード付きで呼ぶ)。 */
public final class AtsaClientHelper {

    private AtsaClientHelper() {
    }

    public static void openGroundUnitScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new GroundUnitScreen(pos));
    }

    public static void openTrainProtectionSelector() {
        Minecraft.getInstance().setScreen(new TrainProtectionSelectorScreen());
    }

    public static void openDataMapEditor() {
        Minecraft.getInstance().setScreen(new DataMapEditorScreen());
    }

    public static void openIFTTTScreen(BlockPos pos) {
        Minecraft.getInstance().setScreen(new IFTTTScreen(pos));
    }
}
