package com.portofino.realtrainmodunofficial.fabric;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * AddPackFindersEvent で集めたパックを Fabric へ差し込む。
 * NeoForge は RepositorySource を受け取ってリソースパック一覧へ足す。
 */
public final class RtmuPackBridge {

    private static final List<RepositorySource> SOURCES = new ArrayList<>();

    private RtmuPackBridge() {
    }

    /** 集めた source を控えて、以後の #collectInto で流せるようにする。 */
    public static void install(AddPackFindersEvent event) {
        SOURCES.clear();
        SOURCES.addAll(event.getSources());
        if (!SOURCES.isEmpty()) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
                "[RTMU/Fabric] 追加パック提供元を {} 件受け取りました", SOURCES.size());
        }
    }

    /**
     * バニラの PackRepository が一覧を作るときに呼ばれる (mixin から)。
     * NeoForge のパックファインダと同じ位置で足す。
     */
    public static void collectInto(java.util.function.Consumer<Pack> consumer) {
        for (RepositorySource source : SOURCES) {
            try {
                source.loadPacks(consumer);
            } catch (Throwable t) {
                // 1 つ壊れても他のパックは生かす。黙って消えると「モデルが出ない」で迷う。
                com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.error(
                    "[RTMU/Fabric] 追加パックの読み込みに失敗", t);
            }
        }
    }

    /** 生成し直したパックを反映するために作り直す (README 同意後など)。 */
    public static boolean hasSources() {
        return !SOURCES.isEmpty();
    }
}
