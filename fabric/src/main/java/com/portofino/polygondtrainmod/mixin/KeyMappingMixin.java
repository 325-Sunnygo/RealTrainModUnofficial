package com.portofino.polygondtrainmod.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 同じキーに複数の割り当てが乗っているとき、全部に届ける。
 * 直した症状
 * Fabric 版で前後に歩けない (左右にしか動けない)。
 * ALL.put(name, this);      // 名前 → 割り当て
 * MAP.put(this.key, this);  // ★キー → 割り当て。
 */
@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    @Shadow
    @Final
    private static Map<String, KeyMapping> ALL;

    @Shadow
    @Final
    private static Map<InputConstants.Key, KeyMapping> MAP;

    /** 押した瞬間 (押下回数を増やす)。consumeClick 側が読む。 */
    @Inject(method = "click", at = @At("HEAD"))
    private static void rtmu$clickAll(InputConstants.Key key, CallbackInfo ci) {
        KeyMapping handledByVanilla = MAP.get(key);
        for (KeyMapping mapping : ALL.values()) {
            KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
            if (mapping == handledByVanilla || !key.equals(accessor.rtmu$getKey())) {
                continue;
            }
            accessor.rtmu$setClickCount(accessor.rtmu$getClickCount() + 1);
        }
    }

    /** 押しっぱなしの状態。isDown 側が読む。 */
    @Inject(method = "set", at = @At("HEAD"))
    private static void rtmu$setAll(InputConstants.Key key, boolean held, CallbackInfo ci) {
        KeyMapping handledByVanilla = MAP.get(key);
        for (KeyMapping mapping : ALL.values()) {
            if (mapping == handledByVanilla
                || !key.equals(((KeyMappingAccessor) mapping).rtmu$getKey())) {
                continue;
            }
            mapping.setDown(held);
        }
    }
}
