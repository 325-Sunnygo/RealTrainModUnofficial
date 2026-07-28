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
 * <b>同じキーに複数の割り当てが乗っているとき、全部に届ける。</b>
 *
 * <h2>直した症状</h2>
 * Fabric 版で<b>前後に歩けない</b> (左右にしか動けない)。
 *
 * <h2>なぜ起きるか</h2>
 * バニラの {@link KeyMapping} は<b>コンストラクタの中で自分を静的な表へ登録する</b>。
 *
 * <pre>
 *   ALL.put(name, this);      // 名前 → 割り当て
 *   MAP.put(this.key, this);  // ★キー → 割り当て。1 つのキーに 1 つだけ
 * </pre>
 *
 * <p>{@code MAP} は普通の Map なので<b>後から作った方が前のを追い出す</b>。
 * RTMU は運転操作に W (ブレーキ緩め) と S (力行切) を既定で使っており、
 * これがバニラの前進・後退を追い出していた。押しても
 * {@code MAP.get(W)} が RTMU 側を返すので、バニラの前進は<b>一度も押されない</b>。
 * 左右 (A/D) は取られていないので動く ⇒「一定の方向にしか動けない」。
 *
 * <h2>なぜ NeoForge 版では起きないか</h2>
 * NeoForge は {@code MAP} を<b>1 キーに複数持てる表</b>へ置き換え、さらに
 * 「運転中だけ効く」といった<b>競合文脈</b>を持たせている。素の Fabric にはどちらも無い。
 *
 * <h2>直し方</h2>
 * 既定キーをずらす手もあるが、W=ブレーキ緩め・S=力行切は<b>本家 RTM から続く操作</b>なので
 * 変えたくない。そこで NeoForge と同じく<b>そのキーに割り当てられた全部へ配る</b>。
 * {@code MAP} が持っている 1 つはバニラ本体が処理するので、ここでは<b>それ以外</b>を配る。
 * こうすると、どちらが {@code MAP} を取っていても両方に届く。
 *
 * <p>歩きながら運転席にいることは無いので、W が「前進」と「ブレーキ緩め」の両方に届いても
 * 実害は出ない (NeoForge 版も同じ挙動)。
 */
@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    @Shadow
    @Final
    private static Map<String, KeyMapping> ALL;

    @Shadow
    @Final
    private static Map<InputConstants.Key, KeyMapping> MAP;

    /** 押した瞬間 (押下回数を増やす)。{@code consumeClick()} 側が読む。 */
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

    /** 押しっぱなしの状態。{@code isDown()} 側が読む。 */
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
