package jp.kaiz.atsassistmod.voicetext;

import net.minecraft.util.Mth;
import net.neoforged.fml.loading.FMLPaths;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * 本家 jp.kaiz.atsassistmod.voicetext.VoiceText の移植 (VoiceText Web API TTS)。
 * API キーは config/atsassistmod-voicetext.txt の1行目から読む
 * (https://cloud.voicetext.jp/webapi で取得した無料キー)。
 * IFTTT の JavaScript アクションなどから利用する想定:
 * <pre>
 *   var VoiceText = Java.type('jp.kaiz.atsassistmod.voicetext.VoiceText');
 *   var Speaker = Java.type('jp.kaiz.atsassistmod.voicetext.Speaker');
 *   VoiceText.create().setText('まもなく発車します').setSpeaker(Speaker.hikari).playSound();
 * </pre>
 */
public class VoiceText {
    private static final String BASE_URL = "https://api.voicetext.jp/v1/tts";
    private static final String KEY_FILE = "atsassistmod-voicetext.txt";
    private static String configuredKey;

    private final String key;

    private String text;
    private Speaker speaker;
    private Format format;
    private Emotion emotion;
    private int emotionLevel;
    private int pitch;
    private int speed;
    private int volume;

    public VoiceText(String key) {
        this.key = key;
    }

    /** config のキーで生成 (キー未設定でも生成できるが再生時に何もしない)。 */
    public static VoiceText create() {
        return new VoiceText(getConfiguredKey());
    }

    /** config/atsassistmod-voicetext.txt からキーを読む (無ければ雛形を作成)。 */
    public static synchronized String getConfiguredKey() {
        if (configuredKey != null) {
            return configuredKey.isEmpty() ? null : configuredKey;
        }
        try {
            Path file = FMLPaths.CONFIGDIR.get().resolve(KEY_FILE);
            if (Files.exists(file)) {
                configuredKey = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .findFirst()
                        .orElse("");
            } else {
                Files.write(file, List.of(
                        "# VoiceText Web API key (https://cloud.voicetext.jp/webapi)",
                        "# 1行目にキーだけ書いてください"));
                configuredKey = "";
            }
        } catch (IOException e) {
            configuredKey = "";
        }
        return configuredKey.isEmpty() ? null : configuredKey;
    }

    public VoiceText setText(String text) {
        this.text = text;
        return this;
    }

    public VoiceText setSpeaker(Speaker speaker) {
        this.speaker = speaker;
        return this;
    }

    public VoiceText setFormat(Format format) {
        this.format = format;
        return this;
    }

    public VoiceText setEmotion(Emotion emotion, int level) {
        this.emotion = emotion;
        this.emotionLevel = Mth.clamp(level, 1, 4);
        return this;
    }

    public VoiceText setPitch(int pitch) {
        this.pitch = Mth.clamp(pitch, 50, 200);
        return this;
    }

    public VoiceText setSpeed(int speed) {
        this.speed = Mth.clamp(speed, 50, 400);
        return this;
    }

    public VoiceText setVolume(int volume) {
        this.volume = Mth.clamp(volume, 50, 200);
        return this;
    }

    /** 非同期取得して再生 (本家仕様)。 */
    public void playSound() {
        new Thread(() -> {
            AudioInputStream ais = this.getAudioInputStream();
            if (ais != null) {
                play(ais, 1.0F);
            }
        }, "ATSA-VoiceText").start();
    }

    /**
     * 位置つき再生。本家は OpenAL 直叩きだったが、1.21 では距離減衰を
     * ゲイン計算で近似する (radius ブロックで無音)。
     */
    public void playSound(float x, float y, float z, float radius) {
        new Thread(() -> {
            AudioInputStream ais = this.getAudioInputStream();
            if (ais == null) {
                return;
            }
            float gain = 1.0F;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                double distance = mc.player.position().distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
                gain = (float) Mth.clamp(1.0 - distance / Math.max(1.0F, radius), 0.0, 1.0);
            }
            if (gain > 0.0F) {
                play(ais, gain);
            }
        }, "ATSA-VoiceText").start();
    }

    /** 本家 KaizUtils.playSound(AudioInputStream) 相当。 */
    private static void play(AudioInputStream ais, float gain) {
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            if (gain < 1.0F && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                control.setValue(Math.max(control.getMinimum(), (float) (20.0 * Math.log10(gain))));
            }
            clip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public byte[] getBytes() {
        if (this.text == null || this.speaker == null) {
            return null;
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(("text=" + this.text).getBytes(StandardCharsets.UTF_8));
            baos.write('&');
            baos.write(("speaker=" + this.speaker).getBytes());
            if (this.format != null) {
                baos.write('&');
                baos.write(("format=" + this.format).getBytes());
            }
            if (this.emotion != null) {
                baos.write('&');
                baos.write(("emotion=" + this.emotion).getBytes());
                baos.write(("&emotion_level=" + this.emotionLevel).getBytes());
            }
            if (this.pitch != 0) {
                baos.write('&');
                baos.write(("pitch=" + this.pitch).getBytes());
            }
            if (this.speed != 0) {
                baos.write('&');
                baos.write(("speed=" + this.speed).getBytes());
            }
            if (this.volume != 0) {
                baos.write('&');
                baos.write(("volume=" + this.volume).getBytes());
            }
            return baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public AudioInputStream getAudioInputStream() {
        if (this.key == null || this.text == null || this.speaker == null) {
            return null;
        }
        return VoiceText.getAudioInputStream(this.key, this.getBytes());
    }

    public static AudioInputStream getAudioInputStream(String key, byte[] bytes) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(BASE_URL).toURL().openConnection();
            conn.setRequestProperty("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString((key + ":").getBytes()));
            conn.setRequestProperty("User-Agent", "ATSAssist_1.0.0:FromMinecraft");
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.getOutputStream().write(bytes);

            if (conn.getResponseCode() != 200) {
                jp.kaiz.atsassistmod.ATSAssistCore.LOGGER.warn(
                        "VoiceText API error: {}", conn.getResponseMessage());
                return null;
            }

            return AudioSystem.getAudioInputStream(new BufferedInputStream(conn.getInputStream()));
        } catch (IOException | UnsupportedAudioFileException e) {
            e.printStackTrace();
            return null;
        }
    }
}
