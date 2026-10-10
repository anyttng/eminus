package com.eminus.box;

import java.nio.ByteBuffer;
import java.util.List;

import com.eminus.api.v1.FarBox;

public final class BoxRecords {
    public static final int TEXELS = 4;
    public static final int BYTES = TEXELS * 4 * Integer.BYTES;
    public static final int EMISSIVE = 1;
    public static final int VERTICES = 36;

    private static final int UNUSED = 0;

    public static long bytes(int boxes) {
        return (long) boxes * BYTES;
    }

    public static void write(List<FarBox> boxes, ByteBuffer target) {
        for (FarBox box : boxes) {
            target.putInt(block(box.minX())).putInt(block(box.minY())).putInt(block(box.minZ())).putInt(box.argb())
                    .putInt(block(box.maxX())).putInt(block(box.maxY())).putInt(block(box.maxZ()))
                    .putInt(box.emissive() ? EMISSIVE : 0)
                    .putFloat(fraction(box.minX())).putFloat(fraction(box.minY())).putFloat(fraction(box.minZ()))
                    .putInt(UNUSED)
                    .putFloat(fraction(box.maxX())).putFloat(fraction(box.maxY())).putFloat(fraction(box.maxZ()))
                    .putInt(UNUSED);
        }
    }

    public static int block(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    public static float fraction(double coordinate) {
        return (float) (coordinate - Math.floor(coordinate));
    }

    private BoxRecords() {
    }
}
