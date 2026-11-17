package dev.parrots;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;

import static org.mockito.Mockito.*;

class PlaybackTest {
    @Test
    void cancelsFutureAssignedAfterTheStreamAlreadyStopped() {
        var playback = new PlasmoVoiceBridge.Playback(1L, null);
        Future<?> stream = mock(Future.class);
        playback.cancelStream();
        playback.setStreamTask(stream);
        verify(stream).cancel(false);
    }

    @Test
    void cancelsAnExistingStream() {
        var playback = new PlasmoVoiceBridge.Playback(1L, null);
        Future<?> stream = mock(Future.class);
        playback.setStreamTask(stream);
        verifyNoInteractions(stream);
        playback.cancelStream();
        verify(stream).cancel(false);
    }
}
