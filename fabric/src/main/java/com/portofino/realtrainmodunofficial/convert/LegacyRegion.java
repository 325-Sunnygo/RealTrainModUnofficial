package com.portofino.realtrainmodunofficial.convert;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * 1.7.10 / 1.12.2 のリージョンファイル (.mca) を読む。
 *
 * <p>Anvil のリージョン形式は 1.2 から 1.21 まで変わっていない
 * (先頭 4KiB がチャンクの位置表、次の 4KiB がタイムスタンプ、以降 4KiB セクタ単位でチャンク本体)。
 * よってバニラの実装に頼らずに自前で読める。バニラの {@code RegionFile} は開いた時点で
 * ヘッダを検証・書き戻すことがあるため、<b>読み取り専用でここでは触らない</b>。
 */
public final class LegacyRegion {

    private static final int SECTOR = 4096;

    private LegacyRegion() {
    }

    /**
     * リージョンファイル内の全チャンク NBT を返す (壊れたチャンクは飛ばす)。
     */
    public static List<CompoundTag> readChunks(Path mca) {
        byte[] file;
        try {
            file = Files.readAllBytes(mca);
        } catch (IOException e) {
            RealTrainModUnofficial.LOGGER.warn("[convert] region read failed: {}", mca, e);
            return List.of();
        }
        if (file.length < SECTOR * 2) {
            return List.of();
        }

        List<CompoundTag> chunks = new ArrayList<>();
        for (int i = 0; i < 1024; i++) {
            int location = readInt(file, i * 4);
            if (location == 0) {
                continue;
            }
            int offset = (location >>> 8) & 0xFFFFFF;
            int sectors = location & 0xFF;
            if (sectors == 0 || offset < 2) {
                continue;
            }
            int start = offset * SECTOR;
            if (start + 5 > file.length) {
                continue;
            }
            int length = readInt(file, start);
            if (length <= 1 || start + 4 + length > file.length) {
                continue;
            }
            byte compression = file[start + 4];
            byte[] data = Arrays.copyOfRange(file, start + 5, start + 4 + length);

            CompoundTag tag = decode(data, compression, mca, i);
            if (tag != null) {
                chunks.add(tag);
            }
        }
        return chunks;
    }

    /**
     * チャンク番号 → NBT。書き戻すときに使う。
     */
    public static Map<Integer, CompoundTag> readChunksIndexed(Path mca) {
        Map<Integer, CompoundTag> chunks = new LinkedHashMap<>();
        byte[] file;
        try {
            file = Files.readAllBytes(mca);
        } catch (IOException e) {
            return chunks;
        }
        if (file.length < SECTOR * 2) {
            return chunks;
        }
        for (int i = 0; i < 1024; i++) {
            int location = readInt(file, i * 4);
            if (location == 0) {
                continue;
            }
            int offset = (location >>> 8) & 0xFFFFFF;
            int sectors = location & 0xFF;
            if (sectors == 0 || offset < 2) {
                continue;
            }
            int start = offset * SECTOR;
            if (start + 5 > file.length) {
                continue;
            }
            int length = readInt(file, start);
            if (length <= 1 || start + 4 + length > file.length) {
                continue;
            }
            byte compression = file[start + 4];
            byte[] data = Arrays.copyOfRange(file, start + 5, start + 4 + length);
            CompoundTag tag = decode(data, compression, mca, i);
            if (tag != null) {
                chunks.put(i, tag);
            }
        }
        return chunks;
    }

    /**
     * チャンクを書き戻す (zlib 圧縮、Anvil の 4KiB セクタ形式)。
     */
    public static void writeChunks(Path mca, Map<Integer, CompoundTag> chunks) throws IOException {
        byte[] header = new byte[SECTOR * 2];
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int sector = 2;

        for (Map.Entry<Integer, CompoundTag> e : chunks.entrySet()) {
            byte[] compressed = compress(e.getValue());
            //[長さ(4)][圧縮方式(1)][データ]
            int total = compressed.length + 5;
            int sectors = (total + SECTOR - 1) / SECTOR;
            if (sectors > 255) {
                //1MB 超のチャンクは外部ファイルになるが、旧ワールドでは実質発生しない
                throw new IOException("chunk too large: " + e.getKey());
            }
            int location = (sector << 8) | sectors;
            writeInt(header, e.getKey() * 4, location);
            //タイムスタンプはそのままで良いが、0 だと再生成されるので現在値を入れない (再現性のため 1)
            writeInt(header, SECTOR + e.getKey() * 4, 1);

            byte[] padded = new byte[sectors * SECTOR];
            writeInt(padded, 0, compressed.length + 1);
            padded[4] = 2; //zlib
            System.arraycopy(compressed, 0, padded, 5, compressed.length);
            body.write(padded);
            sector += sectors;
        }

        try (java.io.OutputStream out = Files.newOutputStream(mca)) {
            out.write(header);
            out.write(body.toByteArray());
        }
    }

    private static byte[] compress(CompoundTag tag) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        Deflater deflater = new Deflater();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(raw, deflater))) {
            NbtIo.write(tag, out);
        } finally {
            deflater.end();
        }
        return raw.toByteArray();
    }

    private static void writeInt(byte[] b, int i, int v) {
        b[i] = (byte) (v >>> 24);
        b[i + 1] = (byte) (v >>> 16);
        b[i + 2] = (byte) (v >>> 8);
        b[i + 3] = (byte) v;
    }

    private static CompoundTag decode(byte[] data, byte compression, Path mca, int index) {
        try {
            InputStream raw = new ByteArrayInputStream(data);
            InputStream in = switch (compression) {
                case 1 -> new GZIPInputStream(raw);
                case 2 -> new InflaterInputStream(raw);
                case 3 -> raw;
                default -> null;
            };
            if (in == null) {
                return null;
            }
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(in))) {
                return NbtIo.read(dis);
            }
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("[convert] broken chunk #{} in {}: {}", index, mca.getFileName(), e.toString());
            return null;
        }
    }

    private static int readInt(byte[] b, int i) {
        return ((b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16) | ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
    }
}
