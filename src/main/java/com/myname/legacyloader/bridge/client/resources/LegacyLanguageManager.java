package com.myname.legacyloader.bridge.client.resources;

import net.minecraft.client.Minecraft;

/**
 * 1.7.10 の {@code net.minecraft.client.resources.LanguageManager} の代役。
 * ClassTransformer が旧クラス参照をこれへ差し替える (Mappings 参照)。
 * 現行 Minecraft の選択言語を 1.7.10 形式 (ja_JP など) で返す。
 */
public class LegacyLanguageManager {

    public LegacyLanguage getCurrentLanguage() {
        return new LegacyLanguage(currentCode());
    }

    // SRG: getCurrentLanguage()
    public LegacyLanguage func_135041_c() {
        return getCurrentLanguage();
    }

    /** 現行 MC の選択言語を 1.7.10 形式へ (例 "ja_jp" → "ja_JP")。 */
    private static String currentCode() {
        try {
            Minecraft mc = Minecraft.getInstance();
            String selected = mc == null || mc.getLanguageManager() == null
                    ? "en_US" : mc.getLanguageManager().getSelected();
            if (selected == null || selected.isBlank()) {
                return "en_US";
            }
            // 1.21 は "ja_jp" 形式、1.7.10 は "ja_JP" 形式。国コード部を大文字化して合わせる。
            int us = selected.indexOf('_');
            if (us > 0) {
                return selected.substring(0, us) + "_" + selected.substring(us + 1).toUpperCase(java.util.Locale.ROOT);
            }
            return selected;
        } catch (Throwable t) {
            return "en_US";
        }
    }
}
