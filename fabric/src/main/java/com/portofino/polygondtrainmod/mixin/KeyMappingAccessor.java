package com.portofino.polygondtrainmod.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** KeyMappingMixin が他のインスタンスの押下回数を触るための入口。 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("clickCount")
    int rtmu$getClickCount();

    @Accessor("clickCount")
    void rtmu$setClickCount(int count);

    /**
     * 今どのキーに割り当てられているか。
     * getDefaultKey は公開されているが既定値なので使えない。
     */
    @Accessor("key")
    com.mojang.blaze3d.platform.InputConstants.Key rtmu$getKey();
}
