package com.portofino.realtrainmodunofficial.util;

import java.io.InputStream;
import java.util.Locale;

/** Keep legacy case-sensitive names usable while packaged Minecraft resources use valid lowercase IDs. */
public final class BundledResourceStreams {
    private BundledResourceStreams() {}
    public static InputStream open(Class<?> owner,String path) {
        InputStream input=owner.getResourceAsStream(path);
        return input!=null?input:owner.getResourceAsStream(path.toLowerCase(Locale.ROOT));
    }
}
