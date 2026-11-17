package dev.parrots;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Parrot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.ServerActivation;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerEntitySource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlasmoVoiceBridgeTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createsMatchingChannelSourceAndCancelsPublishedTasksOnDisable(boolean stereo) throws Exception {
        Fixture fixture = new Fixture();
        ServerEntitySource source = mock(ServerEntitySource.class);
        when(fixture.parrot.isValid()).thenReturn(true);
        when(fixture.line.createEntitySource(any(), anyBoolean(), any())).thenReturn(source);
        assertTrue(fixture.play(stereo));
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler).execute(eq(fixture.plugin), callback.capture(), any(), anyLong());
        callback.getValue().run();
        verify(fixture.line).createEntitySource(any(), eq(stereo), any());

        @SuppressWarnings("unchecked")
        Set<PlasmoVoiceBridge.Playback> active = (Set<PlasmoVoiceBridge.Playback>) fixture.get("activePlaybacks");
        var playback = active.iterator().next();
        Future<?> stream = mock(Future.class);
        Future<?> cleanup = mock(Future.class);
        playback.setStreamTask(stream);
        playback.setCleanupTask(cleanup);

        fixture.bridge.disable();
        fixture.bridge.disable();

        verify(stream).cancel(false);
        verify(cleanup).cancel(false);
        verify(fixture.line).removeSource(source);
        assertTrue(active.isEmpty());
    }

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

    @Test
    void reloadInvalidatesOldCallbacksAndAllowsNewReservations() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.server.getActivationManager().getActivationByName(anyString()))
                .thenReturn(Optional.of(mock(ServerActivation.class)));
        assertTrue(fixture.play());
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler).execute(eq(fixture.plugin), callback.capture(), any(), anyLong());

        fixture.bridge.reload();
        callback.getValue().run();

        assertTrue(fixture.bridge.isReady());
        ServerSourceLine reloadedLine = (ServerSourceLine) fixture.get("sourceLine");
        when(reloadedLine.getSources()).thenReturn(List.of());
        verify(fixture.line, never()).createEntitySource(any(), anyBoolean(), any());
        assertTrue(fixture.play());
        fixture.bridge.disable();
    }

    private static final class Fixture {
        final PlasmoParrotsPlugin plugin = mock(PlasmoParrotsPlugin.class);
        final Parrot parrot = mock(Parrot.class);
        final EntityScheduler scheduler = mock(EntityScheduler.class);
        final ServerSourceLine line = mock(ServerSourceLine.class);
        final PlasmoVoiceServer server = mock(PlasmoVoiceServer.class, RETURNS_DEEP_STUBS);
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
            set("voiceServer", server);
            set("sourceLine", line);
            set("registered", true);
        }

        boolean play() {
            return play(false);
        }

        boolean play(boolean stereo) {
            return bridge.playParrotRepeat(parrot, List.of(new byte[]{1}), stereo, 1.2,
                    new ReplayEffect("test", 1, 1.1, 1.2, 0, 0, 0, 0), 500L);
        }

        void set(String name, Object value) throws Exception {
            Field field = PlasmoVoiceBridge.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(bridge, value);
        }

        Object get(String name) throws Exception {
            Field field = PlasmoVoiceBridge.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(bridge);
        }
    }
}
