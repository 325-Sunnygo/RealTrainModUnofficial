package com.portofino.realtrainmodunofficial.util;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;

/**
 * モデルパック (zip) を開くときの文字コード対策。
 *
 * <p>ZIP のエントリ名は UTF-8 とは限らない。Windows のエクスプローラで作った zip は、
 * ファイル名に日本語が含まれると <b>Shift-JIS (MS932)</b> で書かれ、UTF-8 フラグ
 * (汎用目的ビット 11) も立たない。Java の {@link java.util.zip.ZipInputStream} は
 * 既定で UTF-8 として<b>厳密に</b>デコードするため、そういう zip はエントリ名の時点で
 * {@link MalformedInputException} を投げ、<b>パック丸ごと読み込めなくなる</b>。
 *
 * <pre>
 *   Failed to load rail pack 113-hikariv2.zip
 *   java.nio.charset.MalformedInputException: Input length = 1
 *       at java.util.zip.ZipInputStream.readLOC
 * </pre>
 *
 * <p>そこで UTF-8 で開いてみて、エントリ名が壊れていたら Shift-JIS で開き直す。
 * <ul>
 *   <li>UTF-8 フラグが立っているエントリは、指定した文字コードに関わらず UTF-8 で
 *       デコードされる (ZIP 仕様)。なので Shift-JIS で開き直しても正しい zip は壊れない。</li>
 *   <li>ASCII だけのエントリ名はどちらでも同じ。</li>
 * </ul>
 * 「まず UTF-8」の順にするのは、フラグの立っていない UTF-8 の zip (一部の Linux ツール製)
 * を Shift-JIS と誤読して文字化けさせないため。
 */
public final class PackZip {

    /** Windows で作られた日本語ファイル名の zip のエントリ名。 */
    public static final Charset SHIFT_JIS = Charset.forName("windows-31j");

    private PackZip() {
    }

    /** zip を開き直せる供給元 (ファイルパス / バイト列)。 */
    @FunctionalInterface
    public interface Opener {
        InputStream open() throws IOException;
    }

    /** 与えられた文字コードで zip を読む処理。 */
    @FunctionalInterface
    public interface Reader {
        void read(InputStream in, Charset charset) throws Exception;
    }

    /**
     * UTF-8 で読み、エントリ名が壊れていたら Shift-JIS で読み直す。
     * <p>
     * ★ {@code reader} は「zip を全部走査してから登録する」構造でなければならない
     * (途中で登録してしまうと、やり直しで二重登録になる)。RTMU の 3 つのパックローダは
     * どれも「ループ内では収集だけ、ループ後に登録」なので安全。
     */
    public static void readWithFallback(Opener opener, String packName, Reader reader) throws Exception {
        try (InputStream in = opener.open()) {
            reader.read(in, StandardCharsets.UTF_8);
            return;
        } catch (Exception e) {
            if (!isMalformedEntryName(e)) {
                throw e;
            }
        }
        try (InputStream in = opener.open()) {
            reader.read(in, SHIFT_JIS);
        }
    }

    /**
     * 例外が「zip のエントリ名をデコードできなかった」ものか。
     * <p>
     * ZipCoder は {@link java.nio.charset.CharacterCodingException} を
     * {@link IllegalArgumentException} に包み直して投げてくるので、原因まで辿る。
     */
    public static boolean isMalformedEntryName(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof MalformedInputException || c instanceof UnmappableCharacterException) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }
}
