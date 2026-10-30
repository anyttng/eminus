package com.eminus.model;

import java.util.ArrayList;
import java.util.List;

import com.eminus.model.port.ModelQuad;

import net.minecraft.core.Direction;

import org.joml.Vector3f;
import org.joml.Vector3fc;

final class FlowerBedQuads {
    static final float SIXTEENTH = 1.0F / 16.0F;
    static final float TOP_PETALS = 2.99F * SIXTEENTH;
    static final float MIDDLE_PETALS = 2.0F * SIXTEENTH;
    static final float LOW_PETALS = SIXTEENTH;
    static final float FIRST_STEM_X = 0.25F;
    static final float FIRST_STEM_Z = 0.3F;
    static final float SECOND_STEM_X = 0.7F;
    static final float SECOND_STEM_Z = 0.6F;

    private static final float HALF_STEM = 0.5F * SIXTEENTH;
    private static final float[] FACE_UV = {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};

    static List<ModelQuad> bed() {
        List<ModelQuad> bed = new ArrayList<>();
        bed.addAll(petals(LOW_PETALS));
        bed.addAll(petals(TOP_PETALS));
        bed.addAll(petals(MIDDLE_PETALS));
        bed.addAll(stem(FIRST_STEM_X, FIRST_STEM_Z));
        bed.addAll(stem(SECOND_STEM_X, SECOND_STEM_Z));
        return bed;
    }

    static List<ModelQuad> petals(float height) {
        return List.of(
                quad(new float[] {0, height, 0, 0, height, 1, 1, height, 1, 1, height, 0}),
                quad(new float[] {0, height, 0, 1, height, 0, 1, height, 1, 0, height, 1}));
    }

    static List<ModelQuad> stem(float x, float z) {
        return List.of(
                quad(new float[] {x - HALF_STEM, 0, z - HALF_STEM, x + HALF_STEM, 0, z + HALF_STEM,
                        x + HALF_STEM, TOP_PETALS, z + HALF_STEM, x - HALF_STEM, TOP_PETALS, z - HALF_STEM}),
                quad(new float[] {x - HALF_STEM, 0, z + HALF_STEM, x + HALF_STEM, 0, z - HALF_STEM,
                        x + HALF_STEM, TOP_PETALS, z - HALF_STEM, x - HALF_STEM, TOP_PETALS, z + HALF_STEM}));
    }

    static ModelQuad quad(float[] positions) {
        return new ModelQuad(new Vector3fc[] {
            new Vector3f(positions[0], positions[1], positions[2]),
            new Vector3f(positions[3], positions[4], positions[5]),
            new Vector3f(positions[6], positions[7], positions[8]),
            new Vector3f(positions[9], positions[10], positions[11])},
                FACE_UV.clone(), null, ModelQuad.NO_TINT, false, 0, Direction.UP, Direction.UP, false);
    }

    private FlowerBedQuads() {
    }
}
