package com.eminus.gpu;

import java.nio.ByteBuffer;

import org.joml.Matrix4fc;
import org.joml.Vector4fc;

public final class Std140 {
    private static final int SCALAR = 4;
    private static final int VECTOR = 16;
    private static final int IVEC3_BYTES = 12;
    private static final int MAT4_BYTES = 64;

    private final ByteBuffer buffer;
    private final int start;

    private Std140(ByteBuffer buffer) {
        this.buffer = buffer;
        this.start = buffer.position();
    }

    public static Std140 into(ByteBuffer buffer) {
        return new Std140(buffer);
    }

    public static Size size() {
        return new Size();
    }

    public ByteBuffer get() {
        return buffer.flip();
    }

    public Std140 putFloat(float value) {
        align(SCALAR);
        buffer.putFloat(value);
        return this;
    }

    public Std140 putInt(int value) {
        align(SCALAR);
        buffer.putInt(value);
        return this;
    }

    public Std140 putIVec3(int x, int y, int z) {
        align(VECTOR);
        buffer.putInt(x).putInt(y).putInt(z);
        return this;
    }

    public Std140 putVec4(Vector4fc value) {
        align(VECTOR);
        value.get(buffer);
        buffer.position(buffer.position() + VECTOR);
        return this;
    }

    public Std140 putMat4f(Matrix4fc value) {
        align(VECTOR);
        value.get(buffer);
        buffer.position(buffer.position() + MAT4_BYTES);
        return this;
    }

    private void align(int alignment) {
        buffer.position(start + aligned(buffer.position() - start, alignment));
    }

    private static int aligned(int offset, int alignment) {
        return (offset + alignment - 1) & -alignment;
    }

    public static final class Size {
        private int bytes;

        private Size() {
        }

        public int get() {
            return bytes;
        }

        public Size putFloat() {
            return put(SCALAR, SCALAR);
        }

        public Size putInt() {
            return put(SCALAR, SCALAR);
        }

        public Size putIVec3() {
            return put(VECTOR, IVEC3_BYTES);
        }

        public Size putVec4() {
            return put(VECTOR, VECTOR);
        }

        public Size putMat4f() {
            return put(VECTOR, MAT4_BYTES);
        }

        private Size put(int alignment, int size) {
            bytes = aligned(bytes, alignment) + size;
            return this;
        }
    }
}
