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
 * ZIP のエントリ名は UTF-8 とは限らない。
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
     * ★ reader は「zip を全部走査してから登録する」構造でなければならない
     * (途中で登録してしまうと、やり直しで二重登録になる)。
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
     * ZipCoder は java.nio.charset.CharacterCodingException を
     * IllegalArgumentException に包み直して投げてくるので、原因まで辿る。
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
