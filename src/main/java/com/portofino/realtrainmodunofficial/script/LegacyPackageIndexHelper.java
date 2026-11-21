package com.portofino.realtrainmodunofficial.script;

/** スクリプトの importPackage から参照されるクラス索引ヘルパ (JS からメソッド呼び出し)。 */
public final class LegacyPackageIndexHelper {
    public String[] classesOf(String pkg) {
        String[] classes = LegacyPackageIndex.PACKAGES.get(pkg);
        return classes == null ? new String[0] : classes;
    }
}
