package com.eminus.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.eminus.cell.FaceMask;
import com.eminus.model.BakedModel;
import com.eminus.model.ModelMetadata;

import org.junit.jupiter.api.Test;

class ModelRecordsTest {
    private static final int HALF_VALUES = 1 << ModelRecords.METADATA_HALF_BITS;
    private static final int[] FLAGS = {ModelMetadata.TINTED, ModelMetadata.TRANSLUCENT, ModelMetadata.BLADED,
            ModelMetadata.SLOPED, ModelMetadata.FLUID, ModelMetadata.ONE_SIDED, ModelMetadata.INWARD,
            ModelMetadata.OPAQUE, ModelMetadata.MOVED, ModelMetadata.SMOOTH};

    @Test
    void everyLowHalfComesBack() {
        for (int low = 0; low < HALF_VALUES; low++) {
            assertEquals(low, rebuilt(low));
        }
    }

    @Test
    void everyHighHalfComesBack() {
        for (int high = 0; high < HALF_VALUES; high++) {
            int word = high << ModelRecords.METADATA_HALF_BITS;

            assertEquals(word, rebuilt(word));
        }
    }

    @Test
    void everyFlagComesBackOverFullMasks() {
        for (int flag : FLAGS) {
            int word = ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, ModelMetadata.MAX_EMISSION, flag);

            assertEquals(word, rebuilt(word));
        }
        int every = ModelMetadata.pack(FaceMask.ALL, FaceMask.ALL, FaceMask.ALL, ModelMetadata.MAX_EMISSION,
                ModelMetadata.FLAGS);

        assertEquals(every, rebuilt(every));
    }

    @Test
    void neitherHalfIsWrittenAsBits() {
        ByteBuffer scratch = encoded(ModelMetadata.SMOOTH | 1);

        assertEquals(1.0F, scratch.getFloat(ModelRecords.METADATA_LOW_FLOAT * Float.BYTES));
        assertEquals(32768.0F, scratch.getFloat(ModelRecords.METADATA_HIGH_FLOAT * Float.BYTES));
    }

    private static int rebuilt(int word) {
        ByteBuffer scratch = encoded(word);
        int low = (int) scratch.getFloat(ModelRecords.METADATA_LOW_FLOAT * Float.BYTES);
        int high = (int) scratch.getFloat(ModelRecords.METADATA_HIGH_FLOAT * Float.BYTES);

        return low | high << ModelRecords.METADATA_HALF_BITS;
    }

    private static ByteBuffer encoded(int word) {
        ByteBuffer scratch = ByteBuffer.allocate(ModelRecords.BYTES).order(ByteOrder.nativeOrder());
        ModelRecords.encode(scratch, BakedModel.empty().withMetadata(word), 0);

        return scratch;
    }
}
