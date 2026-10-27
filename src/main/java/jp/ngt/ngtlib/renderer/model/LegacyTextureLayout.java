package jp.ngt.ngtlib.renderer.model;

import java.util.Map;

/** ModelObject's 1.7.10 material-name/default lookup and ordered light texture slots. */
public final class LegacyTextureLayout {
    private LegacyTextureLayout() {}

    public static String resolve(String materialName, Map<String, String> textures) {
        if (textures == null) return null;
        // MQO permits faces without a Material chunk. Immutable definition maps reject null keys.
        String value = materialName == null ? null : textures.get(materialName);
        return value == null ? textures.get("default") : value;
    }

    public static TextureSet[] create(PolygonModel model, Map<String, String> textures) {
        if (model.materials.isEmpty()) return new TextureSet[]{fromDescriptor((byte) 0, textures.get("default"))};
        int size = model.materials.values().stream().mapToInt(material -> material.id & 255).max().orElse(0) + 1;
        TextureSet[] result = new TextureSet[size];
        model.materials.forEach((name, material) -> result[material.id & 255] = fromDescriptor(material.id, resolve(name, textures)));
        return result;
    }

    public static TextureSet fromDescriptor(byte id, String descriptor) {
        if (descriptor == null) return new TextureSet(new Material(id, null));
        int separator = descriptor.indexOf("|ptmeta=");
        String path = separator < 0 ? descriptor : descriptor.substring(0, separator);
        String[] metadata = separator < 0 ? new String[0] : descriptor.substring(separator + 8).split(",", -1);
        String options = metadata.length == 0 ? "" : metadata[0];
        String[] lights = metadata.length <= 1 ? new String[0] : java.util.Arrays.copyOfRange(metadata, 1, metadata.length);
        Material material = new Material(id, new jp.ngt.mccompat.ResourceLocation(path));
        TextureSet result = new TextureSet(material, lights.length > 0 ? lights.length : options.contains("Light") ? 3 : 0,
                options.contains("AlphaBlend"), lights);
        result.lightOptionDeclared = options.contains("Light");
        return result;
    }
}
