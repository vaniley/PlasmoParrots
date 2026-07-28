package dev.parrots;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PluginSettingsTest {
    @Test
    void preservesDocumentedBoundaryValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("repeat-chance", 0D);
        config.set("pitch-factor", 1D);
        config.set("max-concurrent-replays", 3);

        PluginSettings settings = PluginSettings.load(config);

        assertEquals(0D, settings.repeatChance());
        assertEquals(1D, settings.pitchFactor());
        assertEquals(3, settings.maxConcurrentReplays());
    }

    @Test
    void clampsInvalidRangesAndNonFiniteNumbers() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("repeat-chance", Double.NaN);
        config.set("parrot-volume", 5D);
        config.set("parrots-min", 4);
        config.set("parrots-max", 1);
        config.set("effects", java.util.List.of(Map.of(
                "name", "test",
                "weight", 1,
                "pitch-min", 1.5,
                "pitch-max", 1.0
        )));

        PluginSettings settings = PluginSettings.load(config);

        assertEquals(0.52D, settings.repeatChance());
        assertEquals(1D, settings.parrotVolume());
        assertEquals(4, settings.parrotsMin());
        assertEquals(4, settings.parrotsMax());
        assertEquals(1.5D, settings.effects().getFirst().pitchMax());
    }
}
