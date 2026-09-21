package com.portofino.realtrainmodunofficial.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * 本家 {@code jp.ngt.rtm.item.ItemCamera} の移植。
 *
 * <p>本家は {@code onItemUse} (ブロック右クリック) で {@code GuiCamera} を開くだけ。
 * RTMU は以前「空中右クリックで撮り鉄モード切替」という独自実装だったが、
 * 本家と同じ挙動にするためブロック右クリックでカメラ画面を開くように変更した。
 */
public class CameraItem extends Item {

    public CameraItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            com.portofino.realtrainmodunofficial.ClientHooks.openCameraScreen();
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
