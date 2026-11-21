package org.webctc;

import net.minecraft.server.level.ServerPlayer;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家 org.webctc.router.PlayerSessionManager の移植 + セッション管理。
 * /webctc auth → ワンタイムキー発行 → ブラウザで /auth/mc-session-login?key= を開くと
 * セッション Cookie が発行され、アカウント連携 (MCID/UUID) が有効になる。
 */
public final class PlayerSessionManager {

    public record PlayerData(String name, UUID uuid) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** ワンタイムキー → プレイヤー (本家 sessionMap、使用で消える)。 */
    private static final Map<String, PlayerData> oneTimeKeys = new ConcurrentHashMap<>();
    /** セッショントークン (Cookie) → プレイヤー。 */
    private static final Map<String, PlayerData> sessions = new ConcurrentHashMap<>();

    private PlayerSessionManager() {
    }

    public static String createSession(ServerPlayer player) {
        String key = generateKey(16);
        oneTimeKeys.put(key, new PlayerData(player.getGameProfile().getName(), player.getUUID()));
        return key;
    }

    /** ワンタイムキーを消費してセッショントークンを発行。無効なら null。 */
    public static String useKey(String key) {
        PlayerData data = oneTimeKeys.remove(key);
        if (data == null) {
            return null;
        }
        String token = generateKey(32);
        sessions.put(token, data);
        return token;
    }

    public static PlayerData getSession(String token) {
        return token == null ? null : sessions.get(token);
    }

    public static void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private static String generateKey(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
