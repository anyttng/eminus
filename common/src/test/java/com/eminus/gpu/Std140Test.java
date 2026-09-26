package com.eminus.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class Std140Test {
    private static final Matrix4f MATRIX = new Matrix4f().perspective(1.2F, 1.5F, 0.1F, 900.0F).rotateY(0.7F);
    private static final Vector4f VECTOR = new Vector4f(0.25F, 0.5F, 0.75F, 1.0F);

    @Test
    void theFarFrameLayoutMatchesTheGame() {
        int size = Std140.size().putMat4f().putInt().putInt().putInt().putInt().putIVec3()
                .putFloat().putFloat().putFloat().putFloat().putFloat().putFloat().putIVec3().putVec3().get();
        assertEquals(new Std140SizeCalculator().putMat4f().putInt().putInt().putInt().putInt().putIVec3()
                .putFloat().putFloat().putFloat().putFloat().putFloat().putFloat().putIVec3().putVec3().get(), size);

        ByteBuffer ours = Std140.into(direct(size)).putMat4f(MATRIX).putInt(-64).putInt(8).putInt(33).putInt(24)
                .putIVec3(-512, -64, 256).putFloat(0.5F).putFloat(1.0F).putFloat(0.8F).putFloat(0.8F)
                .putFloat(0.6F).putFloat(0.6F).putIVec3(-1, 70, 12).putVec3(-0.75F, -0.5F, -0.25F).get();
        ByteBuffer game = Std140Builder.intoBuffer(direct(size)).putMat4f(MATRIX).putInt(-64).putInt(8).putInt(33)
                .putInt(24).putIVec3(-512, -64, 256).putFloat(0.5F).putFloat(1.0F).putFloat(0.8F).putFloat(0.8F)
                .putFloat(0.6F).putFloat(0.6F).putIVec3(-1, 70, 12).putVec3(-0.75F, -0.5F, -0.25F).get();
        assertEquals(game, ours);
    }

    @Test
    void theCompositeLayoutMatchesTheGame() {
        int size = Std140.size().putMat4f().putMat4f().putVec4().putFloat().putFloat().putFloat()
                .putFloat().putFloat().putFloat().putFloat().putFloat().get();
        assertEquals(new Std140SizeCalculator().putMat4f().putMat4f().putVec4().putFloat().putFloat().putFloat()
                .putFloat().putFloat().putFloat().putFloat().putFloat().get(), size);

        ByteBuffer ours = Std140.into(direct(size)).putMat4f(MATRIX).putMat4f(MATRIX).putVec4(VECTOR)
                .putFloat(1.0F).putFloat(2.0F).putFloat(3.0F).putFloat(4.0F).putFloat(5.0F).putFloat(6.0F)
                .putFloat(7.0F).putFloat(8.0F).get();
        ByteBuffer game = Std140Builder.intoBuffer(direct(size)).putMat4f(MATRIX).putMat4f(MATRIX).putVec4(VECTOR)
                .putFloat(1.0F).putFloat(2.0F).putFloat(3.0F).putFloat(4.0F).putFloat(5.0F).putFloat(6.0F)
                .putFloat(7.0F).putFloat(8.0F).get();
        assertEquals(game, ours);
    }

    @Test
    void theOcclusionLayoutMatchesTheGame() {
        int size = Std140.size().putMat4f().putMat4f().putMat4f().putFloat().get();
        assertEquals(new Std140SizeCalculator().putMat4f().putMat4f().putMat4f().putFloat().get(), size);

        ByteBuffer ours = Std140.into(direct(size)).putMat4f(MATRIX).putMat4f(MATRIX).putMat4f(MATRIX)
                .putFloat(540.0F).get();
        ByteBuffer game = Std140Builder.intoBuffer(direct(size)).putMat4f(MATRIX).putMat4f(MATRIX).putMat4f(MATRIX)
                .putFloat(540.0F).get();
        assertEquals(game, ours);
    }

    private static ByteBuffer direct(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }
}
