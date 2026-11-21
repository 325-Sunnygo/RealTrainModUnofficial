package com.portofino.realtrainmodunofficial;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * mod が自前で持つ音。
 *
 * <p>車両やパックの音は生成サウンドパック ({@code ExternalSoundPackBridge}) 経由なので
 * ここには入らない。ここは<b>jar に同梱している音</b>だけ。
 */
public final class RealTrainModUnofficialSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, RealTrainModUnofficial.MODID);

    /** 銃声。本家 {@code RTMSound.GUN} = {@code rtm:sounds/item/gun.ogg}。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> GUN = register("item.gun");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(RealTrainModUnofficial.MODID, name)));
    }

    private RealTrainModUnofficialSounds() {
    }
}
