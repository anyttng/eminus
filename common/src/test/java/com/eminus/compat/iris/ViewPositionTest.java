package com.eminus.compat.iris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewPositionTest {
    private static final String DIRECTIVE = "#";
    private static final String PACK_CODE = "uniform sampler2D depthtex0;";
    private static final String CALLING = """
            #version 330 compatibility
            #extension GL_ARB_shading_language_packing : enable

            uniform sampler2D depthtex0;

            void main() {
                vec4 position = eminus_viewPosition(vec2(0.5), false);
            }
            """;
    private static final String NOT_CALLING = """
            #version 330 compatibility

            uniform sampler2D depthtex0;

            void main() {
            }
            """;

    @Test
    void aSourceThatCallsTheHelperCarriesItAfterItsDirectivesAndBeforeItsOwnCode() {
        String spliced = ViewPosition.splice(CALLING);
        int helper = spliced.indexOf(ViewPosition.SOURCE);

        assertTrue(helper > spliced.indexOf("#extension"), spliced);
        assertTrue(helper < spliced.indexOf(PACK_CODE), spliced);
        assertEquals(spliced.indexOf(PackContract.VIEW_POSITION + "(vec2 texcoord"),
                spliced.lastIndexOf(PackContract.VIEW_POSITION + "(vec2 texcoord"), spliced);
    }

    @Test
    void aSourceThatDoesNotNameTheHelperIsReturnedUnchanged() {
        assertSame(NOT_CALLING, ViewPosition.splice(NOT_CALLING));
    }

    @Test
    void aSourceWithoutAVersionIsLeftForIrisToRefuse() {
        String noVersion = "void main() { eminus_viewPosition(vec2(0.5), true); }\n";

        assertSame(noVersion, ViewPosition.splice(noVersion));
    }

    @Test
    void theHelperCarriesNoPreprocessorDirective() {
        ViewPosition.SOURCE.lines().forEach(line -> assertFalse(line.strip().startsWith(DIRECTIVE), line));
    }

    @Test
    void theHelperReadsOnlyItsPrivateNames() {
        for (String packName : PackContract.UNIFORMS) {
            assertFalse(ViewPosition.SOURCE.contains(packName), packName);
        }
        assertFalse(ViewPosition.SOURCE.contains(PackContract.DEPTH_SAMPLER), ViewPosition.SOURCE);
        assertFalse(ViewPosition.SOURCE.contains(PackContract.NEAR_DEPTH), ViewPosition.SOURCE);
        assertFalse(ViewPosition.SOURCE.contains(PackContract.NEAR_OPAQUE_DEPTH), ViewPosition.SOURCE);
    }
}
