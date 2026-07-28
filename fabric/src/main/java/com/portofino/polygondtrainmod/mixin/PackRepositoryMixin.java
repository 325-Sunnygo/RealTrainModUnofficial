package com.portofino.polygondtrainmod.mixin;

import com.portofino.realtrainmodunofficial.fabric.RtmuPackBridge;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RTMU が実行時に生成するリソースパックをバニラの一覧へ差し込む。
 *
 * <p>NeoForge は {@code AddPackFindersEvent} でパックファインダを足せる。Fabric API の
 * {@code registerBuiltinResourcePack} は jar 内のフォルダしか受け付けないので、
 * <b>実行時生成のパック</b> (生成サウンドパック・外部建材の blockstate/model) を扱えない。
 *
 * <p>ここで {@code PackRepository.reload} の頭に割り込んで、集めた提供元から作らせる。
 * これが無いとモデルもテクスチャも 1 つも出ない。
 */
@Mixin(PackRepository.class)
public class PackRepositoryMixin {

    @Inject(method = "reload", at = @At("HEAD"))
    private void rtmu$addPacks(CallbackInfo ci) {
        //ここでは足すだけ。実際の反映は下の discoverPacks 側で行う。
    }

    @Inject(method = "discoverAvailable", at = @At("RETURN"), cancellable = true)
    private void rtmu$discover(
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<
                java.util.Map<String, Pack>> cir) {
        if (!RtmuPackBridge.hasSources()) {
            return;
        }
        java.util.Map<String, Pack> map = new java.util.LinkedHashMap<>(cir.getReturnValue());
        RtmuPackBridge.collectInto(pack -> map.put(pack.getId(), pack));
        cir.setReturnValue(map);
    }
}
