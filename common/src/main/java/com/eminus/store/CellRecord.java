package com.eminus.store;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.eminus.cell.Cell;
import com.eminus.cell.DetailLevel;

import io.airlift.compress.zstd.ZstdCompressor;
import io.airlift.compress.zstd.ZstdDecompressor;

public final class CellRecord {
    private static final int VERSION_BYTES = Byte.BYTES;
    private static final int LENGTH_BYTES = Integer.BYTES;
    private static final int HEADER_BYTES = VERSION_BYTES + LENGTH_BYTES;

    private static final int INDEX_BYTES = DetailLevel.VOXELS_PER_CELL * Short.BYTES;
    private static final int PALETTE_SIZE_BYTES = Integer.BYTES;
    private static final int MIN_PLAIN_BYTES = PALETTE_SIZE_BYTES + Long.BYTES + INDEX_BYTES;
    private static final int MAX_PLAIN_BYTES = PALETTE_SIZE_BYTES + Cell.MAX_PALETTE * Long.BYTES + INDEX_BYTES;

    public static byte[] encode(Cell cell) {
        long[] palette = cell.copyPalette();
        short[] indices = cell.indices();

        int plainLength = PALETTE_SIZE_BYTES + palette.length * Long.BYTES + INDEX_BYTES;
        ByteBuffer plain = ByteBuffer.allocate(plainLength);
        plain.putInt(palette.length);
        for (long entry : palette) {
            plain.putLong(entry);
        }
        plain.asShortBuffer().put(indices);

        ZstdCompressor compressor = new ZstdCompressor();
        byte[] record = new byte[HEADER_BYTES + compressor.maxCompressedLength(plainLength)];
        int packedLength = compressor.compress(
                plain.array(), 0, plainLength, record, HEADER_BYTES, record.length - HEADER_BYTES);

        ByteBuffer header = ByteBuffer.wrap(record);
        header.put((byte) StoreFormat.VERSION);
        header.putInt(plainLength);
        return Arrays.copyOf(record, HEADER_BYTES + packedLength);
    }

    public static Cell decode(long key, byte[] record) {
        if (record.length <= HEADER_BYTES) {
            throw new DamagedRecordException("record of " + record.length + " bytes carries no body");
        }

        ByteBuffer header = ByteBuffer.wrap(record);
        int version = header.get();
        if (version != StoreFormat.VERSION) {
            throw new DamagedRecordException("record format version " + version + ", expected " + StoreFormat.VERSION);
        }

        int plainLength = header.getInt();
        if (plainLength < MIN_PLAIN_BYTES || plainLength > MAX_PLAIN_BYTES) {
            throw new DamagedRecordException("record claims " + plainLength + " plain bytes");
        }

        byte[] plainBytes = new byte[plainLength];
        new ZstdDecompressor().decompress(record, HEADER_BYTES, record.length - HEADER_BYTES, plainBytes, 0, plainLength);

        ByteBuffer plain = ByteBuffer.wrap(plainBytes);
        int paletteSize = plain.getInt();
        if (paletteSize < 1 || paletteSize > Cell.MAX_PALETTE
                || plainLength != PALETTE_SIZE_BYTES + paletteSize * Long.BYTES + INDEX_BYTES) {
            throw new DamagedRecordException("record claims a palette of " + paletteSize + " entries");
        }

        long[] palette = new long[paletteSize];
        for (int entry = 0; entry < paletteSize; entry++) {
            palette[entry] = plain.getLong();
        }

        short[] indices = new short[DetailLevel.VOXELS_PER_CELL];
        plain.asShortBuffer().get(indices);
        return Cell.of(key, palette, indices);
    }

    private CellRecord() {
    }
}
