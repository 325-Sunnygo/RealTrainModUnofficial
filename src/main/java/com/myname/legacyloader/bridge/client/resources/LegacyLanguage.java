package com.myname.legacyloader.bridge.client.resources;

/**
 * 1.7.10 の {@code net.minecraft.client.resources.Language} の代役。
 * 言語コード (ja_JP など) を保持し、旧 SRG 名でアクセスできるようにする。
 */
public class LegacyLanguage {

    private final String code;

    public LegacyLanguage(String code) {
        this.code = code == null ? "en_US" : code;
    }

    public String getLanguageCode() {
        return this.code;
    }

    // SRG: getLanguageCode()
    public String func_135034_a() {
        return this.code;
    }

    @Override
    public String toString() {
        return this.code;
    }
}
