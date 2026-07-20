package jp.ngt.rtm.modelpack.state;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 本家 jp.ngt.rtm.modelpack.state.ResourceState のスクリプト互換最小移植。
 * スクリプトは getDataMap() / getResourceName() / add|removeExclusionParts() を使う。
 */
public class ResourceState {
    /**
     * 本家 ResourceState.color の既定値は {@code 16777215} (0xFFFFFF = 白)。
     * <p>
     * Java の既定値 0 のままにしていると黒になる。電線スクリプトは
     * {@code tessellator.setColorOpaque_I(renderer.getColor(tileEntity))} でこの値を
     * そのまま頂点色に使うため、既定 0 では<b>電線が真っ黒</b>になる。
     */
    public int color = 16777215;

    private final DataMap dataMap = new DataMap();
    private final Supplier<String> nameSupplier;

    /**
     * 本家 ResourceState.getResourceSet() が返す ModelSet の供給元。
     * <p>
     * 新しめのパックスクリプト (E259 等、hi03 系) は設定を
     * {@code entity.getResourceState().getResourceSet().getConfig()} で読む
     * (古いパックは {@code entity.getModelSet().getConfig()})。エンティティが
     * 自分の {@link jp.ngt.rtm.entity.train.EntityTrainBase#getModelSet()} 等を渡す。
     */
    private final Supplier<jp.ngt.rtm.modelpack.modelset.ModelSetCompat> resourceSetSupplier;

    /**
     * 本家 ResourceState.exclusionParts: 「今は描かないパーツ」の名前。
     * <p>
     * RTM 標準スクリプトはドアの開閉をこれで表現する。ドアが開いた側の扉パーツ
     * (door_fl / door_bl …) を除外リストに入れて、閉じたら外す。除外されたグループは
     * モデル描画から落ちるので、扉が消える = 開いて見える。
     * <p>
     * 描画スレッドが読み、スクリプト(同じ描画スレッド)が書くが、
     * サーバースクリプトからも触られうるので並行セットにしておく。
     * 照合は正規化 (trim + 小文字) 済みの名前で行う (モデル側のグループ名も同じ正規化)。
     */
    private final Set<String> exclusionParts = ConcurrentHashMap.newKeySet();

    public ResourceState(Supplier<String> nameSupplier) {
        this(nameSupplier, () -> null);
    }

    public ResourceState(Supplier<String> nameSupplier,
                         Supplier<jp.ngt.rtm.modelpack.modelset.ModelSetCompat> resourceSetSupplier) {
        this.nameSupplier = nameSupplier;
        this.resourceSetSupplier = resourceSetSupplier == null ? () -> null : resourceSetSupplier;
    }

    public DataMap getDataMap() {
        return this.dataMap;
    }

    /**
     * 本家 ResourceState.getResourceSet() 互換。スクリプトの
     * {@code entity.getResourceState().getResourceSet().getConfig()} 用で、
     * {@code entity.getModelSet()} と同じ {@link jp.ngt.rtm.modelpack.modelset.ModelSetCompat}
     * (getConfig() を持つ) を返す。これが無いと新しめのパック (E259 等) が
     * 「getResourceSet is not a function」で落ち、サーバー onUpdate も描画スクリプトも
     * 中断する (方向幕・座席・ドア・台車・ガラス等が描かれない)。
     */
    public jp.ngt.rtm.modelpack.modelset.ModelSetCompat getResourceSet() {
        return this.resourceSetSupplier.get();
    }

    public String getResourceName() {
        return this.nameSupplier.get();
    }

    public String getName() {
        return this.getResourceName();
    }

    /** 本家 ResourceState.addExclusionParts */
    public void addExclusionParts(String... names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                this.exclusionParts.add(normalize(name));
            }
        }
    }

    /** 本家 ResourceState.removeExclusionParts */
    public void removeExclusionParts(String... names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                this.exclusionParts.remove(normalize(name));
            }
        }
    }

    public void clearExclusionParts() {
        this.exclusionParts.clear();
    }

    /**
     * @return 除外中のパーツ名 (正規化済み)。空なら除外なし。
     */
    public Set<String> getExclusionParts() {
        return this.exclusionParts.isEmpty() ? Set.of() : Collections.unmodifiableSet(this.exclusionParts);
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
