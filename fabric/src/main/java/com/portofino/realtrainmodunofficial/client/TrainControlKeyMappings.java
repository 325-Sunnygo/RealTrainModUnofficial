package com.portofino.realtrainmodunofficial.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class TrainControlKeyMappings {
    private static final String CATEGORY = "key.categories.realtrainmodunofficial";

    public static final KeyMapping OPEN_CONTROL = key("open_control", GLFW.GLFW_KEY_E);
    public static final KeyMapping POWER_OFF = key("power_off", GLFW.GLFW_KEY_S);
    public static final KeyMapping BRAKE_OFF = key("brake_off", GLFW.GLFW_KEY_W);
    public static final KeyMapping NEUTRAL = key("neutral", GLFW.GLFW_KEY_X);
    public static final KeyMapping TOGGLE_CAB = key("toggle_cab", GLFW.GLFW_KEY_U);
    public static final KeyMapping PLAY_ANNOUNCEMENT = key("play_announcement", GLFW.GLFW_KEY_I);
    public static final KeyMapping PLAY_HORN = key("play_horn", GLFW.GLFW_KEY_P);
    public static final KeyMapping TOGGLE_RENDER_PROFILER = key("toggle_render_profiler", GLFW.GLFW_KEY_F8);
    public static final KeyMapping FREE_CAMERA = key("free_camera", GLFW.GLFW_KEY_V);

    private TrainControlKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONTROL);
        event.register(POWER_OFF);
        event.register(BRAKE_OFF);
        event.register(NEUTRAL);
        event.register(TOGGLE_CAB);
        event.register(PLAY_ANNOUNCEMENT);
        event.register(PLAY_HORN);
        event.register(TOGGLE_RENDER_PROFILER);
        event.register(FREE_CAMERA);
    }

    public static boolean matchesSneak(int key, int scanCode) {
        return Minecraft.getInstance().options.keyShift.matches(key, scanCode);
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key.realtrainmodunofficial." + name, defaultKey, CATEGORY);
    }
}
