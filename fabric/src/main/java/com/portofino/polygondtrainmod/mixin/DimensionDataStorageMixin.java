package com.portofino.polygondtrainmod.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <b>{@code SavedData.Factory} の変換種別 (DataFixTypes) が null でも読めるようにする。</b>
 *
 * <p>バニラの {@code SavedData.Factory} は引数 3 つで、3 つ目に「昔のセーブを今の形へ直す
 * 変換の種別」を要求する。NeoForge は<b>引数 2 つの版</b>を足しており、そちらは種別を
 * null にする。RTMU の保存データ (駅の登録・リモコンの対応表) は<b>この mod 独自の中身</b>で
 * バニラの変換に当てはまるものが無いため、本来 null が正しい。
 *
 * <p>Fabric では 2 引数版が無いので呼び出し側は null を渡すが、バニラは読み込み時に
 * 種別をそのまま使うので<b>セーブがある状態で入り直すと NullPointerException で落ちる</b>
 * (新規ワールドでは読み込みが起きないので気付かない)。ここで null を素通しにする。
 *
 * <p>★呼び出し側 (StationRegistry / RemotePairings) を書き換えて適当な種別を渡す手もあるが、
 * それらは NeoForge 版と共有しているファイルなので<b>差分を作らない</b>方を選んでいる。
 * 差分があると次に本家側を直したとき取り込みで衝突する。
 */
@Mixin(DimensionDataStorage.class)
public class DimensionDataStorageMixin {

    //★呼んでいるのは 4 引数の update。updateToCurrentVersion ではない
    //  (名前から推測して外し、mixin が「対象 0 件」で起動時に落ちた)。
    @Redirect(
        method = "readTagFromDisk",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/datafix/DataFixTypes;update("
                + "Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/nbt/CompoundTag;II)"
                + "Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag rtmu$allowNullDataFixTypes(DataFixTypes type, DataFixer fixer,
                                                   CompoundTag tag, int version, int newVersion) {
        return type == null ? tag : type.update(fixer, tag, version, newVersion);
    }
}
