package com.portofino.realtrainmodunofficial.installedobject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * スピーカーの「音源ID(1〜64) → ogg 音名」マッピングを保持する。
 * 本家 RTM の rtm/speaker_sounds.json と同じ「String 配列」形式で
 * config/realtrainmodunofficial/speaker_sounds.json に永続化する。
 *
 * <p>マッピングは全スピーカー共通（ワールド非依存）。サーバーが正本を持ち、
 * 変更時に保存 + 全クライアントへ同期する。クライアントは同期された値を保持して
 * 描画/プレビューに使う。</p>
 */
public final class SpeakerSoundConfig {
    public static final int MAX_SOUND_ID = 64;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "speaker_sounds.json";

    // sounds[id-1] = 音名（"rtm:..." 等の namespace 付き、または ogg パス）。null/"null"/空 は未設定。
    private static final String[] SOUNDS = new String[MAX_SOUND_ID];

    private static boolean loaded = false;

    private SpeakerSoundConfig() {
    }

    private static Path configFile() {
        return FMLPaths.GAMEDIR.get()
            .resolve("config")
            .resolve("realtrainmodunofficial")
            .resolve(FILE_NAME);
    }

    /** サーバー起動時などに 1 度呼ぶ。config から読み込む。 */
    public static synchronized void load() {
        loaded = true;
        Path file = configFile();
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            String[] arr = GSON.fromJson(json, String[].class);
            if (arr != null) {
                for (int i = 0; i < MAX_SOUND_ID && i < arr.length; i++) {
                    SOUNDS[i] = arr[i];
                }
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to load speaker_sounds.json", e);
        }
    }

    private static synchronized void save() {
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(SOUNDS), StandardCharsets.UTF_8);
        } catch (IOException e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to save speaker_sounds.json", e);
        }
    }

    /** 音源ID(1〜64)に対応する音名を返す。未設定なら null。 */
    public static synchronized String getSound(int id) {
        if (!loaded) {
            load();
        }
        if (id < 1 || id > MAX_SOUND_ID) {
            return null;
        }
        String s = SOUNDS[id - 1];
        if (s == null || s.isBlank() || s.equals("null")) {
            return null;
        }
        return s;
    }

    /** 音源ID(1〜64)に音名を設定し、サーバー側では config に保存する。 */
    public static synchronized void setSound(int id, String sound, boolean saveToDisk) {
        if (id < 1 || id > MAX_SOUND_ID) {
            return;
        }
        SOUNDS[id - 1] = (sound == null || sound.isBlank()) ? null : sound;
        if (saveToDisk) {
            save();
        }
    }

    /**
     * 全マッピングのスナップショット（同期パケット用）。長さは MAX_SOUND_ID。
     * 未設定スロットは null ではなく空文字を入れる
     * (STRING_UTF8 codec が null をエンコードできず接続が切れるのを防ぐ)。
     */
    public static synchronized String[] snapshot() {
        if (!loaded) {
            load();
        }
        String[] copy = new String[MAX_SOUND_ID];
        for (int i = 0; i < MAX_SOUND_ID; i++) {
            copy[i] = SOUNDS[i] == null ? "" : SOUNDS[i];
        }
        return copy;
    }

    /** 同期パケット受信時にクライアント側で全マッピングを上書きする（保存はしない）。 */
    public static synchronized void replaceAll(String[] incoming) {
        loaded = true;
        for (int i = 0; i < MAX_SOUND_ID; i++) {
            SOUNDS[i] = (incoming != null && i < incoming.length) ? incoming[i] : null;
        }
    }
}
