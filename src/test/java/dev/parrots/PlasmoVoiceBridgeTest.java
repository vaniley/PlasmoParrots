package dev.parrots;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Parrot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;

import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlasmoVoiceBridgeTest {
    @Test
    void pendingReplaysConsumeCapacityAndDisableInvalidatesQueuedCallbacks() throws Exception {
        Fixture fixture = new Fixture();
        assertTrue(fixture.play());
        assertFalse(fixture.play(), "A delayed replay must reserve its slot before source creation");
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler).execute(eq(fixture.plugin), callback.capture(), any(), anyLong());

        fixture.bridge.disable();
        callback.getValue().run();

        verify(fixture.line, never()).createEntitySource(any(), anyBoolean(), any());
        assertFalse(fixture.bridge.isReady());
    }

    @Test
    void retiredEntityReleasesReplayReservation() throws Exception {
        Fixture fixture = new Fixture();
        assertTrue(fixture.play());
        ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler).execute(eq(fixture.plugin), any(), retired.capture(), anyLong());

        retired.getValue().run();
        retired.getValue().run(); // Retirement and disable can both attempt cleanup.
        assertTrue(fixture.play());
        fixture.bridge.disable();
    }

    @Test
    void rejectedEntityTaskDoesNotLeakCapacity() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.scheduler.execute(any(), any(), any(), anyLong())).thenReturn(false);
        assertFalse(fixture.play());
        when(fixture.scheduler.execute(any(), any(), any(), anyLong())).thenReturn(true);
        assertTrue(fixture.play());
        fixture.bridge.disable();
    }

    private static final class Fixture {
        final PlasmoParrotsPlugin plugin = mock(PlasmoParrotsPlugin.class);
        final Parrot parrot = mock(Parrot.class);
        final EntityScheduler scheduler = mock(EntityScheduler.class);
        final ServerSourceLine line = mock(ServerSourceLine.class);
        final PlasmoVoiceBridge bridge;

        Fixture() throws Exception {
            YamlConfiguration config = new YamlConfiguration();
            config.set("max-concurrent-replays", 1);
            when(plugin.settings()).thenReturn(PluginSettings.load(config));
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            when(parrot.getScheduler()).thenReturn(scheduler);
            when(scheduler.execute(any(), any(), any(), anyLong())).thenReturn(true);
            bridge = new PlasmoVoiceBridge(plugin, new PhraseRepeater(plugin));
            set("voiceServer", mock(PlasmoVoiceServer.class, RETURNS_DEEP_STUBS));
            set("sourceLine", line);
            set("registered", true);
        }

        boolean play() {
            return bridge.playParrotRepeat(parrot, List.of(new byte[]{1}), false, 1.2,
                    new ReplayEffect("test", 1, 1.1, 1.2, 0, 0, 0, 0), 500L);
        }

        void set(String name, Object value) throws Exception {
            Field field = PlasmoVoiceBridge.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(bridge, value);
        }
    }
}
