package com.portofino.realtrainmodunofficial.signboard;

/**
 * 本家 jp.ngt.rtm.block.tt.SignboardText.AnimeType の移植。
 * <p>
 * NBT には ordinal をそのまま保存するため、並び順は本家から変更しないこと。
 */
public enum SignboardAnimeType {
    /**
     * アニメなし。テキストをそのまま表示する。
     */
    NONE,
    /**
     * 横スクロール。animeSpeed 秒で1周する。
     */
    SCROLL,
    /**
     * '|' で区切った複数テキストを animeSpeed 秒ごとに切り替える。
     */
    SWITCH,
    /**
     * animeSpeed 秒ごとに点滅する。
     */
    FLASH,
    /**
     * 本家では未実装 (パタパタ式)。NONE と同じ扱い。
     */
    FLAP;

    public static SignboardAnimeType byOrdinal(int ordinal) {
        SignboardAnimeType[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SWITCH;
    }
}
