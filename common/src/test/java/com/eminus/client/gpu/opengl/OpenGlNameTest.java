package com.eminus.client.gpu.opengl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class OpenGlNameTest {
    private static final int ID = 7;

    @Test
    void aSecondDeleteDeletesNothing() {
        List<Integer> deleted = new ArrayList<>();
        OpenGlName name = new OpenGlName(ID, deleted::add);

        name.delete();
        name.delete();

        assertEquals(List.of(ID), deleted);
    }
}
