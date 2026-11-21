package jp.ngt.ngtlib.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 本家 KaizPatchX jp.ngt.ngtlib.io.NGTJson の移植。
 * json 文字列を Gson のリフレクションで cfg クラスへ直接マッピングする。
 * (旧 RTMU 独自の手書き JSON パースを置き換える)
 */
public final class NGTJson {
    private NGTJson() {
    }

    /**
     * jsonからオブジェクトを生成
     *
     * @throws NGTFileLoadException jsonの書式が不正な場合にスロー
     */
    public static <T> T getObjectFromJson(String json, Class<T> clazz) throws NGTFileLoadException {
        try {
            return getGson().fromJson(json, clazz);
        } catch (Exception e) {
            String message = "Can't load json : " + json + " (" + e.getMessage() + ")";
            throw new NGTFileLoadException(message, e);
        }
    }

    public static String getJsonFromObject(Object object) {
        return getGson().toJson(object);
    }

    private static Gson getGson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
