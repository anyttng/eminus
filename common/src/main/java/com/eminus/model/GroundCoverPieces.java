package com.eminus.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.eminus.model.port.ModelQuad;

import org.joml.Vector3fc;

public final class GroundCoverPieces {
    private static final float STEPS = 1024.0F;

    private record Axis(int x, int z) {
    }

    public static List<List<ModelQuad>> split(List<ModelQuad> quads) {
        Map<Integer, List<ModelQuad>> planes = new TreeMap<>(Comparator.reverseOrder());
        Map<Axis, List<ModelQuad>> stems = new LinkedHashMap<>();

        for (ModelQuad quad : quads) {
            Vector3fc normal = FaceRasterizer.normal(quad);
            if (FaceRasterizer.horizontal(normal)) {
                planes.computeIfAbsent(step(quad.corner(0).y()), height -> new ArrayList<>()).add(quad);
            } else if (FaceRasterizer.blade(normal)) {
                stems.computeIfAbsent(axis(quad), at -> new ArrayList<>()).add(quad);
            } else {
                return List.of(quads);
            }
        }

        List<List<ModelQuad>> pieces = new ArrayList<>(planes.values());
        pieces.addAll(stems.values());
        return pieces.size() <= 1 ? List.of(quads) : pieces;
    }

    private static Axis axis(ModelQuad quad) {
        float x = 0.0F;
        float z = 0.0F;
        for (int corner = 0; corner < ModelQuad.CORNERS; corner++) {
            x += quad.corner(corner).x();
            z += quad.corner(corner).z();
        }

        return new Axis(step(x / ModelQuad.CORNERS), step(z / ModelQuad.CORNERS));
    }

    private static int step(float coordinate) {
        return Math.round(coordinate * STEPS);
    }

    private GroundCoverPieces() {
    }
}
