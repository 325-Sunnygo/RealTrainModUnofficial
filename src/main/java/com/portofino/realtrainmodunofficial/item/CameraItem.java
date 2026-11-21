package com.portofino.realtrainmodunofficial.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** 本家 jp.ngt.rtm.item.ItemCamera の移植。 */
public class CameraItem extends Item {

    public CameraItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            com.portofino.realtrainmodunofficial.ClientHooks.openCameraScreen();
        }
        return InteractionResult.PASS;
    }
}
