package com.portofino.realtrainmodunofficial.client.signboard.tt;

import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;

/**
 * 本家 jp.ngt.rtm.block.tt.StationTimeTable の移植。
 * 「どの時刻表の・どの駅の・どの番線か」という看板ごとの視点。
 * <p>
 * 看板の設定文字列は本家と同じ {@code tt=<file>,station=<駅名>,track=<番線>} 形式。
 */
public final class StationTimeTable {
    private static final int SECONDS_PER_DAY = 86400;

    @Nullable
    public final TimeTable timeTable;
    public final String station;
    public final byte track;
    /**
     * 駅名に対応する行。見つからなければ -1。
     */
    private final int rowIndex;

    public StationTimeTable(String ttName, String station, int track) {
        this.timeTable = TimeTableManager.INSTANCE.getTimeTable(ttName);
        this.station = station == null ? "" : station;
        this.track = (byte) track;
        Integer row = this.timeTable == null ? null : this.timeTable.stationAxis.get(this.station);
        //本家は get() の結果をそのまま int にアンボックスしていて、駅名が違うと NPE で落ちた。
        this.rowIndex = row == null ? -1 : row;
    }

    /**
     * 本家形式の設定文字列を解析する。壊れていても既定値で動くようにする。
     */
    public static StationTimeTable parse(String setting) {
        String ttName = TimeTableManager.SAMPLE;
        String station = "西京";
        int track = -1;
        if (setting != null) {
            for (String s : setting.split(",")) {
                String t = s.trim();
                int eq = t.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = t.substring(0, eq).trim();
                String value = t.substring(eq + 1).trim();
                switch (key) {
                    case "tt" -> ttName = value;
                    case "station" -> station = value;
                    case "track" -> {
                        try {
                            track = Integer.parseInt(value);
                        } catch (NumberFormatException ignored) {
                            //既定 (-1 = 番線を問わない) のまま
                        }
                    }
                    default -> {
                        //未知のキーは無視
                    }
                }
            }
        }
        return new StationTimeTable(ttName, station, track);
    }

    public String toSetting() {
        String ttName = timeTable != null ? timeTable.fileName : TimeTableManager.SAMPLE;
        return String.format("tt=%s,station=%s,track=%d", ttName, station, track);
    }

    public boolean isValid() {
        return timeTable != null && rowIndex >= 0 && rowIndex < timeTable.ttData.length;
    }

    /**
     * この駅の列車本数。
     */
    public int getSize() {
        return isValid() ? timeTable.ttData[rowIndex].length : 0;
    }

    /**
     * 本家 getMatchTrainIndex: 現在時刻以降に発車する最初の列車の index。
     * 該当なしなら getSize() (= 空白テキストの index)。
     * <p>
     * 本家は通過(ﾚ)の列で ttData が null になり NPE の可能性があったので、null は飛ばす。
     */
    public int getMatchTrainIndex(int trackFilter) {
        if (!isValid()) {
            return 0;
        }
        int time = currentTimeOfDaySeconds();
        TimeTable.TTEntry[] row = timeTable.ttData[rowIndex];
        for (int i = 0; i < row.length; i++) {
            TimeTable.TTEntry entry = row[i];
            if (entry == null) {
                //通過 (ﾚ) / 設定なし (･･)
                continue;
            }
            if (time <= entry.departureTime && (trackFilter < 0 || trackFilter == entry.trackNum)) {
                return i;
            }
        }
        return getSize();
    }

    /**
     * 本家: useRealTime なら現実時間、そうでなければマイクラ時間 (6000tick ずらして 0時基準)。
     */
    private int currentTimeOfDaySeconds() {
        if (timeTable != null && timeTable.useRealTime) {
            return (int) (System.currentTimeMillis() / 1000L % SECONDS_PER_DAY);
        }
        Minecraft mc = Minecraft.getInstance();
        long mcTime = mc.level == null ? 0L : (mc.level.getDayTime() + 6000L) % 24000L;
        return (int) (mcTime * SECONDS_PER_DAY / 24000L);
    }

    /**
     * この駅・この列車の指定列の値。col=-1 は列車名。
     */
    public String getData(int trainIndex, int col) {
        if (!isValid()) {
            return "";
        }
        TimeTable.TTEntry[] row = timeTable.ttData[rowIndex];
        if (trainIndex < 0 || trainIndex >= row.length) {
            return "";
        }
        if (col == -1) {
            String[] names = timeTable.trainName;
            return trainIndex < names.length ? names[trainIndex] : "";
        }
        TimeTable.TTEntry entry = row[trainIndex];
        return entry == null ? "" : entry.get(col);
    }
}
