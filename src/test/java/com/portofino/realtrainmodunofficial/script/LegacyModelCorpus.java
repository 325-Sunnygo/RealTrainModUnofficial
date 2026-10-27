package com.portofino.realtrainmodunofficial.script;

import jp.ngt.ngtlib.renderer.model.*;
import java.nio.file.*;
import java.util.*;

/** Parses all available MQO/MQOZ model files through the production script-model loader. */
public final class LegacyModelCorpus {
    public static void main(String[] args) throws Exception {
        List<Path> roots = List.of(Path.of("src/main/resources/assets/minecraft/models"),
                Path.of("../reference/KaizPatchX-master/src/main/resources/assets/minecraft/models"),
                Path.of("bundled_pack_src"));
        int files = 0, faces = 0, bakedFiles = 0, materialLess = 0;
        Class<?> loader = com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.class;
        Class<?> openerType = Class.forName(loader.getName() + "$TextureOpener");
        Object opener = java.lang.reflect.Proxy.newProxyInstance(openerType.getClassLoader(), new Class<?>[]{openerType},
                (proxy, method, values) -> method.getName().equals("getPackKey") ? "legacy-corpus" : null);
        java.lang.reflect.Method bake = loader.getDeclaredMethod("bake", String.class, openerType, Map.class, boolean.class);
        bake.setAccessible(true);
        org.apache.logging.log4j.core.config.Configurator.setLevel(loader.getName(), org.apache.logging.log4j.Level.ERROR);
        List<String> failures = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            List<Path> models;
            try (var paths = Files.walk(root)) {
                models = paths.filter(p -> p.toString().endsWith(".mqo") || p.toString().endsWith(".mqoz")).sorted().toList();
            }
            for (Path path : models) {
                try {
                    PolygonModel model = ModelLoader.parse(Files.readAllBytes(path), path.getFileName().toString());
                    if (model.groupObjects.isEmpty()) throw new AssertionError("no objects");
                    for (GroupObject group : model.groupObjects) {
                        for (Face face : group.faces) {
                            if (face.vertices.length % 3 != 0) throw new AssertionError("incomplete triangle list");
                            for (Vertex vertex : face.vertices) {
                                if (vertex == null || !Float.isFinite(vertex.x) || !Float.isFinite(vertex.y) || !Float.isFinite(vertex.z))
                                    throw new AssertionError("invalid vertex");
                            }
                            if (face.faceNormal == null || !Float.isFinite(face.faceNormal.x)
                                    || !Float.isFinite(face.faceNormal.y) || !Float.isFinite(face.faceNormal.z))
                                throw new AssertionError("invalid face normal");
                            faces++;
                        }
                    }
                    if (model.materials.isEmpty()) materialLess++;
                    String text = MqoReader.extractText(Files.readAllBytes(path), path.getFileName().toString());
                    var baked = (com.portofino.realtrainmodunofficial.client.model.MqoModelLoader.MqoModel)
                            bake.invoke(null, text, opener, Map.of(), true);
                    boolean sourceHasFaces = model.groupObjects.stream().anyMatch(group -> !group.faces.isEmpty());
                    if (sourceHasFaces && baked.getOriginalGroupNames().isEmpty()) throw new AssertionError("baker lost all objects");
                    bakedFiles++;
                    files++;
                } catch (Throwable failure) {
                    failures.add(path + " :: " + failure);
                }
            }
        }
        System.out.println("Legacy model corpus: files=" + files + " faces=" + faces + " baked=" + bakedFiles + " materialLess=" + materialLess + " failed=" + failures.size());
        failures.forEach(System.out::println);
        if (!failures.isEmpty()) throw new AssertionError("Model corpus failures: " + failures.size());
    }
}
