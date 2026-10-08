package com.eminus.gpu;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4fc;
import org.joml.Vector4fc;

public final class Std140 {
    private static final int SCALAR = 4;
    private static final int VECTOR = 16;
    private static final int VEC3_BYTES = 12;
    private static final int MAT4_BYTES = 64;
    private static final int MAX_MEMBERS = Long.SIZE;

    private Std140() {
    }

    public static Block block(String name) {
        return new Block(name);
    }

    private static int aligned(int offset, int alignment) {
        return (offset + alignment - 1) & -alignment;
    }

    public enum Type {
        FLOAT("float", SCALAR, SCALAR),
        INT("int", SCALAR, SCALAR),
        IVEC3("ivec3", VECTOR, VEC3_BYTES),
        VEC3("vec3", VECTOR, VEC3_BYTES),
        VEC4("vec4", VECTOR, VECTOR),
        MAT4("mat4", VECTOR, MAT4_BYTES);

        private final String glsl;
        private final int alignment;
        private final int bytes;

        Type(String glsl, int alignment, int bytes) {
            this.glsl = glsl;
            this.alignment = alignment;
            this.bytes = bytes;
        }

        public String glsl() {
            return glsl;
        }
    }

    public static final class Member {
        private final Block block;
        private final int index;
        private final String name;
        private final Type type;
        private final int offset;

        private Member(Block block, int index, String name, Type type, int offset) {
            this.block = block;
            this.index = index;
            this.name = name;
            this.type = type;
            this.offset = offset;
        }

        public String name() {
            return name;
        }

        public Type type() {
            return type;
        }

        public int offset() {
            return offset;
        }
    }

    public static final class Block {
        private final String name;
        private final List<Member> members = new ArrayList<>();
        private int size;
        private boolean sealed;

        private Block(String name) {
            this.name = name;
        }

        public Member add(Type type, String memberName) {
            if (sealed) {
                throw new IllegalStateException("Uniform block " + name + " was sized before " + memberName
                        + " was added");
            }
            if (members.size() == MAX_MEMBERS) {
                throw new IllegalStateException("Uniform block " + name + " holds more than " + MAX_MEMBERS
                        + " members");
            }

            int offset = aligned(size, type.alignment);
            Member member = new Member(this, members.size(), memberName, type, offset);
            members.add(member);
            size = offset + type.bytes;
            return member;
        }

        public String name() {
            return name;
        }

        public List<Member> members() {
            return List.copyOf(members);
        }

        public int size() {
            sealed = true;
            return size;
        }

        public Writer into(ByteBuffer buffer) {
            return new Writer(this, buffer);
        }

        private long allWritten() {
            return members.size() == MAX_MEMBERS ? -1L : (1L << members.size()) - 1;
        }
    }

    public static final class Writer {
        private final Block block;
        private final ByteBuffer buffer;
        private final int start;
        private long written;

        private Writer(Block block, ByteBuffer buffer) {
            this.block = block;
            this.buffer = buffer;
            this.start = buffer.position();
        }

        public Writer putFloat(Member member, float value) {
            buffer.putFloat(at(member, Type.FLOAT), value);
            return this;
        }

        public Writer putInt(Member member, int value) {
            buffer.putInt(at(member, Type.INT), value);
            return this;
        }

        public Writer putIVec3(Member member, int x, int y, int z) {
            int at = at(member, Type.IVEC3);
            buffer.putInt(at, x).putInt(at + SCALAR, y).putInt(at + 2 * SCALAR, z);
            return this;
        }

        public Writer putVec3(Member member, float x, float y, float z) {
            int at = at(member, Type.VEC3);
            buffer.putFloat(at, x).putFloat(at + SCALAR, y).putFloat(at + 2 * SCALAR, z);
            return this;
        }

        public Writer putVec4(Member member, Vector4fc value) {
            value.get(at(member, Type.VEC4), buffer);
            return this;
        }

        public Writer putMat4(Member member, Matrix4fc value) {
            value.get(at(member, Type.MAT4), buffer);
            return this;
        }

        public ByteBuffer get() {
            if (written != block.allWritten()) {
                throw new IllegalStateException("Uniform block " + block.name + " was handed over with a member"
                        + " left unwritten");
            }
            return buffer.limit(start + block.size()).position(start);
        }

        private int at(Member member, Type type) {
            if (member.block != block || member.type != type) {
                throw new IllegalArgumentException(member.name + " is not a " + type.glsl + " of uniform block "
                        + block.name);
            }
            written |= 1L << member.index;
            return start + member.offset;
        }
    }
}
