package dev.parrots;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PhraseBufferTest {
    @Test
    void copiesPacketsAndHonoursLimit() {
        PhraseBuffer buffer = new PhraseBuffer(UUID.randomUUID(), 2);
        byte[] first = {1, 2, 3};

        buffer.add(first, true);
        first[0] = 99;
        buffer.add(new byte[]{4}, true);
        buffer.add(new byte[]{5}, true);

        assertEquals(2, buffer.packets().size());
        assertArrayEquals(new byte[]{1, 2, 3}, buffer.packets().getFirst());
        assertEquals(40L, buffer.durationMillis());
        assertTrue(buffer.stereo(), "The channel mode of the first packet defines the phrase");
    }

    @Test
    void ignoresEmptyPackets() {
        PhraseBuffer buffer = new PhraseBuffer(UUID.randomUUID(), 2);
        buffer.add(new byte[0], false);
        assertTrue(buffer.isEmpty());
    }

    @Test
    void rejectsMixedChannelModes() {
        PhraseBuffer buffer = new PhraseBuffer(UUID.randomUUID(), 4);
        buffer.add(new byte[]{1}, true);
        buffer.add(new byte[]{2}, false);
        assertEquals(1, buffer.packets().size());
        assertTrue(buffer.stereo());
    }

    @Test
    void usesMonotonicTimeAndIgnoresEmptyPacketActivity() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        PhraseBuffer buffer = new PhraseBuffer(UUID.randomUUID(), 2, clock::get);
        clock.set(200_000_000L);
        buffer.add(new byte[]{1}, false);
        clock.set(700_000_000L);
        buffer.add(new byte[0], false);

        assertEquals(700L, buffer.ageMillis());
        assertEquals(500L, buffer.idleMillis());
        assertThrows(UnsupportedOperationException.class, () -> buffer.packets().add(new byte[]{2}));
    }

    @Test
    void fullBufferKeepsPhraseOpenWhileValidSpeechContinues() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        PhraseBuffer buffer = new PhraseBuffer(UUID.randomUUID(), 1, clock::get);
        buffer.add(new byte[]{1}, false);
        clock.set(5_000_000_000L);
        buffer.add(new byte[]{2}, false);
        assertEquals(1, buffer.packets().size());
        assertEquals(5000L, buffer.ageMillis());
        assertEquals(0L, buffer.idleMillis());
    }
}
