package com.portofino.realtrainmodunofficial.client.signboard.tt;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本家 jp.ngt.rtm.block.tt.TimeTableManager の移植。
 * 看板の時刻表表示は描画時にしか使わないのでクライアント専用。
 */
public final class TimeTableManager {
    public static final String SAMPLE = "tt_sample.csv";
    public static final TimeTableManager INSTANCE = new TimeTableManager();

    private final Map<String, TimeTable> entries = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    private TimeTableManager() {
    }

    /** config/realtrainmodunofficial/timetable/ (ユーザーが tt_*.csv を置く場所)。 */
    public static Path userDir() {
        return FMLPaths.CONFIGDIR.get().resolve(RealTrainModUnofficial.MODID).resolve("timetable");
    }

    /**
     * 読み込み済みの時刻表を捨てる。次に使われたときに読み直す。
     * ワールドを抜けたときに呼ばれるので、ユーザーが tt_*.csv を足しても再入場で反映される。
     */
    public synchronized void invalidate() {
        entries.clear();
        loaded = false;
    }

    public synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        loadFromResources();
        loadFromPacks();
        loadFromUserDir();
        if (entries.isEmpty()) {
        } else {
        }
    }

    /** mod jar / リソースパックの assets/<ns>/timetable/tt_*.csv。 */
    private void loadFromResources() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) {
            return;
        }
        try {
            Map<ResourceLocation, Resource> found = mc.getResourceManager()
                    .listResources("timetable", rl -> rl.getPath().toLowerCase(Locale.ROOT).endsWith(".csv"));
            for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
                String name = leaf(e.getKey().getPath());
                try (InputStream in = e.getValue().open()) {
                    put(name, in);
                } catch (Exception ex) {
                    RealTrainModUnofficial.LOGGER.warn("[TTM] failed to read resource {}", e.getKey(), ex);
                }
            }
        } catch (Exception e) {
        }
    }

    /** モデルパック zip の中の tt_*.csv。NGTFileLoader が全パックを索引済み。 */
    private void loadFromPacks() {
        byte[] sample = jp.ngt.ngtlib.io.NGTFileLoader.findAsset("timetable/" + SAMPLE);
        if (sample != null && !entries.containsKey(SAMPLE)) {
            put(SAMPLE, sample);
        }
    }

    /** config/realtrainmodunofficial/timetable/*.csv (ユーザー配置、最優先)。 */
    private void loadFromUserDir() {
        Path dir = userDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String name = p.getFileName().toString();
                if (!Files.isRegularFile(p) || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    continue;
                }
                try {
                    put(name, Files.readAllBytes(p));
                } catch (IOException e) {
                    RealTrainModUnofficial.LOGGER.warn("[TTM] failed to read {}", p, e);
                }
            }
        } catch (IOException e) {
        }
    }

    private void put(String name, byte[] bytes) {
        try {
            entries.put(name, new TimeTable(name, readCsv(bytes)));
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[TTM] failed to parse {}", name, e);
        }
    }

    private void put(String name, InputStream in) throws IOException {
        put(name, in.readAllBytes());
    }

    /**
     * 本家 NGTText.readCSV 相当。
     * 本家は String.split(",") をそのまま使っており、末尾の空フィールドが落ちる
     * (= "," だけの行は長さ0の配列になり読み飛ばされる) 挙動に依存している。ここも同じにする。
     */
    private static List<String[]> readCsv(byte[] bytes) throws IOException {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (first) {
                    // UTF-8 BOM
                    if (!line.isEmpty() && line.charAt(0) == '﻿') {
                        line = line.substring(1);
                    }
                    first = false;
                }
                out.add(line.split(","));
            }
        }
        return out;
    }

    private static String leaf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    /** 本家: 見つからなければ tt_sample.csv にフォールバックする。無ければ null。 */
    public TimeTable getTimeTable(String name) {
        load();
        TimeTable tt = name == null ? null : entries.get(name);
        return tt != null ? tt : entries.get(SAMPLE);
    }

    public String[] getNames() {
        load();
        return entries.keySet().toArray(new String[0]);
    }
}
