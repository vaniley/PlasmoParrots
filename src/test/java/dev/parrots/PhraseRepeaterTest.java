package dev.parrots;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhraseRepeaterTest {
    @Test
    void exactChanceBoundariesRemainExact() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 100; i++) {
            assertEquals(0D, PhraseRepeater.individualChance(0D, 0, 4, random));
            assertEquals(1D, PhraseRepeater.individualChance(1D, 0, 4, random));
        }
    }
}
