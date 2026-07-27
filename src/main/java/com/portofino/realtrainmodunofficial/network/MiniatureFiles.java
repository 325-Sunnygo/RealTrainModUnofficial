package com.portofino.realtrainmodunofficial.network;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/** ミニチュア (NGTO) の保存先。本家 MCTE と同じく専用フォルダに置く。 */
public final class MiniatureFiles {

    public static final String EXTENSION = ".ngto";

    private MiniatureFiles() {
    }

    /** {@code <ゲームフォルダ>/mcte/miniature}。無ければ作る。 */
    public static Path dir() {
        Path p = FMLPaths.GAMEDIR.get().resolve("mcte").resolve("miniature");
        try {
            Files.createDirectories(p);
        } catch (Exception ignored) {
            //作れなくても読み書き時に失敗するだけなので、ここでは黙って進む
        }
        return p;
    }
}
