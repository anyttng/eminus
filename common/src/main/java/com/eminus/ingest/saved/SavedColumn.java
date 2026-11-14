package com.eminus.ingest.saved;

import java.util.Objects;

import com.eminus.ingest.BiomeWindow;
import com.eminus.ingest.ColumnLight;
import com.eminus.ingest.IngestColumn;
import com.eminus.ingest.SectionConverter;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.jspecify.annotations.Nullable;

public final class SavedColumn implements IngestColumn {
    public static final int NEIGHBOR_REACH = 1;
    public static final int NEIGHBOR_SIDE = 2 * NEIGHBOR_REACH + 1;

    private static final String SUBMITTED = "submits answers false for a section the save lacks";

    private final SavedChunk center;
    private final @Nullable SavedChunk[] around;
    private final boolean hasSky;
    private final long biomeZoomSeed;

    public SavedColumn(SavedChunk center, @Nullable SavedChunk[] around, boolean hasSky, long biomeZoomSeed) {
        this.center = center;
        this.around = around;
        this.hasSky = hasSky;
        this.biomeZoomSeed = biomeZoomSeed;
    }

    public enum Verdict {
        ACCEPTED,
        NOT_SAVED,
        NOT_FULL,
        UNLIT,
        NO_NEIGHBORS
    }

    public static int neighborIndex(int dx, int dz) {
        return (dz + NEIGHBOR_REACH) * NEIGHBOR_SIDE + dx + NEIGHBOR_REACH;
    }

    public static Verdict judge(@Nullable SavedChunk[] around) {
        SavedChunk center = around[neighborIndex(0, 0)];
        if (center == null) {
            return Verdict.NOT_SAVED;
        }

        if (!center.full()) {
            return Verdict.NOT_FULL;
        }

        if (!center.lit()) {
            return Verdict.UNLIT;
        }

        for (SavedChunk neighbor : around) {
            if (neighbor == null || !neighbor.hasBiomes()) {
                return Verdict.NO_NEIGHBORS;
            }
        }

        return Verdict.ACCEPTED;
    }

    @Override
    public int chunkX() {
        return center.chunkX();
    }

    @Override
    public int chunkZ() {
        return center.chunkZ();
    }

    @Override
    public int sectionCount() {
        return center.sectionCount();
    }

    @Override
    public int sectionY(int index) {
        return center.minSectionY() + index;
    }

    @Override
    public boolean submits(int index) {
        LevelChunkSection section = center.section(index);
        if (section == null) {
            return false;
        }

        return !section.hasOnlyAir() || SectionConverter.lightDiffersFromBlank(sky(index), blockLight(index));
    }

    @Override
    public LevelChunkSection section(int index) {
        return Objects.requireNonNull(center.section(index), SUBMITTED);
    }

    @Override
    @SuppressWarnings("NullAway")
    public BiomeWindow biomeWindow(int index) {
        return BiomeWindow.capture(this::noiseBiome, chunkX(), sectionY(index), chunkZ(), biomeZoomSeed);
    }

    @Override
    public DataLayer skyLight(int index) {
        return Objects.requireNonNullElseGet(sky(index), () -> ColumnLight.openSky(hasSky));
    }

    @Override
    public @Nullable DataLayer blockLight(int index) {
        return center.layer(LightLayer.BLOCK, sectionY(index));
    }

    private @Nullable DataLayer sky(int index) {
        return ColumnLight.sky(this::engineLayer, hasSky, sectionY(index), center.maxLightSection());
    }

    private @Nullable DataLayer engineLayer(LightLayer layer, int sectionY) {
        DataLayer stored = center.layer(layer, sectionY);
        if (stored != null || layer != LightLayer.SKY || !hasSky || !engineHeldLayer(sectionY)) {
            return stored;
        }

        return new DataLayer();
    }

    private boolean engineHeldLayer(int sectionY) {
        for (SavedChunk chunk : around) {
            for (int dy = -NEIGHBOR_REACH; dy <= NEIGHBOR_REACH; dy++) {
                if (chunk != null && chunk.holdsBlocks(sectionY + dy)) {
                    return true;
                }
            }
        }

        return false;
    }

    private @Nullable Holder<Biome> noiseBiome(int quartX, int quartY, int quartZ) {
        SavedChunk neighbor = around[neighborIndex(QuartPos.toSection(quartX) - chunkX(),
                QuartPos.toSection(quartZ) - chunkZ())];
        return neighbor == null ? null : neighbor.noiseBiome(quartX, quartY, quartZ);
    }
}
