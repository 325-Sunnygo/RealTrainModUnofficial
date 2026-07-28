package com.portofino.polygondtrainmod.mixin;

import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SavedData.Factory の変換種別 (DataFixTypes) が null でも読めるようにする。
 * バニラの SavedData.Factory は引数 3 つで、3 つ目に「昔のセーブを今の形へ直す
 * 変換の種別」を要求する。
 * ★呼び出し側 (StationRegistry / RemotePairings) を書き換えて適当な種別を渡す手もあるが、
 * それらは NeoForge 版と共有しているファイルなので差分を作らない方を選んでいる。
 */
@Mixin(DimensionDataStorage.class)
public class DimensionDataStorageMixin {

    // ★呼んでいるのは 4 引数の update。updateToCurrentVersion ではない
    // (名前から推測して外し、mixin が「対象 0 件」で起動時に落ちた)。
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
