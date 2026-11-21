package net.neoforged.neoforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * シム: Fabric では登録タイミングの制約が無いため、register 呼び出しで
 * 即座にバニラ Registry へ登録する。register(IEventBus) は no-op。
 */
public class DeferredRegister<T> {
    private final Registry<T> registry;
    private final String namespace;
    private final List<DeferredHolder<T, ? extends T>> entries = new ArrayList<>();

    protected DeferredRegister(Registry<T> registry, String namespace) {
        this.registry = registry;
        this.namespace = namespace;
    }

    public static <T> DeferredRegister<T> create(Registry<T> registry, String namespace) {
        return new DeferredRegister<>(registry, namespace);
    }

    @SuppressWarnings("unchecked")
    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String namespace) {
        Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
        if (registry == null) {
            throw new IllegalArgumentException("Unknown registry: " + key);
        }
        return new DeferredRegister<>(registry, namespace);
    }

    public static Blocks createBlocks(String namespace) {
        return new Blocks(namespace);
    }

    public static Items createItems(String namespace) {
        return new Items(namespace);
    }

    /**
     * データコンポーネント用。NeoForge は専用サブクラスを返すので形を合わせる。
     * 第 1 引数のレジストリキーは NeoForge 版の呼び出しに合わせて受けるだけで、
     * 実体は常に BuiltInRegistries.DATA_COMPONENT_TYPE。
     */
    public static DataComponents createDataComponents(
            ResourceKey<? extends Registry<net.minecraft.core.component.DataComponentType<?>>> key,
            String namespace) {
        return new DataComponents(namespace);
    }

    public static DataComponents createDataComponents(String namespace) {
        return new DataComponents(namespace);
    }

    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, name);
        I value = supplier.get();
        Registry.register(registry, id, value);
        DeferredHolder<T, I> holder = DeferredHolder.of(id, value);
        entries.add(holder);
        return holder;
    }

    /** Fabric では登録が即時のため、バス接続は不要。 */
    public void register(IEventBus bus) {
    }

    public List<DeferredHolder<T, ? extends T>> getEntries() {
        return entries;
    }

    protected String namespace() {
        return namespace;
    }

    /** データコンポーネント特化型 (NeoForge の DeferredRegister.DataComponents 相当)。 */
    public static class DataComponents
            extends DeferredRegister<net.minecraft.core.component.DataComponentType<?>> {
        protected DataComponents(String namespace) {
            super(BuiltInRegistries.DATA_COMPONENT_TYPE, namespace);
        }

        /**
         * NeoForge の registerComponentType(name, builderOp) 相当。
         * ビルダーを渡して型を組み立てさせる形なので、こちらで新しいビルダーを用意して渡す。
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <D> DeferredHolder<net.minecraft.core.component.DataComponentType<?>,
                net.minecraft.core.component.DataComponentType<D>> registerComponentType(
                        String name,
                        java.util.function.UnaryOperator<net.minecraft.core.component.DataComponentType
                                .Builder<D>> builderOp) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace(), name);
            net.minecraft.core.component.DataComponentType<D> type =
                builderOp.apply(net.minecraft.core.component.DataComponentType.builder()).build();
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, type);
            return (DeferredHolder) DeferredHolder.of(id, type);
        }
    }

    // ---- Blocks / Items 特化型 ----

    public static class Blocks extends DeferredRegister<Block> {
        protected Blocks(String namespace) {
            super(BuiltInRegistries.BLOCK, namespace);
        }

        public <B extends Block> DeferredBlock<B> register(String name, Supplier<? extends B> supplier) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace(), name);
            B value = supplier.get();
            Registry.register(BuiltInRegistries.BLOCK, id, value);
            return DeferredBlock.createBlock(id, value);
        }
    }

    public static class Items extends DeferredRegister<Item> {
        protected Items(String namespace) {
            super(BuiltInRegistries.ITEM, namespace);
        }

        public <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> supplier) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace(), name);
            I value = supplier.get();
            Registry.register(BuiltInRegistries.ITEM, id, value);
            return DeferredItem.createItem(id, value);
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
            return register(name, () -> factory.apply(new Item.Properties()));
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block) {
            return register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }
}
