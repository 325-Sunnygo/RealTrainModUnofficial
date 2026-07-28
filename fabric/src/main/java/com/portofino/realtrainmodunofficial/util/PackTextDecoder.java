package com.portofino.realtrainmodunofficial.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PackTextDecoder {
    private static final int STREAM_BUFFER_BYTES = 1024 * 1024;
    private static final Charset[] TEXT_CHARSETS = {
        StandardCharsets.UTF_8,
        Charset.forName("MS932"),
        Charset.forName("Shift_JIS")
    };
    private static final ThreadLocal<SoftReference<byte[]>> STREAM_BUFFER = new ThreadLocal<>();

    private PackTextDecoder() {
    }

    public static String decodeText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (hasUtf8Bom(bytes)) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        for (Charset charset : TEXT_CHARSETS) {
            try {
                return decodeStrict(bytes, charset);
            } catch (CharacterCodingException ignored) {
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String decodeJson(byte[] bytes) {
        return decodeText(bytes);
    }

    public static String readText(Path path) throws IOException {
        return decodeText(Files.readAllBytes(path));
    }

    public static String readText(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(8192);
        byte[] buffer = getStreamBuffer();
        int read;
        while ((read = inputStream.read(buffer, 0, buffer.length)) >= 0) {
            if (read == 0) {
                continue;
            }
            output.write(buffer, 0, read);
        }
        return decodeText(output.toByteArray());
    }

    private static byte[] getStreamBuffer() {
        SoftReference<byte[]> reference = STREAM_BUFFER.get();
        byte[] buffer = reference == null ? null : reference.get();
        if (buffer == null) {
            buffer = new byte[STREAM_BUFFER_BYTES];
            STREAM_BUFFER.set(new SoftReference<>(buffer));
        }
        return buffer;
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
            && (bytes[0] & 0xFF) == 0xEF
            && (bytes[1] & 0xFF) == 0xBB
            && (bytes[2] & 0xFF) == 0xBF;
    }

    private static String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
    }
}
