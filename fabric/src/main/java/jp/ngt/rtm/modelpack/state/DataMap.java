package jp.ngt.rtm.modelpack.state;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家 jp.ngt.rtm.modelpack.state.DataMap のスクリプト互換移植。
 * set 系の第3引数は本家では同期フラグ (0:なし, 1:server→client)。
 */
public class DataMap {
    /** server→client 同期する。 */
    public static final byte SYNC_FLAG = 1;
    /** ワールドに保存する。 */
    public static final byte SAVE_FLAG = 2;

    private final Map<String, Object> map = new ConcurrentHashMap<>();
    /** SYNC_FLAG 付きで書かれた同期待ちエントリ (サーバー側でのみ溜まる)。 */
    private final Map<String, Object> pendingSync = new ConcurrentHashMap<>();
    /** このマップの持ち主 (エンティティ / ブロックエンティティ)。本家 setEntity。 */
    private Object entity;

    /**
     * 同期対象か。フラグはビット和なので == SYNC_FLAG で見てはいけない。
     * SYNC|SAVE (=3) で書くスクリプトが同期されなくなる。
     */
    private boolean shouldSync(int flag) {
        return (flag & SYNC_FLAG) != 0;
    }

    public int getInt(String key) {
        Object v = this.map.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    public void setInt(String key, int value, int flag) {
        this.map.put(key, value);
        if (this.shouldSync(flag)) {
            this.pendingSync.put(key, value);
        }
    }

    public boolean getBoolean(String key) {
        Object v = this.map.get(key);
        return v instanceof Boolean b && b;
    }

    public void setBoolean(String key, boolean value, int flag) {
        this.map.put(key, value);
        if (this.shouldSync(flag)) {
            this.pendingSync.put(key, value);
        }
    }

    public double getDouble(String key) {
        Object v = this.map.get(key);
        return v instanceof Number n ? n.doubleValue() : 0.0D;
    }

    public void setDouble(String key, double value, int flag) {
        this.map.put(key, value);
        if (this.shouldSync(flag)) {
            this.pendingSync.put(key, value);
        }
    }

    public String getString(String key) {
        Object v = this.map.get(key);
        return v instanceof String s ? s : "";
    }

    public void setString(String key, String value, int flag) {
        this.map.put(key, value == null ? "" : value);
        if (this.shouldSync(flag)) {
            this.pendingSync.put(key, value == null ? "" : value);
        }
    }

    /** 本家 getVec: 3 成分ベクトル。未設定は ZERO。 */
    public jp.ngt.ngtlib.math.Vec3 getVec(String key) {
        Object v = this.map.get(key);
        return v instanceof jp.ngt.ngtlib.math.Vec3 vec ? vec : jp.ngt.ngtlib.math.Vec3.ZERO;
    }

    public void setVec(String key, jp.ngt.ngtlib.math.Vec3 value, int flag) {
        this.map.put(key, value == null ? jp.ngt.ngtlib.math.Vec3.ZERO : value);
        if (this.shouldSync(flag)) {
            this.pendingSync.put(key, this.map.get(key));
        }
    }

    /**
     * 本家 getHex: 16 進で持つ整数 (主に色)。
     * 内部表現は int だが、getArg では "Hex" 型として 0x 付きで出す必要があるため
     * 専用のキー集合で型を覚えておく。
     */
    public int getHex(String key) {
        Object v = this.map.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    public void setHex(String key, int value, int flag) {
        this.map.put(key, value);
        this.hexKeys.add(key);
        if (this.shouldSync(flag)) {
            this.pendingSync.put(key, value);
        }
    }

    /** setHex で書かれたキー (getArg の型表記を "Hex" にするため)。 */
    private final java.util.Set<String> hexKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 本家 setEntity: このマップの持ち主を覚える。 */
    public void setEntity(Object entity) {
        this.entity = entity;
    }

    public Object getEntity() {
        return this.entity;
    }

    /** 本家 setFormatter: 値の検証器。RTMU は検証しないので受け取るだけ。 */
    public void setFormatter(Object formatter) {
    }

    /**
     * 本家 set(key, "(Type)value", flag): 型付き文字列 1 本で書き込む。
     * 型が省略された場合は既存の値の型に合わせる。
     */
    public boolean set(String key, String value, int flag) {
        if (key == null || value == null) {
            return false;
        }
        java.util.regex.Matcher m = VAL_TYPE.matcher(value);
        String type;
        String body;
        if (m.find()) {
            type = m.group().replace("(", "").replace(")", "");
            body = m.replaceAll("");
        } else {
            type = this.typeKeyOf(key);
            body = value;
        }
        return this.putTyped(key, type, body, flag);
    }

    private static final java.util.regex.Pattern VAL_TYPE = java.util.regex.Pattern.compile("\\([a-zA-Z]+\\)");

    private boolean putTyped(String key, String type, String body, int flag) {
        try {
            switch (type) {
                case "Int" -> this.setInt(key, body.isEmpty() ? 0 : Integer.parseInt(body.trim()), flag);
                case "Double" -> this.setDouble(key, body.isEmpty() ? 0.0D : Double.parseDouble(body.trim()), flag);
                case "Boolean" -> this.setBoolean(key, !body.isEmpty() && Boolean.parseBoolean(body.trim()), flag);
                case "String" -> this.setString(key, body, flag);
                case "Hex" -> this.setHex(key, body.isEmpty() ? 0 : Integer.decode(body.trim()), flag);
                case "Vec" -> {
                    String[] sa = body.trim().split(" +");
                    if (sa.length < 3) {
                        return false;
                    }
                    this.setVec(key, new jp.ngt.ngtlib.math.Vec3(
                            Double.parseDouble(sa[0]), Double.parseDouble(sa[1]), Double.parseDouble(sa[2])), flag);
                }
                default -> {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            jp.ngt.ngtlib.io.NGTLog.debug("[DataMap] Invalid Data (Key:%s, Value:%s)", key, body);
            return false;
        }
    }

    /** 値から本家 DataType の key を求める。 */
    private String typeKeyOf(String key) {
        if (this.hexKeys.contains(key)) {
            return "Hex";
        }
        Object v = this.map.get(key);
        if (v instanceof Integer) {
            return "Int";
        }
        if (v instanceof Double || v instanceof Float) {
            return "Double";
        }
        if (v instanceof Boolean) {
            return "Boolean";
        }
        if (v instanceof jp.ngt.ngtlib.math.Vec3) {
            return "Vec";
        }
        return "String";
    }

    /** 値を本家 DataEntry.toString と同じ表現へ。 */
    private static String bodyOf(Object v) {
        if (v instanceof jp.ngt.ngtlib.math.Vec3 vec) {
            return vec.getX() + " " + vec.getY() + " " + vec.getZ();
        }
        return String.valueOf(v);
    }

    /**
     * 本家 getArg: 全エントリを key=(Type)value,key2=(Type)value2 形式で出す。
     * モデル選択 GUI や設置物アイテムの NBT 引数に使われる。
     */
    public String getArg() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : this.map.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey()).append("=(").append(this.typeKeyOf(e.getKey())).append(')')
                    .append(bodyOf(e.getValue()));
        }
        return sb.toString();
    }

    /** 本家 setArg: getArg 形式の文字列を取り込む。overwrite=false なら既存キーは触らない。 */
    public void setArg(String arg, boolean overwrite) {
        if (arg == null || arg.isEmpty()) {
            return;
        }
        for (String[] sa : convertArg(arg)) {
            if (sa == null || sa.length < 3) {
                continue;
            }
            if (!overwrite && this.map.containsKey(sa[0])) {
                continue;
            }
            this.putTyped(sa[0], sa[1], sa[2], SYNC_FLAG | SAVE_FLAG);
        }
    }

    /** 本家 convertArg: key=(Type)value,... を {key, Type, value} の配列へ分解する。 */
    public static String[][] convertArg(String arg) {
        if (arg == null || arg.isEmpty()) {
            return new String[0][0];
        }
        String[] sa = arg.split(",");
        String[][] out = new String[sa.length][];
        for (int i = 0; i < sa.length; i++) {
            String s = sa[i];
            int eq = s.indexOf('=');
            int br = s.indexOf(')');
            if (eq < 0 || br < 0 || br < eq + 2) {
                jp.ngt.ngtlib.io.NGTLog.debug("Invalid data : %s", s);
                return new String[0][0];
            }
            out[i] = new String[]{s.substring(0, eq), s.substring(eq + 2, br), s.substring(br + 1)};
        }
        return out;
    }

    public boolean contains(String key) {
        return this.map.containsKey(key);
    }

    /** 全エントリのコピー (DataMapEditor 等の閲覧用)。 */
    public Map<String, Object> getEntries() {
        return new HashMap<>(this.map);
    }

    /** 同期待ちエントリを取り出してクリアする (サーバーの配信処理用)。空なら空マップ。 */
    public Map<String, Object> drainPendingSync() {
        if (this.pendingSync.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new HashMap<>(this.pendingSync);
        this.pendingSync.clear();
        return out;
    }

    /** 同期パケットの適用 (クライアント側)。型プレフィクス付き文字列から復元する。 */
    public void applySyncedValue(String key, String encoded) {
        if (encoded == null || encoded.length() < 2) {
            return;
        }
        char type = encoded.charAt(0);
        String body = encoded.substring(2);
        try {
            switch (type) {
                case 'I' -> this.map.put(key, Integer.parseInt(body));
                case 'D' -> this.map.put(key, Double.parseDouble(body));
                case 'B' -> this.map.put(key, Boolean.parseBoolean(body));
                default -> this.map.put(key, body);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    /** 同期パケット用に値を型プレフィクス付き文字列へ ("I:5" / "D:1.5" / "B:true" / "S:xxx")。 */
    public static String encodeSyncedValue(Object value) {
        if (value instanceof Integer i) {
            return "I:" + i;
        }
        if (value instanceof Double d) {
            return "D:" + d;
        }
        if (value instanceof Boolean b) {
            return "B:" + b;
        }
        return "S:" + value;
    }

    /**
     * 本家 receivePacket: 同期パケット "DM,対象,ID,キー,型,値,フラグ" を適用する。
     * RTMU は DataMapSyncPayload を使うが、スクリプトがこの形式を組むことがあるので受ける。
     */
    public static void receivePacket(String msg, Object packet, Object world, boolean onClient) {
        if (msg == null) {
            return;
        }
        String[] sa = msg.split(",");
        if (sa.length < 7 || !"DM".equals(sa[0])) {
            return;
        }
        net.minecraft.world.level.Level level = world instanceof net.minecraft.world.level.Level l ? l
                : world instanceof jp.ngt.mccompat.WorldCompat c ? c.getLevel() : null;
        if (level == null) {
            return;
        }
        DataMap target = null;
        if ("E".equals(sa[1])) {
            try {
                net.minecraft.world.entity.Entity e = level.getEntity(Integer.parseInt(sa[2]));
                if (e instanceof jp.ngt.rtm.entity.vehicle.EntityVehicleBase v) {
                    target = v.getResourceState().getDataMap();
                }
            } catch (NumberFormatException ignored) {
                return;
            }
        } else if ("T".equals(sa[1])) {
            String[] pos = sa[2].split(" ");
            if (pos.length < 3) {
                return;
            }
            try {
                Object be = level.getBlockEntity(new net.minecraft.core.BlockPos(
                        Integer.parseInt(pos[0]), Integer.parseInt(pos[1]), Integer.parseInt(pos[2])));
                if (be != null) {
                    Object state = be.getClass().getMethod("getResourceState").invoke(be);
                    if (state instanceof ResourceState rs) {
                        target = rs.getDataMap();
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return;
            }
        }
        if (target == null) {
            return;
        }
        int flag;
        try {
            flag = Integer.parseInt(sa[6]);
        } catch (NumberFormatException e) {
            flag = SAVE_FLAG;
        }
        // クライアントで受けた値をまた送り返さないよう、同期ビットは落とす
        if (onClient) {
            flag &= ~SYNC_FLAG;
        }
        target.putTyped(sa[3], sa[4], sa[5], flag);
    }

}
