package com.eminus.beam;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import com.eminus.Eminus;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public final class BeamSet {
    private final Object lock = new Object();
    private final Object writing = new Object();
    private final Long2ObjectOpenHashMap<List<BeamSection>> beams = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
    private final BeamStore store;
    private final Executor writes;

    private long version;

    public BeamSet(BeamStore store, Executor writes) {
        this.store = store;
        this.writes = writes;
    }

    public void load(long pos, byte[] record) {
        try {
            List<BeamSection> sections = BeamRecord.decode(record);
            synchronized (lock) {
                beams.put(pos, sections);
                version++;
            }
        } catch (IllegalArgumentException damaged) {
            Eminus.LOGGER.error("Damaged beam record at {} skipped: {}", BlockPos.of(pos), damaged.getMessage());
        }
    }

    public void put(long pos, List<BeamSection> sections) {
        if (sections.isEmpty()) {
            remove(pos);
            return;
        }

        List<BeamSection> copy = List.copyOf(sections);
        synchronized (lock) {
            if (copy.equals(beams.get(pos))) {
                return;
            }

            beams.put(pos, copy);
            changed(pos);
        }
        writes.execute(this::writeOne);
    }

    public void remove(long pos) {
        synchronized (lock) {
            if (beams.remove(pos) == null) {
                return;
            }

            changed(pos);
        }
        writes.execute(this::writeOne);
    }

    public boolean holds(long pos) {
        synchronized (lock) {
            return beams.containsKey(pos);
        }
    }

    public LongList inChunk(int chunkX, int chunkZ) {
        LongList found = new LongArrayList();
        synchronized (lock) {
            for (long pos : beams.keySet()) {
                if (SectionPos.blockToSectionCoord(BlockPos.getX(pos)) == chunkX
                        && SectionPos.blockToSectionCoord(BlockPos.getZ(pos)) == chunkZ) {
                    found.add(pos);
                }
            }
        }

        return found;
    }

    public long version() {
        synchronized (lock) {
            return version;
        }
    }

    public BeamSnapshot snapshot() {
        synchronized (lock) {
            List<Beam> held = new ArrayList<>(beams.size());
            for (Long2ObjectMap.Entry<List<BeamSection>> entry : beams.long2ObjectEntrySet()) {
                held.add(new Beam(entry.getLongKey(), entry.getValue()));
            }
            return new BeamSnapshot(version, List.copyOf(held));
        }
    }

    public void flush() {
        boolean more = true;
        while (more) {
            more = writeOne();
        }
    }

    private void changed(long pos) {
        version++;
        dirty.add(pos);
    }

    private boolean writeOne() {
        synchronized (writing) {
            long pos;
            List<BeamSection> current;
            synchronized (lock) {
                if (dirty.isEmpty()) {
                    return false;
                }

                pos = dirty.removeFirstLong();
                current = beams.get(pos);
            }

            try {
                if (current == null) {
                    store.deleteBeam(pos);
                } else {
                    store.putBeam(pos, BeamRecord.encode(current));
                }
            } catch (RuntimeException failure) {
                Eminus.LOGGER.error("Could not write the beam at {} to the store.", BlockPos.of(pos), failure);
            }
            return true;
        }
    }
}
