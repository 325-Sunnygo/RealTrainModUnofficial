package com.portofino.realtrainmodunofficial.client.sound;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ExternalSoundPackBridge {
    private static final String PACK_ID = "realtrainmodunofficial:external_sound_bridge";
    private static final Component PACK_TITLE = Component.literal("RTM External Sounds");
    private static final Path GENERATED_PACK_ROOT = FMLPaths.GAMEDIR.get()
        .resolve("config")
        .resolve("realtrainmodunofficial")
        .resolve("generated_sound_pack");

    private ExternalSoundPackBridge() {
    }

    /**
     * 生成サウンドパックを作り直す (パック同意状態が変わった後などに呼ぶ)。
     * <p>生成パックはリソースパックとして扱われるため、ファイルを作り直すだけでは
     * {@code SoundManager} には反映されない。呼び出し後に必ず
     * {@link net.minecraft.client.Minecraft#reloadResourcePacks()} でリソースを再読込すること。
     * これをしないと、タイトル画面で README 同意したパックの走行音等が
     * 「パックには入っているのに次回起動まで鳴らない」状態になる。
     */
    public static void rebuild() {
        rebuildGeneratedPack();
    }

    public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path packRoot = rebuildGeneratedPack();
        if (packRoot == null) {
            return;
        }
        Pack pack = Pack.readMetaAndCreate(
            new PackLocationInfo(PACK_ID, PACK_TITLE, PackSource.BUILT_IN, Optional.empty()),
            new PathPackResources.PathResourcesSupplier(packRoot),
            PackType.CLIENT_RESOURCES,
            new PackSelectionConfig(true, Pack.Position.TOP, false)
        );
        if (pack == null) {
            RealTrainModUnofficial.LOGGER.warn("Generated external sound bridge pack could not be registered");
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    private static Path rebuildGeneratedPack() {
        try {
            deleteDirectoryIfExists(GENERATED_PACK_ROOT);
            Files.createDirectories(GENERATED_PACK_ROOT);
            Map<String, JsonObject> mergedSoundDefs = new HashMap<>();
            for (Path candidate : collectCandidatePacks()) {
                try {
                    //README 同意ゲート: 未同意/拒否のパックの音は取り込まない (zip のみ対象)。
                    if (!Files.isDirectory(candidate)
                            && !com.portofino.realtrainmodunofficial.pack.PackConsent.isAllowed(candidate)) {
                        continue;
                    }
                    if (Files.isDirectory(candidate)) {
                        collectFromDirectory(candidate, mergedSoundDefs);
                    } else if (isSupportedArchive(candidate)) {
                        collectFromArchive(candidate, mergedSoundDefs);
                    }
                } catch (Exception e) {
                }
            }
            writeMergedSoundsJson(mergedSoundDefs);
            //中身が空でも pack.mcmeta を書いて<b>有効なパックとして常に返す</b>。こうしておくと、
            //起動時に同意済みの音パックが 1 つも無くても pack が登録され、後からタイトル画面で
            //README 同意 → {@link #rebuild()} + reloadResourcePacks したときに新しい音を足せる。
            //(以前は空だと pack ごと削除して null=未登録にしていたため、その状況では同意しても
            // 再読込で音が復活しなかった。)
            writePackMeta();
            return GENERATED_PACK_ROOT;
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Could not build external sound bridge pack", e);
            return null;
        }
    }

    private static List<Path> collectCandidatePacks() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        List<Path> roots = List.of(
            gameDir.resolve("mods"),
            gameDir.resolve("mods").resolve("modelpacks"),
            gameDir.resolve("content"),
            gameDir.resolve("vehicle_packs"),
            com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get(),
            gameDir.resolve("config").resolve("realtrainmodunofficial"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("vehicle_packs"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("packs"),
            gameDir.resolve("config").resolve("realtrainmodunofficial").resolve("rail_packs")
        );
        Set<Path> unique = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                stream.forEach(path -> {
                    if (!path.equals(GENERATED_PACK_ROOT)) {
                        unique.add(path);
                    }
                });
            } catch (IOException e) {
            }
        }
        return new ArrayList<>(unique);
    }

    private static boolean collectFromDirectory(Path packDir, Map<String, JsonObject> mergedSoundDefs) throws IOException {
        String rootNamespace = namespaceFromPackName(packDir.getFileName().toString());
        boolean copiedAny = false;
        Path rootSoundsJson = packDir.resolve("sounds.json");
        if (Files.isRegularFile(rootSoundsJson)) {
            mergeSoundDefinitions(rootNamespace, Files.readString(rootSoundsJson), mergedSoundDefs);
            copiedAny = true;
        }
        Path rootSoundsDir = packDir.resolve("sounds");
        if (Files.isDirectory(rootSoundsDir)) {
            try (var walk = Files.walk(rootSoundsDir)) {
                for (Path source : walk.filter(Files::isRegularFile).toList()) {
                    Path relative = rootSoundsDir.relativize(source);
                    Path target = GENERATED_PACK_ROOT.resolve("assets").resolve(rootNamespace).resolve("sounds")
                        .resolve(lowercasePath(relative));
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    registerCopiedSound(mergedSoundDefs, rootNamespace, relative);
                    copiedAny = true;
                }
            }
        }
        Path assetsDir = packDir.resolve("assets");
        if (!Files.isDirectory(assetsDir)) {
            return copiedAny;
        }
        try (var namespaces = Files.list(assetsDir)) {
            for (Path namespaceDir : namespaces.toList()) {
                if (!Files.isDirectory(namespaceDir)) {
                    continue;
                }
                String namespace = namespaceDir.getFileName().toString().toLowerCase(Locale.ROOT);
                Path soundsJson = namespaceDir.resolve("sounds.json");
                if (Files.isRegularFile(soundsJson)) {
                    mergeSoundDefinitions(namespace, Files.readString(soundsJson), mergedSoundDefs);
                    copiedAny = true;
                }
                Path soundsDir = namespaceDir.resolve("sounds");
                if (Files.isDirectory(soundsDir)) {
                    try (var walk = Files.walk(soundsDir)) {
                        for (Path source : walk.filter(Files::isRegularFile).toList()) {
                            Path relative = soundsDir.relativize(source);
                            Path target = GENERATED_PACK_ROOT.resolve("assets").resolve(namespace).resolve("sounds")
                                .resolve(lowercasePath(relative));
                            Files.createDirectories(target.getParent());
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                            registerCopiedSound(mergedSoundDefs, namespace, relative);
                            copiedAny = true;
                        }
                    }
                }
            }
        }
        return copiedAny;
    }

    private static boolean collectFromArchive(Path archive, Map<String, JsonObject> mergedSoundDefs) throws IOException {
        boolean copiedAny = false;
        String rootNamespace = namespaceFromPackName(archive.getFileName().toString());
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String normalized = normalize(entry.getName());
                String lower = normalized.toLowerCase(Locale.ROOT);
                if (lower.equals("sounds.json")) {
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        mergeSoundDefinitions(rootNamespace, readUtf8(input), mergedSoundDefs);
                        copiedAny = true;
                    }
                    continue;
                }
                if (lower.startsWith("sounds/")) {
                    String[] parts = normalized.split("/");
                    Path target = GENERATED_PACK_ROOT.resolve("assets").resolve(rootNamespace).resolve("sounds");
                    for (int i = 1; i < parts.length; i++) {
                        target = target.resolve(parts[i].toLowerCase(Locale.ROOT));
                    }
                    Files.createDirectories(target.getParent());
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        Files.write(target, input.readAllBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    }
                    registerCopiedSound(mergedSoundDefs, rootNamespace, Path.of(String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length))));
                    copiedAny = true;
                    continue;
                }
                if (!lower.startsWith("assets/")) {
                    continue;
                }
                String[] parts = normalized.split("/");
                if (parts.length < 3) {
                    continue;
                }
                // 名前空間は小文字化必須(MC のリソース名前空間は小文字のみ)。RTM パックには
                // "sound_MasaCrossings" 等の大文字名前空間があり、そのままだと MC が読めず音が鳴らない。
                String namespace = parts[1].toLowerCase(Locale.ROOT);
                if (lower.equals("assets/" + namespace + "/sounds.json")) {
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        mergeSoundDefinitions(namespace, readUtf8(input), mergedSoundDefs);
                        copiedAny = true;
                    }
                    continue;
                }
                if (parts.length >= 4 && "sounds".equalsIgnoreCase(parts[2])) {
                    Path target = GENERATED_PACK_ROOT.resolve("assets").resolve(namespace).resolve("sounds");
                    for (int i = 3; i < parts.length; i++) {
                        target = target.resolve(sanitizeSoundPath(parts[i]));
                    }
                    Files.createDirectories(target.getParent());
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        Files.write(target, input.readAllBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                    }
                    registerCopiedSound(mergedSoundDefs, namespace, Path.of(String.join("/", java.util.Arrays.copyOfRange(parts, 3, parts.length))));
                    copiedAny = true;
                }
            }
        }
        return copiedAny;
    }

    private static Path lowercasePath(Path path) {
        Path lowered = Path.of("");
        for (Path part : path) {
            lowered = lowered.resolve(sanitizeSoundPath(part.toString()));
        }
        return lowered;
    }

    /**
     * ResourceLocation のパスに使えない文字 (空白・大文字・記号) を安全化する。
     * 1.21 のリソースパスは [a-z0-9/._-] のみ許可。本家 1.7.10 は緩く、hi03 Train Melodies の
     * "Amairo no kami no otome.ogg" のように<b>空白・大文字入りのサウンド名</b>を持つパックがあり、
     * そのままだと ResourceLocation 生成が例外→無音 (踏切ならデフォルトのベル音にフォールバック) になる。
     * <b>生成側 (このパック) と再生側 (CrossingGateSoundManager / LegacyScriptSoundManager) で必ず同じ変換</b>を
     * 使うこと。そろっていないと参照先がずれて鳴らない。
     */
    public static String sanitizeSoundPath(String s) {
        if (s == null) {
            return "";
        }
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '/' || c == '.' || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private static void registerCopiedSound(Map<String, JsonObject> mergedSoundDefs, String namespace, Path relativePath) {
        if (relativePath == null) {
            return;
        }
        String soundPath = sanitizeSoundPath(normalize(relativePath.toString()));
        if (!soundPath.endsWith(".ogg")) {
            return;
        }
        soundPath = soundPath.substring(0, soundPath.length() - ".ogg".length());
        if (soundPath.isBlank()) {
            return;
        }
        String eventKey = soundPath.replace('/', '.');
        JsonObject target = mergedSoundDefs.computeIfAbsent(namespace, ignored -> new JsonObject());
        if (target.has(eventKey)) {
            return;
        }
        JsonObject event = new JsonObject();
        JsonArray sounds = new JsonArray();
        sounds.add(soundReferenceWithRange(namespace + ":" + soundPath));
        event.add("sounds", sounds);
        target.add(eventKey, event);
    }

    /**
     * レガシー音の可聴距離 (ブロック)。MC 既定の 16 は列車には短すぎて
     * 「音がすぐ消える」ため、45 ブロックかけて線形に減衰させる。
     * 実際の減衰終端は max(音量,1)×この値 (音量は {@link LegacyScriptSoundManager} 側で 1.0 に制限)。
     */
    private static final int LEGACY_ATTENUATION_DISTANCE = 45;

    /** "ns:path" の音参照を attenuation_distance 付きのオブジェクト形式にする。 */
    private static JsonObject soundReferenceWithRange(String name) {
        JsonObject sound = new JsonObject();
        sound.addProperty("name", name);
        sound.addProperty("attenuation_distance", LEGACY_ATTENUATION_DISTANCE);
        return sound;
    }

    private static void mergeSoundDefinitions(String namespace, String jsonText, Map<String, JsonObject> mergedSoundDefs) {
        try (Reader reader = new StringReader(jsonText)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject target = mergedSoundDefs.computeIfAbsent(namespace, ignored -> new JsonObject());
            JsonObject source = parsed.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                target.add(sanitizeSoundPath(entry.getKey()), normalizeSoundEvent(namespace, entry.getValue()));
            }
        } catch (Exception e) {
        }
    }

    private static JsonElement normalizeSoundEvent(String namespace, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return element;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                result.add(normalizeSoundEvent(namespace, child));
            }
            return result;
        }
        if (!element.isJsonObject()) {
            return element.deepCopy();
        }
        JsonObject copy = element.getAsJsonObject().deepCopy();
        JsonElement sounds = copy.get("sounds");
        if (sounds != null && sounds.isJsonArray()) {
            JsonArray normalizedSounds = new JsonArray();
            for (JsonElement soundEntry : sounds.getAsJsonArray()) {
                normalizedSounds.add(normalizeSoundReference(namespace, soundEntry));
            }
            copy.add("sounds", normalizedSounds);
        }
        return copy;
    }

    private static JsonElement normalizeSoundReference(String namespace, JsonElement soundEntry) {
        if (soundEntry == null || soundEntry.isJsonNull()) {
            return soundEntry;
        }
        if (soundEntry.isJsonPrimitive() && soundEntry.getAsJsonPrimitive().isString()) {
            //文字列参照はオブジェクト形式へ変換し、可聴距離 45 を付与する
            return soundReferenceWithRange(namespacedSoundPath(namespace, soundEntry.getAsString()));
        }
        if (!soundEntry.isJsonObject()) {
            return soundEntry.deepCopy();
        }
        JsonObject copy = soundEntry.getAsJsonObject().deepCopy();
        JsonElement name = copy.get("name");
        if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()) {
            copy.addProperty("name", namespacedSoundPath(namespace, name.getAsString()));
        }
        copy.addProperty("attenuation_distance", LEGACY_ATTENUATION_DISTANCE);
        return copy;
    }

    private static String namespacedSoundPath(String namespace, String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        // 既に "ns:path" 形式ならその名前空間を小文字化、無ければ与えられた名前空間を前置(小文字)。
        // RTM パックの sounds.json は "sound_MasaCrossings:machine/..." のように大文字名前空間を
        // 含むことがあり、小文字化しないと MC が .ogg を見つけられず無音になる。
        int colon = raw.indexOf(':');
        String ns = colon >= 0 ? raw.substring(0, colon) : namespace;
        String path = colon >= 0 ? raw.substring(colon + 1) : raw;
        if (ns == null || ns.isBlank() || "minecraft".equalsIgnoreCase(ns)) {
            return raw;
        }
        return sanitizeSoundPath(ns) + ":" + sanitizeSoundPath(path);
    }

    /**
     * 生成パックに含まれるレガシー音の名前空間 (sound_mugenlib, rtm 等)。
     * {@link LegacyStereoDownmix} が「どの音をモノラル化するか」の判定に使う。
     */
    private static final java.util.Set<String> GENERATED_NAMESPACES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static java.util.Set<String> generatedNamespaces() {
        return GENERATED_NAMESPACES;
    }

    private static boolean writeMergedSoundsJson(Map<String, JsonObject> mergedSoundDefs) throws IOException {
        boolean wroteAny = false;
        for (Map.Entry<String, JsonObject> entry : mergedSoundDefs.entrySet()) {
            GENERATED_NAMESPACES.add(entry.getKey());
            Path target = GENERATED_PACK_ROOT.resolve("assets").resolve(entry.getKey()).resolve("sounds.json");
            Files.createDirectories(target.getParent());
            Files.writeString(
                target,
                entry.getValue().toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
            wroteAny = true;
        }
        return wroteAny;
    }

    private static void writePackMeta() throws IOException {
        int packFormat = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        String packMeta = """
            {
              "pack": {
                "pack_format": %d,
                "description": "RTM external sound bridge"
              }
            }
            """.formatted(packFormat);
        Files.writeString(
            GENERATED_PACK_ROOT.resolve("pack.mcmeta"),
            packMeta,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }

    private static String normalize(String raw) {
        return raw.replace('\\', '/').replaceFirst("^/+", "");
    }

    private static String namespaceFromPackName(String packName) {
        String base = packName == null ? "rtm_pack" : packName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String normalized = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return normalized.isBlank() ? "rtm_pack" : normalized;
    }

    private static boolean isSupportedArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".jar");
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        input.transferTo(buffer);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static void deleteDirectoryIfExists(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
