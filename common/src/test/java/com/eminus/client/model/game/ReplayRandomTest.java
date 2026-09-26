package com.eminus.client.model.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import org.junit.jupiter.api.Test;

class ReplayRandomTest {
    private static final long SEED = 1234L;
    private static final long OTHER_SEED = 99L;
    private static final int BOUND = 71;

    @Test
    void aPassAfterARestartSeesTheDrawsOfTheSeed() {
        ReplayRandom replay = new ReplayRandom(new LegacyRandomSource(SEED));
        RandomSource reference = new LegacyRandomSource(SEED);
        long firstLong = reference.nextLong();
        int boundedInt = reference.nextInt(BOUND);
        float nextFloat = reference.nextFloat();

        for (int pass = 0; pass < 3; pass++) {
            replay.restart();
            assertEquals(firstLong, replay.nextLong(), "pass " + pass);
            assertEquals(boundedInt, replay.nextInt(BOUND), "pass " + pass);
            assertEquals(nextFloat, replay.nextFloat(), "pass " + pass);
        }
    }

    @Test
    void aGaussianReplaysAsTheSeedDrawsIt() {
        ReplayRandom replay = new ReplayRandom(new LegacyRandomSource(SEED));
        double expected = new LegacyRandomSource(SEED).nextGaussian();

        assertEquals(expected, replay.nextGaussian());
        replay.restart();
        assertEquals(expected, replay.nextGaussian());
    }

    @Test
    void theSourceIsDrawnOnlyAsDeepAsTheDeepestPass() {
        LegacyRandomSource source = new LegacyRandomSource(SEED);
        ReplayRandom replay = new ReplayRandom(source);

        replay.nextLong();
        replay.restart();
        replay.nextLong();
        replay.nextLong();
        replay.restart();
        replay.nextInt();

        RandomSource reference = new LegacyRandomSource(SEED);
        reference.nextLong();
        reference.nextLong();
        assertEquals(reference.nextLong(), source.nextLong());
    }

    @Test
    void aSeedSetInsideAPassHoldsUntilTheNextRestart() {
        ReplayRandom replay = new ReplayRandom(new LegacyRandomSource(SEED));
        long replayed = new LegacyRandomSource(SEED).nextLong();

        replay.setSeed(OTHER_SEED);
        assertEquals(new LegacyRandomSource(OTHER_SEED).nextLong(), replay.nextLong());
        replay.restart();
        assertEquals(replayed, replay.nextLong());
    }
}
