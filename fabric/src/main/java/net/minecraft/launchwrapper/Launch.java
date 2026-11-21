package net.minecraft.launchwrapper;

import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class Launch {
    public static final Map<String, Object> blackboard = new HashMap<>();
    public static final File minecraftHome = FMLPaths.GAMEDIR.get().toFile();

    private Launch() {
    }
}
