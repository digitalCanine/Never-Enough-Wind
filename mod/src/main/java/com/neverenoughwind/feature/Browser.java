package com.neverenoughwind.feature;

import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.feature.browse.BrowserScreen;
import com.neverenoughwind.parse.Catalog;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.List;

// opens the item browser: a key (none until the player picks one) or /wind browse
public final class Browser {
    private static KeyBinding key;
    private static List<Catalog.Section> sections;
    // drafts are part of the list, so it is rebuilt when debug info is switched
    private static boolean builtWithDrafts;

    private Browser() {}

    public static void register() {
        key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.neverenoughwind.browser",
                InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "key.categories.neverenoughwind"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) {
                if (mc.currentScreen == null) open(null);
            }
        });
    }

    // query = null keeps whatever was searched last
    public static void open(String query) {
        if (NeverEnoughWind.data() == null) return;
        boolean drafts = Config.get().debug;
        if (sections == null || drafts != builtWithDrafts) {
            sections = Catalog.build(NeverEnoughWind.data(), drafts);
            builtWithDrafts = drafts;
        }
        MinecraftClient.getInstance().setScreen(new BrowserScreen(sections, query));
    }
}
