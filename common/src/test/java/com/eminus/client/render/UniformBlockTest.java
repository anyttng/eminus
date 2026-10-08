package com.eminus.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eminus.client.handoff.NearMaskPass;
import com.eminus.client.render.backend.BackendCheck;
import com.eminus.client.render.far.CompositePass;
import com.eminus.client.render.far.FarFrame;
import com.eminus.client.render.far.OcclusionPass;
import com.eminus.gpu.Std140;

import org.junit.jupiter.api.Test;

class UniformBlockTest {
    private static final String SHADERS = "/assets/eminus/shaders/";
    private static final String BLOCK_PATTERN = "layout\\(std140\\)\\s*uniform\\s+%s\\s*\\{([^}]*)\\}";
    private static final String MEMBER_END = ";";
    private static final String WHITESPACE = "\\s+";
    private static final String SPACE = " ";

    @Test
    void theFrameBlockMatchesItsInclude() throws IOException {
        assertMatches(FarFrame.BLOCK, "include/far_frame.glsl");
    }

    @Test
    void theCompositeBlockMatchesItsShader() throws IOException {
        assertMatches(CompositePass.BLOCK, "core/far_composite.fsh");
    }

    @Test
    void theOcclusionBlockMatchesItsShader() throws IOException {
        assertMatches(OcclusionPass.BLOCK, "core/far_occlusion.fsh");
    }

    @Test
    void theMaskBlockMatchesItsShader() throws IOException {
        assertMatches(NearMaskPass.BLOCK, "core/near_mask.fsh");
    }

    @Test
    void theProbeBlockMatchesItsShader() throws IOException {
        assertMatches(BackendCheck.BLOCK, "core/depth_probe.fsh");
    }

    @Test
    void aSwappedMemberIsCaught() throws IOException {
        String source = read("include/far_frame.glsl");
        String swapped = source.replace("float ShadeDown;", "float SWAP;")
                .replace("float ShadeUp;", "float ShadeDown;")
                .replace("float SWAP;", "float ShadeUp;");
        assertNotEquals(source, swapped);

        assertNotEquals(members(FarFrame.BLOCK), declared(swapped, FarFrame.BLOCK.name()));
    }

    private static void assertMatches(Std140.Block block, String shader) throws IOException {
        assertEquals(members(block), declared(read(shader), block.name()), block.name() + " in " + shader);
    }

    private static List<String> members(Std140.Block block) {
        return block.members().stream().map(member -> member.type().glsl() + SPACE + member.name()).toList();
    }

    private static List<String> declared(String source, String block) {
        Matcher matcher = Pattern.compile(String.format(BLOCK_PATTERN, block)).matcher(source);
        assertTrue(matcher.find(), "no std140 block " + block);
        return Arrays.stream(matcher.group(1).split(MEMBER_END))
                .map(String::strip)
                .filter(member -> !member.isEmpty())
                .map(member -> member.replaceAll(WHITESPACE, SPACE))
                .toList();
    }

    private static String read(String shader) throws IOException {
        try (InputStream stream = UniformBlockTest.class.getResourceAsStream(SHADERS + shader)) {
            assertNotNull(stream, shader);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
