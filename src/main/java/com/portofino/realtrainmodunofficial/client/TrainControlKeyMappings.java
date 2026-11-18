package com.portofino.realtrainmodunofficial.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** 本家 RTMKeyHandlerClient と同じ項目のキー設定。 */
public final class TrainControlKeyMappings {
    public static final String CATEGORY = "key.categories.realtrainmodunofficial";

    public static final KeyMapping KEY_HORN = key("rtm.horn", GLFW.GLFW_KEY_P);
    public static final KeyMapping KEY_CHIME = key("rtm.chime", GLFW.GLFW_KEY_I);
    public static final KeyMapping KEY_ATS = key("rtm.ats", GLFW.GLFW_KEY_COMMA);
    public static final KeyMapping KEY_EB = key("key.rtm.eb", GLFW.GLFW_KEY_5);
    public static final KeyMapping KEY_CHIME_NEXT = key("key.rtm.chime_next", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping KEY_CHIME_PREV = key("key.rtm.chime_prev", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping KEY_REVERSER_FORWARD = key("key.rtm.reverser_forward", GLFW.GLFW_KEY_UP);
    public static final KeyMapping KEY_REVERSER_BACK = key("key.rtm.reverser_back", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping KEY_PANTOGRAPH = key("key.rtm.pantograph", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping KEY_DEST_PREV = key("key.rtm.destination_prev", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping KEY_DEST_NEXT = key("key.rtm.destination_next", GLFW.GLFW_KEY_UNKNOWN);

    // RTMU 追加 (本家に無い項目)
    public static final KeyMapping TOGGLE_CAB = key("toggle_cab", GLFW.GLFW_KEY_U);
    public static final KeyMapping TOGGLE_RENDER_PROFILER = key("toggle_render_profiler", GLFW.GLFW_KEY_F8);
    public static final KeyMapping FREE_CAMERA = key("free_camera", GLFW.GLFW_KEY_V);

    private TrainControlKeyMappings() {
    }

    public static void register(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(KEY_HORN);
        event.register(KEY_CHIME);
        event.register(KEY_ATS);
        event.register(KEY_EB);
        event.register(KEY_CHIME_NEXT);
        event.register(KEY_CHIME_PREV);
        event.register(KEY_REVERSER_FORWARD);
        event.register(KEY_REVERSER_BACK);
        event.register(KEY_PANTOGRAPH);
        event.register(KEY_DEST_PREV);
        event.register(KEY_DEST_NEXT);
        event.register(TOGGLE_CAB);
        event.register(TOGGLE_RENDER_PROFILER);
        event.register(FREE_CAMERA);
    }

    public static boolean matchesSneak(int key, int scanCode) {
        return net.minecraft.client.Minecraft.getInstance().options.keyShift.matches(key, scanCode);
    }

    public static boolean matchesInventory(net.minecraft.client.Minecraft mc, int key, int scanCode) {
        return mc.options.keyInventory.matches(key, scanCode);
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping(name, defaultKey, CATEGORY);
    }
}
