package com.myname.legacyloader.bridge.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.7.10 の {@code net.minecraftforge.client.model.AdvancedModelLoader} の代役。
 * {@code loadModel(ResourceLocation)} で .obj モデルを返す。
 *
 * <p>重要: mod は PreInit (初期化フェーズ) で loadModel を呼ぶが、その時点では
 * リソースマネージャにレガシーパックがまだ載っていない。そこで実パースは
 * 初回描画時 (renderAll) まで遅延する。
 */
public final class LegacyAdvancedModelLoader {

    private LegacyAdvancedModelLoader() {
    }

    public static LegacyModelCustom loadModel(ResourceLocation location) {
        if (location == null) {
            return null;
        }
        return new LazyModel(location);
    }

    /** 初回 renderAll 時に実パースする遅延モデル。パース結果はローカルにキャッシュ。 */
    private static final class LazyModel implements LegacyModelCustom {
        private final ResourceLocation location;
        private volatile LegacyModelCustom delegate;

        LazyModel(ResourceLocation location) {
            this.location = location;
        }

        private LegacyModelCustom resolve() {
            LegacyModelCustom d = delegate;
            if (d != null) {
                return d;
            }
            synchronized (this) {
                if (delegate == null) {
                    delegate = parse(location);
                }
                return delegate;
            }
        }

        @Override public String getType() { return "obj"; }
        @Override public void renderAll() { resolve().renderAll(); }
        @Override public void renderOnly(String... groupNames) { resolve().renderOnly(groupNames); }
        @Override public void renderPart(String partName) { resolve().renderPart(partName); }
    }

    private static final Map<ResourceLocation, LegacyModelCustom> CACHE = new ConcurrentHashMap<>();

    private static LegacyModelCustom parse(ResourceLocation location) {
        LegacyModelCustom cached = CACHE.get(location);
        if (cached != null) {
            return cached;
        }
        LegacyModelCustom model = doParse(location);
        CACHE.put(location, model);
        return model;
    }

    private static LegacyModelCustom doParse(ResourceLocation location) {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open()) {
                    return new LegacyWavefrontObject(in);
                }
            }
            com.myname.legacyloader.LegacyLoaderMod.LOGGER.warn(
                    "LegacyLoader: OBJ model not found: {}", location);
        } catch (Exception e) {
            com.myname.legacyloader.LegacyLoaderMod.LOGGER.warn(
                    "LegacyLoader: OBJ model load error: {}", location, e);
        }
        return new EmptyModel();
    }

    /** 読み込み失敗時のダミー (描画なし)。 */
    private static final class EmptyModel implements LegacyModelCustom {
        @Override public String getType() { return "obj"; }
        @Override public void renderAll() { }
        @Override public void renderOnly(String... groupNames) { }
        @Override public void renderPart(String partName) { }
    }
}
