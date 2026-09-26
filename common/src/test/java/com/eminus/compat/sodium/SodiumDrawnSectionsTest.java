package com.eminus.compat.sodium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.eminus.compat.sodium.SodiumDrawnSections.Reach;

class SodiumDrawnSectionsTest {
    private static final int CAMERA_Y = 70;
    private static final float HALF = 0.5F;
    private static final float EIGHT_CHUNKS = 128.0F;
    private static final float JUST_UNDER_127 = 126.8F;
    private static final boolean BUILT = true;
    private static final Reach EIGHT_CHUNK_REACH = new Reach(0, CAMERA_Y, 0, HALF, HALF, HALF, EIGHT_CHUNKS);

    @BeforeEach
    void clear() {
        SodiumDrawnSections.reset();
    }

    @Test
    void sectionCrossingTheSearchDistanceIsInside() {
        assertTrue(EIGHT_CHUNK_REACH.covers(8, 4, 0));
        assertTrue(EIGHT_CHUNK_REACH.covers(-9, 4, 0));
    }

    @Test
    void sectionPastTheSearchDistanceIsOutside() {
        assertFalse(EIGHT_CHUNK_REACH.covers(9, 4, 0));
        assertFalse(EIGHT_CHUNK_REACH.covers(-10, 4, 0));
    }

    @Test
    void horizontalReachIsACircle() {
        assertTrue(EIGHT_CHUNK_REACH.covers(5, 4, 5));
        assertFalse(EIGHT_CHUNK_REACH.covers(6, 4, 6));
    }

    @Test
    void verticalReachIsTheDistanceEitherWay() {
        assertTrue(EIGHT_CHUNK_REACH.covers(0, 12, 0));
        assertFalse(EIGHT_CHUNK_REACH.covers(0, 13, 0));
    }

    @Test
    void cameraFractionMovesTheEdge() {
        assertTrue(new Reach(0, CAMERA_Y, 0, HALF, HALF, HALF, JUST_UNDER_127).covers(8, 4, 0));
        assertFalse(new Reach(0, CAMERA_Y, 0, 0.1F, HALF, HALF, JUST_UNDER_127).covers(8, 4, 0));
    }

    @Test
    void visitedSectionStaysDrawnAcrossTraversalsThatSkipIt() {
        SodiumDrawnSections.visited(2, 4, 0, BUILT);
        publish(EIGHT_CHUNKS);
        publish(EIGHT_CHUNKS);

        assertTrue(SodiumDrawnSections.drawn(2, 4, 0));
    }

    @Test
    void sectionVisitedBeforeItWasBuiltIsNotDrawn() {
        SodiumDrawnSections.visited(2, 4, 0, !BUILT);
        publish(EIGHT_CHUNKS);

        assertFalse(SodiumDrawnSections.drawn(2, 4, 0));
    }

    @Test
    void neverVisitedSectionIsNotDrawn() {
        publish(EIGHT_CHUNKS);

        assertFalse(SodiumDrawnSections.drawn(2, 4, 0));
    }

    @Test
    void removedSectionIsNotDrawnUntilVisitedAgain() {
        SodiumDrawnSections.visited(2, 4, 0, BUILT);
        SodiumDrawnSections.removed(2, 4, 0);
        publish(EIGHT_CHUNKS);

        assertFalse(SodiumDrawnSections.drawn(2, 4, 0));
    }

    @Test
    void visitedSectionPastThePublishedReachIsNotDrawn() {
        SodiumDrawnSections.visited(9, 4, 0, BUILT);
        publish(EIGHT_CHUNKS);

        assertFalse(SodiumDrawnSections.drawn(9, 4, 0));
    }

    @Test
    void reachCountsOnlyOncePublished() {
        SodiumDrawnSections.visited(2, 4, 0, BUILT);
        SodiumDrawnSections.traversal(0, CAMERA_Y, 0, HALF, HALF, HALF, EIGHT_CHUNKS);

        assertFalse(SodiumDrawnSections.drawn(2, 4, 0));
    }

    private static void publish(float searchDistance) {
        SodiumDrawnSections.traversal(0, CAMERA_Y, 0, HALF, HALF, HALF, searchDistance);
        SodiumDrawnSections.publish();
    }
}
