package com.eminus.compat.iris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PackBlendTest {
    private static final String GAME_MODE = "blend.gbuffers_water";
    private static final String OWN_MODE = "blend.eminus_translucent";
    private static final int FIRST_BUFFER = 0;
    private static final int SECOND_BUFFER = 13;
    private static final Buffer OWN_FIRST = new Buffer(FIRST_BUFFER, "own");
    private static final Buffer GAME_FIRST = new Buffer(FIRST_BUFFER, "game");
    private static final Buffer GAME_SECOND = new Buffer(SECOND_BUFFER, "game");

    private record Buffer(int index, String mode) {
    }

    @Test
    void gameKeyAloneGivesTheProgramTheGameBlend() {
        assertEquals(GAME_MODE, blend(null, List.of(), GAME_MODE, List.of()).mode());
    }

    @Test
    void contractKeyWinsOverTheGameKey() {
        assertEquals(OWN_MODE, blend(OWN_MODE, List.of(), GAME_MODE, List.of()).mode());
    }

    @Test
    void neitherKeyLeavesTheProgramWithoutAnOverride() {
        PackBlend<String, Buffer> blend = blend(null, List.of(), null, List.of());

        assertNull(blend.mode());
        assertEquals(List.of(), blend.buffers());
    }

    @Test
    void gameBufferKeysApplyWhereTheContractSetsNone() {
        assertEquals(List.of(GAME_FIRST, GAME_SECOND),
                blend(OWN_MODE, List.of(), GAME_MODE, List.of(GAME_FIRST, GAME_SECOND)).buffers());
    }

    @Test
    void contractBufferKeyWinsForItsBufferAndTheGameKeepsTheOthers() {
        assertEquals(List.of(OWN_FIRST, GAME_SECOND),
                blend(null, List.of(OWN_FIRST), GAME_MODE, List.of(GAME_FIRST, GAME_SECOND)).buffers());
    }

    private static PackBlend<String, Buffer> blend(@Nullable String ownMode, List<Buffer> ownBuffers,
            @Nullable String gameMode, List<Buffer> gameBuffers) {
        return PackBlend.of(ownMode, ownBuffers, gameMode, gameBuffers, Buffer::index);
    }
}
