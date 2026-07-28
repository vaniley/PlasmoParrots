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
        buffer.add(new byte[]{4}, false);
        buffer.add(new byte[]{5}, false);

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
}
