package com.eminus.compat.iris.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeclaredCutoutTest {
    private static final int LEAVES = 10005;

    @Test
    void packCutsWhereTheFileSaysSo() {
        assertTrue(ContractDeclarations.of("cutout = pack\n").packCuts());
    }

    @Test
    void theValueIsReadPastSurroundingSpace() {
        assertTrue(ContractDeclarations.of("cutout =   pack  \n").packCuts());
    }

    @Test
    void aFileWithoutTheKeyLeavesTheCutToEminus() {
        assertFalse(ContractDeclarations.of("moved = 10001\n").packCuts());
    }

    @Test
    void anotherValueLeavesTheCutToEminus() {
        assertFalse(ContractDeclarations.of("cutout = 0.1\n").packCuts());
    }

    @Test
    void bothKeysAreReadFromOneFile() {
        ContractDeclarations declared = ContractDeclarations.of("moved = 10005\ncutout = pack\n");
        assertTrue(declared.packCuts());
        assertTrue(declared.moved().contains(LEAVES));
    }
}
