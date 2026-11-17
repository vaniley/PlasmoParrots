package dev.parrots;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhraseRepeaterTest {
    @Test
    void exactChanceBoundariesRemainExact() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 100; i++) {
            assertEquals(0D, PhraseRepeater.individualChance(0D, 0, 4, random));
            assertEquals(1D, PhraseRepeater.individualChance(1D, 0, 4, random));
        }
    }

    @Test
    void searchesReleaseTheCaptureLockAndRecoverFromQueryFailures() {
        PlasmoParrotsPlugin plugin = mock(PlasmoParrotsPlugin.class);
        when(plugin.settings()).thenReturn(PluginSettings.load(new YamlConfiguration()));
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        PlasmoVoiceBridge bridge = mock(PlasmoVoiceBridge.class);
        when(bridge.isCurrentGeneration(1L)).thenReturn(true);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));
        PhraseBuffer phrase = new PhraseBuffer(id, 2);
        phrase.add(new byte[]{1}, false);
        PhraseRepeater repeater = new PhraseRepeater(plugin);
        repeater.setVoiceBridge(bridge);
        var attempts = new java.util.concurrent.atomic.AtomicInteger();

        try (var searches = mockStatic(ParrotSearch.class)) {
            searches.when(() -> ParrotSearch.find(eq(plugin), any(), anyDouble(), anyInt(), any()))
                    .thenAnswer(invocation -> {
                        assertFalse(Thread.holdsLock(bridge));
                        if (attempts.getAndIncrement() == 0) throw new IllegalStateException("test query failure");
                        return null;
                    });
            repeater.maybeRepeat(phrase, player, 1L);
            repeater.maybeRepeat(phrase, player, 1L);
            searches.verify(() -> ParrotSearch.find(eq(plugin), any(), anyDouble(), anyInt(), any()), times(2));
        }
    }
}
