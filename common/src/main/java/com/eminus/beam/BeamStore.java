package com.eminus.beam;

public interface BeamStore {
    void putBeam(long pos, byte[] sections);

    void deleteBeam(long pos);

    void readBeams(Rows into);

    @FunctionalInterface
    interface Rows {
        void row(long pos, byte[] sections);
    }
}
