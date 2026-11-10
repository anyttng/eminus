package com.eminus.render.far;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import com.eminus.render.arena.MeshSlot;
import com.eminus.render.backend.DepthConvention;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OcclusionTestTest {
    private static final int BLOCK = 16;
    private static final int PIXEL_WIDTH = 160;
    private static final int PIXEL_HEIGHT = 96;
    private static final int TEXEL_WIDTH = PIXEL_WIDTH / BLOCK;
    private static final int TEXEL_HEIGHT = PIXEL_HEIGHT / BLOCK;
    private static final float FOV = (float) Math.toRadians(70.0);
    private static final float NEAR = 0.5F;
    private static final float FAR = 1000.0F;
    private static final float WALL = 20.0F;
    private static final double EYE_X = 100.5;
    private static final double EYE_Y = 64.0;
    private static final double EYE_Z = -30.25;
    private static final float NEAR_FACE = 50.0F;
    private static final float FAR_FACE = 60.0F;
    private static final int OPEN_COLUMN = 5;
    private static final float HALF_PIXEL = 0.5F;
    private static final int GUARD_PIXELS = 1;

    private record Scene(DepthConvention depth, Matrix4f viewProjection) {
        static Scene of(boolean reversed) {
            DepthConvention depth = DepthConvention.of(true, reversed);
            float aspect = (float) PIXEL_WIDTH / PIXEL_HEIGHT;
            return new Scene(depth, reversed ? new Matrix4f().perspective(FOV, aspect, FAR, NEAR, true)
                    : new Matrix4f().perspective(FOV, aspect, NEAR, FAR, true));
        }

        float depthAt(float distance) {
            Vector4f point = viewProjection.transform(new Vector4f(0.0F, 0.0F, -distance, 1.0F));
            return point.z / point.w;
        }

        OcclusionTest test(float[] texels) {
            return test(texels, 0);
        }

        OcclusionTest test(float[] texels, int guardPixels) {
            ByteBuffer bytes = ByteBuffer.allocate(texels.length * Float.BYTES).order(ByteOrder.nativeOrder());
            for (float texel : texels) {
                bytes.putFloat(texel);
            }
            OcclusionTest test = new OcclusionTest(depth, BLOCK);
            test.load(bytes.flip(), TEXEL_WIDTH, TEXEL_HEIGHT, PIXEL_WIDTH, PIXEL_HEIGHT, guardPixels, viewProjection,
                    EYE_X, EYE_Y, EYE_Z, 0L);
            return test;
        }

        float[] wall(float distance) {
            float[] texels = new float[TEXEL_WIDTH * TEXEL_HEIGHT];
            Arrays.fill(texels, depthAt(distance));
            return texels;
        }

        float[] wallOpenAtColumn(int column) {
            float[] texels = wall(WALL);
            for (int row = 0; row < TEXEL_HEIGHT; row++) {
                texels[row * TEXEL_WIDTH + column] = (float) depth.farthest();
            }
            return texels;
        }

        float[] boxInsideColumn(int column, float insetPixels) {
            float scale = viewProjection.m00();
            float left = ndcX(column * BLOCK + insetPixels);
            float right = ndcX((column + 1) * BLOCK - insetPixels);
            return box(left * FAR_FACE / scale, -2.0, -FAR_FACE, right * NEAR_FACE / scale, 2.0, -NEAR_FACE);
        }

        private static float ndcX(float pixel) {
            return pixel / PIXEL_WIDTH * 2.0F - 1.0F;
        }
    }

    private static float[] box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        float[] bounds = new float[MeshSlot.BOUNDS];
        bounds[MeshSlot.MIN_X] = (float) (EYE_X + minX);
        bounds[MeshSlot.MIN_Y] = (float) (EYE_Y + minY);
        bounds[MeshSlot.MIN_Z] = (float) (EYE_Z + minZ);
        bounds[MeshSlot.MAX_X] = (float) (EYE_X + maxX);
        bounds[MeshSlot.MAX_Y] = (float) (EYE_Y + maxY);
        bounds[MeshSlot.MAX_Z] = (float) (EYE_Z + maxZ);
        return bounds;
    }

    private static final float[] BEHIND = box(-2.0, -2.0, -60.0, 2.0, 2.0, -50.0);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aWallInFrontHidesTheBox(boolean reversed) {
        Scene scene = Scene.of(reversed);

        assertTrue(scene.test(scene.wall(WALL)).hidden(BEHIND));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oneTexelOfSkyUnderTheBoxShowsIt(boolean reversed) {
        Scene scene = Scene.of(reversed);
        float[] texels = scene.wall(WALL);
        texels[(TEXEL_HEIGHT / 2) * TEXEL_WIDTH + TEXEL_WIDTH / 2] = (float) scene.depth().farthest();

        assertFalse(scene.test(texels).hidden(BEHIND));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBoxInFrontOfTheWallIsVisible(boolean reversed) {
        Scene scene = Scene.of(reversed);

        assertFalse(scene.test(scene.wall(WALL)).hidden(box(-1.0, -1.0, -10.0, 1.0, 1.0, -8.0)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBoxReachingBehindTheCameraIsVisible(boolean reversed) {
        Scene scene = Scene.of(reversed);

        assertFalse(scene.test(scene.wall(NEAR)).hidden(box(-1.0, -1.0, -60.0, 1.0, 1.0, 5.0)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBoxTouchingTheWallDepthIsVisible(boolean reversed) {
        Scene scene = Scene.of(reversed);
        float[] texels = scene.wall(50.0F);

        assertFalse(scene.test(texels).hidden(BEHIND));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aWallOneUlpBeforeTheBoxFaceLeavesItVisible(boolean reversed) {
        Scene scene = Scene.of(reversed);
        float face = scene.depthAt(50.0F);
        float[] texels = scene.wall(50.0F);
        Arrays.fill(texels, reversed ? Math.nextUp(face) : Math.nextDown(face));

        assertFalse(scene.test(texels).hidden(BEHIND));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theGuardKeepsABoxHalfAPixelFromAnOpenTexel(boolean reversed) {
        Scene scene = Scene.of(reversed);
        float[] texels = scene.wallOpenAtColumn(OPEN_COLUMN);
        float[] box = scene.boxInsideColumn(OPEN_COLUMN + 1, HALF_PIXEL);

        assertTrue(scene.test(texels).hidden(box));
        assertFalse(scene.test(texels, GUARD_PIXELS).hidden(box));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aTestHoldsOnlyNearTheEyeItWasTakenFrom(boolean reversed) {
        Scene scene = Scene.of(reversed);
        OcclusionTest test = scene.test(scene.wall(WALL));

        assertTrue(test.takenNear(EYE_X + 1.0, EYE_Y, EYE_Z, 2.0));
        assertFalse(test.takenNear(EYE_X, EYE_Y + 3.0, EYE_Z, 2.0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBoxReachingPastTheScreenEdgeIsVisibleBehindAFullWall(boolean reversed) {
        Scene scene = Scene.of(reversed);
        float[] edge = box(-200.0, -2.0, -60.0, -20.0, 2.0, -50.0);

        assertFalse(scene.test(scene.wall(WALL)).hidden(edge));
    }
}
