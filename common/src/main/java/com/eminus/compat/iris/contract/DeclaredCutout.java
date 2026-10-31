package com.eminus.compat.iris.contract;

import com.eminus.Eminus;
import com.eminus.compat.iris.PackContract;

import org.jspecify.annotations.Nullable;

public final class DeclaredCutout {
    public static boolean packCuts(@Nullable String declaration) {
        if (declaration == null) {
            return false;
        }

        String value = declaration.strip();
        if (value.equals(PackContract.PACK_CUTOUT)) {
            return true;
        }
        Eminus.LOGGER.warn("Shader pack's {} sets {} to '{}', which is not {}: Eminus cuts the pack's LOD fragments",
                PackContract.PROPERTIES_FILE, PackContract.CUTOUT_KEY, value, PackContract.PACK_CUTOUT);
        return false;
    }

    private DeclaredCutout() {
    }
}
