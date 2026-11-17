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

    @Test
    void boundsResourceUseAndAvoidsRandomRangeOverflow() {
        YamlConfiguration config = new YamlConfiguration();
        for (String key : java.util.List.of("max-phrase-millis", "phrase-idle-millis",
                "player-cooldown-millis", "repeat-duration-min-millis", "repeat-duration-max-millis",
                "parrot-stagger-min-millis", "parrot-stagger-max-millis")) config.set(key, Long.MAX_VALUE);
        for (String key : java.util.List.of("parrots-min", "parrots-max", "max-buffered-packets",
                "max-concurrent-replays")) config.set(key, Integer.MAX_VALUE);
        config.set("parrot-radius", Double.MAX_VALUE);

        PluginSettings settings = PluginSettings.load(config);

        assertEquals(60_000L, settings.maxPhraseMillis());
        assertEquals(60_000L, settings.phraseIdleMillis());
        assertEquals(3_600_000L, settings.playerCooldownMillis());
        assertEquals(60_000L, settings.repeatDurationMinMillis());
        assertEquals(60_000L, settings.repeatDurationMaxMillis());
        assertEquals(60_000L, settings.parrotStaggerMinMillis());
        assertEquals(60_000L, settings.parrotStaggerMaxMillis());
        assertEquals(64, settings.parrotsMin());
        assertEquals(64, settings.parrotsMax());
        assertEquals(3000, settings.maxBufferedPackets());
        assertEquals(64, settings.maxConcurrentReplays());
        assertEquals(64D, settings.parrotRadius());
    }
}
