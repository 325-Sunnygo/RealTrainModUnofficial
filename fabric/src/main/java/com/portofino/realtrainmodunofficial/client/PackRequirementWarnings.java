package com.portofino.realtrainmodunofficial.client;

import com.portofino.realtrainmodunofficial.BundledPackStore;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficial;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackRequirementWarnings {
    private static final List<String> WARNINGS = new ArrayList<>();

    private PackRequirementWarnings() {
    }

    public static synchronized void refresh() {
        WARNINGS.clear();
        try {
            List<Path> archives = collectPackArchives();
            List<ArchiveInfo> archiveInfos = archives.stream()
                .map(PackRequirementWarnings::readArchiveInfo)
                .toList();
            Set<String> availableNames = new LinkedHashSet<>();
            for (ArchiveInfo archiveInfo : archiveInfos) {
                availableNames.addAll(archiveInfo.aliases());
            }
            Set<String> missing = new LinkedHashSet<>();
            for (ArchiveInfo archiveInfo : archiveInfos) {
                inspectArchive(archiveInfo, availableNames, missing);
            }
            WARNINGS.addAll(missing);
        } catch (Exception e) {
            RealTrainModUnofficial.LOGGER.warn("Failed to inspect prerequisite packs", e);
        }
    }

    public static synchronized List<String> getWarnings() {
        return List.copyOf(WARNINGS);
    }

    private static List<Path> collectPackArchives() throws IOException {
        Set<Path> unique = new LinkedHashSet<>();
        List<Path> roots = List.of(
            FMLPaths.GAMEDIR.get(),
            FMLPaths.GAMEDIR.get().resolve("mods"),
            FMLPaths.GAMEDIR.get().resolve("content"),
            FMLPaths.GAMEDIR.get().resolve("vehicle_packs"),
            com.portofino.realtrainmodunofficial.DefaultAssetsFolder.get(),
            FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial"),
            FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial").resolve("packs"),
            FMLPaths.GAMEDIR.get().resolve("config").resolve("realtrainmodunofficial").resolve("vehicle_packs")
        );
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.walk(root, 6)) {
                stream.filter(Files::isRegularFile)
                    .filter(PackRequirementWarnings::isArchive)
                    .forEach(unique::add);
            }
        }
        for (String category : List.of("rail", "vehicle", "installed_object")) {
            unique.addAll(BundledPackStore.listBundledPacks(category));
        }
        return new ArrayList<>(unique);
    }

    private static ArchiveInfo readArchiveInfo(Path archive) {
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(cleanDisplayName(archive.getFileName().toString()));
        Set<String> prerequisites = new LinkedHashSet<>();
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            String readme = readReadme(zipFile);
            if (!readme.isBlank()) {
                String title = extractTitle(readme);
                if (!title.isBlank()) {
                    aliases.add(title);
                }
                String prerequisitePack = inferPrerequisitePackName(readme, archive.getFileName().toString());
                if (!prerequisitePack.isBlank()) {
                    prerequisites.add(prerequisitePack);
                }
            }
        } catch (Exception ignored) {
        }
        return new ArchiveInfo(archive, List.copyOf(aliases), List.copyOf(prerequisites));
    }

    private static void inspectArchive(ArchiveInfo archiveInfo, Set<String> availableNames, Set<String> missing) {
        for (String prerequisitePack : archiveInfo.prerequisites()) {
            if (prerequisitePack.isBlank()) {
                continue;
            }
            boolean present = availableNames.stream()
                .map(PackRequirementWarnings::normalizeName)
                .anyMatch(name -> matchesPackName(name, prerequisitePack));
            if (!present) {
                missing.add("前提パックの " + prerequisitePack + " が入ってません！");
            }
        }
    }

    private static String readReadme(ZipFile zipFile) throws IOException {
        for (ZipEntry entry : Collections.list(zipFile.entries())) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            if (entry.isDirectory() || !name.endsWith(".txt")) {
                continue;
            }
            byte[] bytes = zipFile.getInputStream(entry).readAllBytes();
            // UTF-8 → MS932 → Shift_JIS を厳格に試す共通デコーダに一本化。
            // 旧実装の new String(bytes, charset) は例外を投げないため、CP932 専用文字を含む
            // Shift_JIS README を UTF-8 のまま文字化けさせ、前提パック名の抽出を取りこぼしていた。
            String text = com.portofino.realtrainmodunofficial.util.PackTextDecoder.decodeText(bytes);
            if (text.contains("前提パック")) {
                return text;
            }
            if (!looksCorrupted(text)) {
                return text;
            }
        }
        return "";
    }

    private static String inferPrerequisitePackName(String readme, String archiveName) {
        String title = extractTitle(readme);
        if (title.isBlank()) {
            return "";
        }
        if (normalizeName(archiveName).contains(normalizeName(title))) {
            return "";
        }
        return title;
    }

    private static String extractTitle(String readme) {
        return readme.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .filter(line -> !looksCorrupted(line))
            .findFirst()
            .orElse("");
    }

    private static boolean isArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".jar");
    }

    private static boolean looksCorrupted(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        return value.indexOf('\uFFFD') >= 0 || value.contains("�") || value.contains("縺") || value.contains("繧");
    }

    private static boolean matchesPackName(String normalizedAvailable, String prerequisitePack) {
        String normalizedRequired = normalizeName(prerequisitePack);
        if (normalizedAvailable.isBlank() || normalizedRequired.isBlank()) {
            return false;
        }
        return normalizedAvailable.contains(normalizedRequired) || normalizedRequired.contains(normalizedAvailable);
    }

    private static String cleanDisplayName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace(".zip", "").replace(".jar", "");
        return cleaned.trim();
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
            .replace(".zip", "")
            .replace(".jar", "")
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "")
            .replace("　", "");
    }

    private record ArchiveInfo(Path path, List<String> aliases, List<String> prerequisites) {
    }
}
