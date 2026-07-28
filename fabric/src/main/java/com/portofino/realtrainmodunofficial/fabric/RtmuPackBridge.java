package com.portofino.realtrainmodunofficial.fabric;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.RepositorySource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code AddPackFindersEvent} で集めたパックを Fabric へ差し込む。
 *
 * <p>NeoForge は {@link RepositorySource} を受け取ってリソースパック一覧へ足す。Fabric API の
 * {@code ResourceManagerHelper.registerBuiltinResourcePack} は「mod の jar 内のフォルダ」しか
 * 受け付けず、RTMU が作るような<b>実行時生成のパック</b>を扱えない。
 *
 * <p>そこで集めた source を自前で保持し、バニラの {@code PackRepository} へ直接足す。
 * 起動時に 1 回だけ行う。
 *
 * <p>RTMU がここへ流すもの:
 * <ul>
 *   <li>生成サウンドパック (README 同意後に作り直すもの)</li>
 *   <li>mods フォルダの 1.7.10 建材から作る blockstate/model/texture/lang</li>
 * </ul>
 * どちらも実行時に中身が決まるので、jar 同梱では代用できない。
 */
public final class RtmuPackBridge {

    private static final List<RepositorySource> SOURCES = new ArrayList<>();

    private RtmuPackBridge() {
    }

    /** 集めた source を控えて、以後の {@link #collectInto} で流せるようにする。 */
    public static void install(AddPackFindersEvent event) {
        SOURCES.clear();
        SOURCES.addAll(event.getSources());
        if (!SOURCES.isEmpty()) {
            com.portofino.realtrainmodunofficial.RealTrainModUnofficial.LOGGER.info(
                "[RTMU/Fabric] 追加パック提供元を {} 件受け取りました", SOURCES.size());
        }
    }

    /**
     * バニラの {@code PackRepository} が一覧を作るときに呼ばれる (mixin から)。
     * <p>NeoForge のパックファインダと同じ位置で足す。
     */
    public static void collectInto(java.util.function.Consumer<Pack> consumer) {
        for (RepositorySource source : SOURCES) {
            try {
                source.loadPacks(consumer);
            } catch (Throwable t) {
                //1 つ壊れても他のパックは生かす。黙って消えると「モデルが出ない」で迷う。
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
