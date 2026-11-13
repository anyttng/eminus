package com.eminus.compat.iris;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IrisAlphaTestRuleTest {
    @Test
    void compatibilityProgramWritingTheFirstFragDataIsTested() {
        assertTrue(IrisAlphaTestRule.applies("#version 120\nvoid main() {\n    gl_FragData[0] = vec4(1.0);\n}\n"));
        assertTrue(IrisAlphaTestRule.applies("#version 400 compatibility\nvoid main() { gl_FragData[ 0 ] = vec4(1.0); }"));
    }

    @Test
    void fragColorCountsAsTheFirstFragData() {
        assertTrue(IrisAlphaTestRule.applies("#version 130\nvoid main() { gl_FragColor = vec4(1.0); }"));
    }

    @Test
    void programWithItsOwnOutputIsNotTested() {
        assertFalse(IrisAlphaTestRule.applies("#version 400 compatibility\nlayout(location = 0) out vec3 shadowcolor0_out;\n"
                + "void main() { shadowcolor0_out = vec3(1.0); }"));
    }

    @Test
    void programWritingOnlyALaterFragDataIsNotTested() {
        assertFalse(IrisAlphaTestRule.applies("#version 120\nvoid main() { gl_FragData[1] = vec4(1.0); }"));
    }

    @Test
    void coreProgramIsNotTested() {
        assertFalse(IrisAlphaTestRule.applies("#version 330 core\nvoid main() { gl_FragData[0] = vec4(1.0); }"));
        assertFalse(IrisAlphaTestRule.applies("#version 330\nvoid main() { gl_FragData[0] = vec4(1.0); }"));
    }

    @Test
    void sourceWithoutAVersionIsNotTested() {
        assertFalse(IrisAlphaTestRule.applies("void main() { gl_FragData[0] = vec4(1.0); }"));
    }
}
