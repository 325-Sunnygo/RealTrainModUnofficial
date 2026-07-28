package jp.ngt.rtm.entity.npc.macro;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import jp.ngt.rtm.entity.npc.macro.TrainCommand.CommandType;
import jp.ngt.rtm.entity.train.util.TrainState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家 jp.ngt.rtm.entity.npc.macro.MacroRecorder の移植 (運転マクロの録画)。
 *
 * <p>{@code /rtm macro start} で録画開始 → 自分で列車を運転 (ノッチ/ドア/警笛が
 * 相対時刻つきで記録される) → {@code /rtm macro stop} で
 * {@code config/realtrainmodunofficial/macro/日時.txt} に保存。
 * 保存したマクロは運転士 (EntityMotorman) を素手右クリック → マクロ選択で再生できる。
 *
 * <p>RTMU は列車操作がサーバー側で確定するため、本家 (クライアント単体) と違い
 * プレイヤーごとのレコーダーをサーバーで持つ。
 */
public final class MacroRecorder {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private MacroRecorder() {
    }

    private static final class Session {
        final List<TrainCommand> commands = new ArrayList<>();
        final long startTime;

        Session(long startTime) {
            this.startTime = startTime;
        }
    }

    public static Path macroFolder() {
        return FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial").resolve("macro");
    }

    public static boolean isRecording(Player player) {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }

    /** 録画開始。 */
    public static boolean start(Player player) {
        if (player == null || SESSIONS.containsKey(player.getUUID())) {
            return false;
        }
        SESSIONS.put(player.getUUID(), new Session(player.level().getGameTime()));
        player.sendSystemMessage(Component.literal("マクロ録画を開始しました (/rtm macro stop で保存)"));
        return true;
    }

    /** 録画停止 + 保存。 */
    public static boolean stop(Player player) {
        if (player == null) {
            return false;
        }
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return false;
        }
        try {
            Path folder = macroFolder();
            Files.createDirectories(folder);
            String fileName = DATE_FORMAT.format(new Date()) + ".txt";
            List<String> lines = new ArrayList<>();
            for (TrainCommand command : session.commands) {
                lines.add(command.toString());
            }
            Files.write(folder.resolve(fileName), lines, StandardCharsets.UTF_8);
            player.sendSystemMessage(Component.literal("マクロを保存しました: " + fileName
                    + " (" + lines.size() + " 操作)"));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("マクロの保存に失敗しました: " + e));
            RealTrainModUnofficial.LOGGER.warn("[MacroRecorder] save failed", e);
        }
        return true;
    }

    private static void record(Entity driver, CommandType type, Object param) {
        if (!(driver instanceof Player player)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        long time = player.level().getGameTime() - session.startTime;
        session.commands.add(new TrainCommand(time, type, param));
    }

    /** 本家 recNotch: ノッチの増分を記録 (EntityTrainBase.addNotch から)。 */
    public static void recNotch(Entity driver, int notchInc) {
        record(driver, CommandType.Notch, notchInc);
    }

    public static void recHorn(Entity driver) {
        record(driver, CommandType.Horn, "");
    }

    public static void recChime(Entity driver, String name) {
        record(driver, CommandType.Chime, name);
    }

    public static void recDoor(Entity driver, TrainState state) {
        record(driver, CommandType.Door, state);
    }
}
