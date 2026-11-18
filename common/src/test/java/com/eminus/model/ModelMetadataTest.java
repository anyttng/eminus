package com.eminus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.eminus.cell.FaceMask;

class ModelMetadataTest {
    private static final int NO_EMISSION = 0;
    private static final int NO_FLAGS = 0;

    @Test
    void aFaceThatHidesItsNeighborOverlapsAndAnotherDoesNot() {
        int word = ModelMetadata.pack(FaceMask.ALL, FaceMask.SOUTH | FaceMask.UP, FaceMask.ALL, NO_EMISSION,
                ModelMetadata.TINTED);

        assertEquals(FaceMask.SOUTH | FaceMask.UP, ModelMetadata.overlapping(word));
    }

    @Test
    void anOpaqueModelOverlapsOnEveryFace() {
        int word = ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.ALL, NO_EMISSION, ModelMetadata.OPAQUE);

        assertEquals(FaceMask.ALL, ModelMetadata.overlapping(word));
    }

    @Test
    void aTranslucentModelOverlapsOnNoFace() {
        int occluding = ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, NO_EMISSION,
                ModelMetadata.TRANSLUCENT);
        int opaque = ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.ALL, NO_EMISSION,
                ModelMetadata.TRANSLUCENT | ModelMetadata.OPAQUE);

        assertEquals(FaceMask.NONE, ModelMetadata.overlapping(occluding));
        assertEquals(FaceMask.NONE, ModelMetadata.overlapping(opaque));
    }

    @Test
    void aModelThatHidesNoNeighborOverlapsOnNoFace() {
        int word = ModelMetadata.pack(FaceMask.ALL, FaceMask.NONE, FaceMask.ALL, NO_EMISSION, NO_FLAGS);

        assertEquals(FaceMask.NONE, ModelMetadata.overlapping(word));
    }
}
