package com.portofino.realtrainmodunofficial.modelpack;

import com.google.gson.JsonArray;

/** Lossless separation of the original third options… */
public final class LegacyTextureDescriptor {
    private LegacyTextureDescriptor() {}
    public static String encode(JsonArray texture) {
        String path=texture.get(1).getAsString();
        if(texture.size()<3) return path;
        String options=texture.get(2).getAsString();
        // Commas inside this field separate flags, never im…
        StringBuilder result=new StringBuilder(path).append("|ptmeta=");
        if(options.contains("Light")) result.append("Light");
        if(options.contains("AlphaBlend")) result.append("AlphaBlend");
        for(int i=3;i<texture.size();i++) result.append(',').append(texture.get(i).getAsString());
        return result.toString();
    }
}
