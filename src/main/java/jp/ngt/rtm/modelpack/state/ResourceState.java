package jp.ngt.rtm.modelpack.state;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 本家 jp.ngt.rtm.modelpack.state.ResourceState のスクリプト互換最小移植。
 * スクリプトは getDataMap / getResourceName / add|removeExclusionParts を使う。
 */
public class ResourceState {
    /**
     * 本家 ResourceState.color の既定値は 16777215 (0xFFFFFF = 白)。
     * Java の既定値 0 のままにしていると黒になる。
     */
    public int color = 16777215;

    /** 本家は public フィールド。スクリプトが state.dataMap と直接読む。 */
    public final DataMap dataMap = new DataMap();
    private final Supplier<String> nameSupplier;

    /**
     * 本家 ResourceState.getResourceSet が返す ModelSet の供給元。
     * 新しめのパックスクリプト 等、hi03 系) は設定を
     * entity.getResourceState.getResourceSet.getConfig で読む
     * (古いパックは entity.getModelSet.getConfig)。
     */
    private final Supplier<jp.ngt.rtm.modelpack.modelset.ModelSetCompat> resourceSetSupplier;

    /**
     * 本家 ResourceState.exclusionParts: 「今は描かないパーツ」の名前。
     * RTM 標準スクリプトはドアの開閉をこれで表現する。
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

    /** 本家 getArg: DataMap の全エントリを key=(Type)value,... で返す。 */
    public String getArg() {
        return this.dataMap.getArg();
    }

    /** 本家 setArg: 同形式の文字列を DataMap へ取り込む。 */
    public void setArg(String arg, boolean overwrite) {
        this.dataMap.setArg(arg, overwrite);
    }

    /**
     * 本家 ResourceState.getResourceSet 互換。
     * entity.getResourceState.getResourceSet.getConfig 用で、
     * entity.getModelSet と同じ jp.ngt.rtm.modelpack.modelset.ModelSetCompat
     * (getConfig を持つ) を返す。
     */
    public jp.ngt.rtm.modelpack.modelset.ModelSetCompat getResourceSet() {
        return this.resourceSetSupplier.get();
    }

    public String getResourceName() {
        return this.nameSupplier.get();
    }

    /** 本家 ResourceState.name (モデル選択画面で付けるカスタム名)。未設定なら null。 */
    private String customName;

    /**
     * 本家 getName: カスタム名が付いていればそれ、無ければモデル名。
     * 本家は未設定を "no_name" にするので、その挙動も合わせる。
     */
    public String getName() {
        if (this.customName != null && !this.customName.isEmpty()) {
            return this.customName;
        }
        return this.getResourceName();
    }

    /** 本家 setName: 空なら "no_name" に丸める。 */
    public void setName(String name) {
        this.customName = (name == null || name.isEmpty()) ? "no_name" : name;
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

    /** @return 除外中のパーツ名 (正規化済み)。空なら除外なし。 */
    public Set<String> getExclusionParts() {
        return this.exclusionParts.isEmpty() ? Set.of() : Collections.unmodifiableSet(this.exclusionParts);
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
