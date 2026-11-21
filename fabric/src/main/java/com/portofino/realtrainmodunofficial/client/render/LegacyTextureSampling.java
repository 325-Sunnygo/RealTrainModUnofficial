package com.portofino.realtrainmodunofficial.client.render;

import java.io.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;

/** SimpleTexture's PNG metadata sampling options, independent of pixel alpha. */
public record LegacyTextureSampling(boolean blur, boolean clamp) {
    public static final LegacyTextureSampling DEFAULT = new LegacyTextureSampling(false,false);
    public static LegacyTextureSampling read(InputStream input) throws IOException {
        if(input==null) return DEFAULT;
        var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        if(!root.has("texture")) return DEFAULT;
        var texture=root.getAsJsonObject("texture");
        return new LegacyTextureSampling(texture.has("blur") && texture.get("blur").getAsBoolean(),
                texture.has("clamp") && texture.get("clamp").getAsBoolean());
    }
    public void apply(net.minecraft.client.renderer.texture.AbstractTexture texture) {
        texture.setFilter(blur,false);
        texture.bind();
        int wrap=clamp?org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE:org.lwjgl.opengl.GL11.GL_REPEAT;
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S,wrap);
        org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T,wrap);
    }
}
