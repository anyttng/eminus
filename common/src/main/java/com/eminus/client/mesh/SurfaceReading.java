package com.eminus.client.mesh;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.eminus.cell.CellFrame;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;
import com.eminus.client.model.ClientBakery;
import com.eminus.client.session.ClientSession;
import com.eminus.mesh.CellMesh;
import com.eminus.mesh.FluidCorners;
import com.eminus.mesh.Quad;
import com.eminus.mesh.QuadOffset;
import com.eminus.model.BakedModel;
import com.eminus.model.ModelBakery;
import com.eminus.model.ModelMetadata;
import com.eminus.session.DimensionRuntime;
import com.eminus.session.EminusInstance;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

public final class SurfaceReading {
    public static final float NO_SURFACE = Float.NaN;

    private static final int TIMEOUT_SECONDS = 300;
    private static final int UP = Direction.UP.get3DDataValue();

    public static @Nullable CompletableFuture<float[][]> start(int minX, int minZ, int side, int minY, int maxY) {
        DimensionRuntime runtime = ClientSession.runtime();
        EminusInstance instance = ClientSession.instance();
        if (runtime == null || instance == null) {
            return null;
        }

        CellFrame frame = runtime.frame();
        long[] keys = keys(frame, minX, minZ, side, minY, maxY);
        ClientBakery baking = ClientBakery.start(Minecraft.getInstance());

        return CellMeshing.mesh(runtime, instance, baking, keys, TIMEOUT_SECONDS)
                .thenApply(meshes -> tops(frame, baking.bakery(), meshes, minX, minZ, side))
                .whenComplete((tops, failure) -> baking.stop());
    }

    private static long[] keys(CellFrame frame, int minX, int minZ, int side, int minY, int maxY) {
        int maxX = minX + side - 1;
        int maxZ = minZ + side - 1;
        LongArrayList keys = new LongArrayList();
        for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
            for (int cellX = frame.cellX(minX, level); cellX <= frame.cellX(maxX, level); cellX++) {
                for (int cellY = frame.cellY(minY, level); cellY <= frame.cellY(maxY, level); cellY++) {
                    for (int cellZ = frame.cellZ(minZ, level); cellZ <= frame.cellZ(maxZ, level); cellZ++) {
                        keys.add(CellKey.pack(level, cellX, cellY, cellZ));
                    }
                }
            }
        }

        return keys.toLongArray();
    }

    private static float[][] tops(CellFrame frame, ModelBakery bakery, Map<Long, CellMesh> meshes, int minX, int minZ,
            int side) {
        float[][] tops = new float[DetailLevel.COUNT][side * side];
        for (float[] level : tops) {
            Arrays.fill(level, NO_SURFACE);
        }

        for (CellMesh mesh : meshes.values()) {
            long key = mesh.key();
            int level = CellKey.level(key);
            int voxelBlocks = DetailLevel.blocksPerVoxel(level);

            for (int index = 0; index < mesh.quadCount(); index++) {
                long quad = mesh.quad(index);
                if (Quad.face(quad) != UP) {
                    continue;
                }

                float top = frame.blockYOf(key, Quad.y(quad)) + upFaceHeight(mesh, quad, bakery, level);
                int fromX = frame.blockXOf(key, Quad.x(quad));
                int fromZ = frame.blockZOf(key, Quad.z(quad));
                int toX = fromX + Quad.width(quad) * voxelBlocks;
                int toZ = fromZ + Quad.height(quad) * voxelBlocks;

                for (int x = Math.max(fromX, minX); x < Math.min(toX, minX + side); x++) {
                    for (int z = Math.max(fromZ, minZ); z < Math.min(toZ, minZ + side); z++) {
                        int column = (x - minX) * side + (z - minZ);
                        float known = tops[level][column];
                        if (Float.isNaN(known) || top > known) {
                            tops[level][column] = top;
                        }
                    }
                }
            }
        }

        return tops;
    }

    private static float upFaceHeight(CellMesh mesh, long quad, ModelBakery bakery, int level) {
        BakedModel model = bakery.model(Quad.modelId(quad));
        float[] bounds = model.bounds();
        int placement = mesh.offset(quad);
        boolean coarse = level != DetailLevel.MIN;
        int voxelBlocks = DetailLevel.blocksPerVoxel(level);
        float below = voxelBlocks - (coarse ? VoxelEntry.highGapOf(placement) : 0);

        if (ModelMetadata.has(model.metadata(), ModelMetadata.FLUID)) {
            return below - 1.0F + fluidTop(coarse ? FluidCorners.FLAT : placement, bounds[BakedModel.MAX_Y]);
        }

        float widthFrom = bounds[BakedModel.MIN_X];
        float widthTo = Quad.width(quad) - 1 + bounds[BakedModel.MAX_X];
        float heightFrom = bounds[BakedModel.MIN_Z];
        float heightTo = Quad.height(quad) - 1 + bounds[BakedModel.MAX_Z];
        float alongWidth = model.slopeAlongWidth(UP);
        float alongHeight = model.slopeAlongHeight(UP);
        float inset = model.insets()[UP]
                + Math.min(alongWidth * widthFrom, alongWidth * widthTo)
                + Math.min(alongHeight * heightFrom, alongHeight * heightTo);

        return below - inset + (coarse ? 0.0F : QuadOffset.y(placement));
    }

    private static float fluidTop(int corners, float flatHeight) {
        if (corners == FluidCorners.FLAT) {
            return Math.round(flatHeight * FluidCorners.STEPS) / (float) FluidCorners.STEPS;
        }

        int highest = Math.max(Math.max(FluidCorners.northWest(corners), FluidCorners.northEast(corners)),
                Math.max(FluidCorners.southWest(corners), FluidCorners.southEast(corners)));
        return highest / (float) FluidCorners.STEPS;
    }

    private SurfaceReading() {
    }
}
