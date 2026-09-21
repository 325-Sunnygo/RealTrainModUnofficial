package jp.ngt.ngtlib.io;

/**
 * 本家 {@code jp.ngt.ngtlib.io.FileMatcher}。
 * スクリプトが {@code Java.extend(FileMatcher)} で実装し、
 * {@code NGTFileLoader.findFile(matcher)} に渡す
 * (NGTO Builder2 の external manifest 探索が使う)。
 */
public interface FileMatcher {
    boolean match(java.io.File file);
}
