package com.neverenoughwind.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

// the settings button in Mod Menu. Mod Menu is optional, this is only loaded when it is there
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LayoutScreen::settings;
    }
}
