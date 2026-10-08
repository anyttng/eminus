package com.eminus.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import com.eminus.client.render.far.FarFrame;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class Std140Test {
    private static final Matrix4f MATRIX = new Matrix4f().perspective(1.2F, 1.5F, 0.1F, 900.0F).rotateY(0.7F);
    private static final Vector4f VECTOR = new Vector4f(0.25F, 0.5F, 0.75F, 1.0F);

    @Test
    void theFarFrameOffsetsAreTheShaders() {
        assertEquals(List.of(0, 64, 128, 132, 136, 140, 144, 156, 160, 164, 168, 172, 176, 192, 208),
                FarFrame.BLOCK.members().stream().map(Std140.Member::offset).toList());
        assertEquals(220, FarFrame.BLOCK.size());
    }

    @Test
    void eachMemberIsWrittenAtItsOffset() {
        Std140.Block block = Std140.block("Written");
        Std140.Member matrix = block.add(Std140.Type.MAT4, "Matrix");
        Std140.Member count = block.add(Std140.Type.INT, "Count");
        Std140.Member origin = block.add(Std140.Type.IVEC3, "Origin");
        Std140.Member scale = block.add(Std140.Type.FLOAT, "Scale");
        Std140.Member colour = block.add(Std140.Type.VEC4, "Colour");
        Std140.Member offset = block.add(Std140.Type.VEC3, "Offset");

        ByteBuffer written = block.into(direct(block.size()))
                .putVec3(offset, -0.75F, -0.5F, -0.25F)
                .putVec4(colour, VECTOR)
                .putFloat(scale, 0.5F)
                .putIVec3(origin, -512, -64, 256)
                .putInt(count, 33)
                .putMat4(matrix, MATRIX)
                .get();

        assertEquals(0, written.position());
        assertEquals(124, written.limit());
        assertEquals(MATRIX, new Matrix4f().set(0, written));
        assertEquals(33, written.getInt(64));
        assertEquals(-512, written.getInt(80));
        assertEquals(256, written.getInt(88));
        assertEquals(0.5F, written.getFloat(92));
        assertEquals(VECTOR, new Vector4f().set(96, written));
        assertEquals(-0.25F, written.getFloat(120));
    }

    @Test
    void aMemberOfAnotherBlockIsRefused() {
        Std140.Block block = Std140.block("Own");
        block.add(Std140.Type.FLOAT, "Value");
        Std140.Member foreign = Std140.block("Other").add(Std140.Type.FLOAT, "Value");

        assertThrows(IllegalArgumentException.class, () -> block.into(direct(block.size())).putFloat(foreign, 1.0F));
    }

    @Test
    void aMemberLeftUnwrittenIsRefused() {
        Std140.Block block = Std140.block("Partial");
        Std140.Member first = block.add(Std140.Type.FLOAT, "First");
        block.add(Std140.Type.FLOAT, "Second");

        assertThrows(IllegalStateException.class, () -> block.into(direct(block.size())).putFloat(first, 1.0F).get());
    }

    @Test
    void aMemberAddedAfterSizingIsRefused() {
        Std140.Block block = Std140.block("Sized");
        block.add(Std140.Type.FLOAT, "First");
        block.size();

        assertThrows(IllegalStateException.class, () -> block.add(Std140.Type.FLOAT, "Late"));
    }

    private static ByteBuffer direct(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }
}
