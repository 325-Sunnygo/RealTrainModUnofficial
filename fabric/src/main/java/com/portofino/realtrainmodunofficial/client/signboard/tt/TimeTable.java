package com.portofino.realtrainmodunofficial.client.signboard.tt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 本家 jp.ngt.rtm.block.tt.TimeTable の移植 (tt_*.csv の解析)。
 * <p>
 * CSV は4セクションからなる:
 * <pre>
 *   &lt;Config&gt;     name= / description= / useRealTime=
 *   &lt;Color&gt;      正規表現=0xRRGGBB   (列車名に一致したら文字色を強制)
 *   &lt;TimeTable&gt;  1行目=列車番号, 2行目=列車名, 3行目以降=駅ごとの EntryID 行
 *   &lt;Entry&gt;      1行目=列名, 2行目以降= ID,着時刻,発時刻,番線,乗車位置,両数
 * </pre>
 * 本家は行の分割に Java の {@code String.split(",")} を使っており、末尾の空フィールドが
 * 落ちる性質に頼っている ("," だけの行は長さ0になり読み飛ばされる)。ここでも同じにしないと
 * 行番号がずれるので、意図的に同じ挙動にしてある。
 */
public class TimeTable {
    public final String fileName;
    public String name;
    public String description;
    /**
     * true=現実時間, false=マイクラ時間。
     */
    public boolean useRealTime;
    /**
     * 列車名の正規表現 → 文字色。
     */
    public final Map<Pattern, Integer> textColorMap = new LinkedHashMap<>();
    /**
     * [駅index][列車index]。通過(ﾚ)や設定なし(･･)は null。
     */
    public TTEntry[][] ttData = new TTEntry[0][];
    /**
     * 列車名 (列車index順)。
     */
    public String[] trainName = new String[0];
    /**
     * 駅名 → 駅index。
     */
    public final Map<String, Integer> stationAxis = new LinkedHashMap<>();
    public final List<String> colNames = new ArrayList<>();

    public TimeTable(String fileName, List<String[]> csv) {
        this.fileName = fileName;
        this.loadCSV(csv);
    }

    private void loadCSV(List<String[]> csv) {
        TTSection section = TTSection.Config;
        int sectionCount = 0;
        int stationRow = 0;
        List<List<String>> idRows = new ArrayList<>();
        Map<String, TTEntry> entryMap = new HashMap<>();

        for (String[] sa : csv) {
            if (sa == null || sa.length == 0) {
                //"," だけの行 (空行)。本家同様、行カウントも進めない。
                continue;
            }
            String first = sa[0];
            if (first.startsWith("#")) {
                //コメント行。本家は行カウントだけ進めるので合わせる。
                ++sectionCount;
                continue;
            }
            if (first.startsWith("<")) {
                TTSection next = TTSection.get(first);
                if (next != null) {
                    section = next;
                    sectionCount = 0;
                }
                ++sectionCount;
                continue;
            }

            switch (section) {
                case Config -> {
                    if (first.startsWith("name")) {
                        this.name = parseValue(first);
                    } else if (first.startsWith("description")) {
                        this.description = parseValue(first);
                    } else if (first.startsWith("useRealTime")) {
                        this.useRealTime = Boolean.parseBoolean(parseValue(first));
                    }
                }
                case Color -> {
                    String[] kv = first.split("=");
                    if (kv.length >= 2) {
                        try {
                            this.textColorMap.put(Pattern.compile(kv[0]), Integer.decode(kv[1]));
                        } catch (Exception ignored) {
                            //壊れた色指定は無視する (時刻表全体を落とさない)。
                        }
                    }
                }
                case TimeTable -> {
                    if (sectionCount == 1) {
                        //列車番号の行。本家は trainAxis に入れるだけで、表示には使わない。
                    } else if (sectionCount == 2) {
                        this.trainName = new String[Math.max(0, sa.length - 1)];
                        System.arraycopy(sa, 1, this.trainName, 0, this.trainName.length);
                    } else {
                        //駅の行。1列目が駅名、2列目以降が EntryID。
                        this.stationAxis.putIfAbsent(first, stationRow);
                        List<String> ids = new ArrayList<>(sa.length - 1);
                        for (int i = 1; i < sa.length; i++) {
                            ids.add(sa[i]);
                        }
                        idRows.add(ids);
                        ++stationRow;
                    }
                }
                case Entry -> {
                    if (sectionCount == 1) {
                        this.colNames.addAll(java.util.Arrays.asList(sa));
                    } else {
                        TTEntry entry = TTEntry.parse(sa);
                        if (entry != null) {
                            entryMap.put(first, entry);
                        }
                    }
                }
            }
            ++sectionCount;
        }

        this.ttData = new TTEntry[idRows.size()][];
        for (int i = 0; i < idRows.size(); i++) {
            List<String> ids = idRows.get(i);
            TTEntry[] row = new TTEntry[ids.size()];
            for (int j = 0; j < ids.size(); j++) {
                //通過(ﾚ)や未設定(･･)は entryMap に無いので null のまま。
                row[j] = entryMap.get(ids.get(j));
            }
            this.ttData[i] = row;
        }
    }

    /**
     * "key=value" の value 側。value に '=' が含まれる場合も落とさない。
     */
    private static String parseValue(String s) {
        int i = s.indexOf('=');
        return i < 0 || i + 1 >= s.length() ? "" : s.substring(i + 1);
    }

    /**
     * 列車名にマッチする強制色。無ければ -1。
     */
    public int getForcedColor(String trainName) {
        if (trainName == null) {
            return -1;
        }
        for (Map.Entry<Pattern, Integer> e : this.textColorMap.entrySet()) {
            if (e.getKey().matcher(trainName).matches()) {
                return e.getValue();
            }
        }
        return -1;
    }

    public static final class TTEntry {
        public final String[] data;
        public final int arrivalTime;
        public final int departureTime;
        public final byte trackNum;

        private TTEntry(String[] data, int arrivalTime, int departureTime, byte trackNum) {
            this.data = data;
            this.arrivalTime = arrivalTime;
            this.departureTime = departureTime;
            this.trackNum = trackNum;
        }

        /**
         * 本家 TTEntry(String[]) 相当。本家は壊れた行で NumberFormatException を投げて
         * 時刻表全体のロードを落としていたので、こちらは null を返して1行だけ捨てる。
         */
        static TTEntry parse(String[] entry) {
            if (entry.length < 4) {
                return null;
            }
            try {
                return new TTEntry(entry, convertTime(entry[1]), convertTime(entry[2]), Byte.parseByte(entry[3].trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /**
         * "H:MM" → 0時からの秒数。
         */
        private static int convertTime(String s) {
            String[] sa = s.split(":");
            if (sa.length < 2) {
                throw new NumberFormatException("bad time: " + s);
            }
            int hour = Integer.parseInt(sa[0].trim());
            int minute = Integer.parseInt(sa[1].trim());
            return (hour * 60 + minute) * 60;
        }

        public String get(int col) {
            return col >= 0 && col < data.length ? data[col] : "";
        }
    }

    public enum TTSection {
        Config,
        Color,
        TimeTable,
        Entry;

        static TTSection get(String s) {
            for (TTSection t : values()) {
                if (s.contains(t.toString())) {
                    return t;
                }
            }
            return null;
        }
    }
}
