package net.neoforged.neoforge.registries;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * シム: Fabric は登録が即時なので、生成済みインスタンスを保持するだけの Holder。
 */
public class DeferredHolder<R, T extends R> implements Supplier<T> {
    private final ResourceLocation id;
    private final T value;

    protected DeferredHolder(ResourceLocation id, T value) {
        this.id = id;
        this.value = value;
    }

    public static <R, T extends R> DeferredHolder<R, T> of(ResourceLocation id, T value) {
        return new DeferredHolder<>(id, value);
    }

    @Override
    public T get() {
        return value;
    }

    public T value() {
        return value;
    }

    public ResourceLocation getId() {
        return id;
    }

    /** シム: Fabric では登録が即時なので常に束縛済み。 */
    public boolean isBound() {
        return true;
    }

    /** 登録名。NeoForge の Holder API に合わせる。 */
    public net.minecraft.resources.ResourceLocation getKey() {
        return getId();
    }
}
