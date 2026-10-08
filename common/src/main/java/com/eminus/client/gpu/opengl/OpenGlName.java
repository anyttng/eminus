package com.eminus.client.gpu.opengl;

import java.util.function.IntConsumer;

final class OpenGlName {
    private final int id;
    private final IntConsumer delete;
    private boolean deleted;

    OpenGlName(int id, IntConsumer delete) {
        this.id = id;
        this.delete = delete;
    }

    int id() {
        return id;
    }

    void delete() {
        if (deleted) {
            return;
        }

        deleted = true;
        delete.accept(id);
    }
}
