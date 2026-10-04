package com.neverenoughwind.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {
    @Test
    void defaultsWithoutAFile(@TempDir Path dir) {
        Config.load(dir);
        assertEquals(Config.PanelMode.ALWAYS, Config.get().sidePanel);
        assertEquals(8, Config.get().borderColors.size());
        assertFalse(Files.exists(dir.resolve("config.json")));
    }

    @Test
    void takesOverTheOldRelationsFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("relations.json"),
                "{\"own\":[\"hoes\",\"axe\"],\"enemy\":[\"srmp\"],\"tradebanned\":[\"done\"],\"colors\":{\"ally\":\"#112233\"}}");
        Config.load(dir);
        assertEquals(List.of("hoes", "axe"), Config.get().own);
        assertEquals(List.of("done"), Config.get().tradebanned);
        assertEquals(0x112233, Config.get().allyColor);
        assertTrue(Files.exists(dir.resolve("config.json")));
    }

    @Test
    void savesAndReadsBack(@TempDir Path dir) {
        Config.load(dir);
        Config.get().sidePanel = Config.PanelMode.SHIFT;
        Config.Widget w = Config.get().widget("party");
        w.moved = true;
        w.anchorX = 1f;
        w.offsetX = -12;
        w.scale = 1.5f;
        Config.save();
        Config.load(dir);
        assertEquals(Config.PanelMode.SHIFT, Config.get().sidePanel);
        assertEquals(-12, Config.get().widget("party").offsetX);
        assertEquals(1.5f, Config.get().widget("party").scale);
    }

    @Test
    void aBrokenFileFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.json"), "{ not json");
        Config.load(dir);
        assertTrue(Config.get().borders);
        // and an old file that misses fields still gets every border color
        Files.writeString(dir.resolve("config.json"), "{\"borderColors\":{\"keys\":255}}");
        Config.load(dir);
        assertEquals(255, Config.get().borderColors.get("keys"));
        assertEquals(8, Config.get().borderColors.size());
    }
}
