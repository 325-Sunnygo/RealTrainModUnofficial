package com.portofino.polygondtrainmod.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@link KeyMappingMixin} が他のインスタンスの押下回数を触るための入口。 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("clickCount")
    int rtmu$getClickCount();

    @Accessor("clickCount")
    void rtmu$setClickCount(int count);

    /**
     * 今どのキーに割り当てられているか。
     * <p>{@code getDefaultKey()} は公開されているが<b>既定値</b>なので使えない。
     * 設定画面で割り当てを変えた後も正しく配るには、今の値を見る必要がある。
     */
    @Accessor("key")
    com.mojang.blaze3d.platform.InputConstants.Key rtmu$getKey();
}
