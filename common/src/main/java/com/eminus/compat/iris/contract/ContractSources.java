package com.eminus.compat.iris.contract;

import java.util.List;

import com.eminus.client.render.far.FarDraw;
import com.eminus.compat.iris.PackContract;
import com.eminus.compat.iris.PackSources;

import org.jspecify.annotations.Nullable;

final class ContractSources {
    static final String MAIN = "void main() {\n    " + PackContract.FUNCTION + "(eminus_fragment());\n}\n";

    private static final String NO_MAIN = "";

    private ContractSources() {
    }

    static List<String> candidates(String file, @Nullable String folder) {
        return folder == null || folder.isEmpty() ? List.of(PackContract.ROOT + file)
                : List.of(PackContract.ROOT + folder + PackContract.ROOT + file, PackContract.ROOT + file);
    }

    static String splice(String packSource, String header) {
        return PackSources.insert(packSource, header, MAIN);
    }

    static String spliceVertex(String packSource, String header) {
        return PackSources.insert(packSource, header, NO_MAIN);
    }

    static FarDraw.PackVertex shadowStage(boolean vertex, boolean shadowVertex) {
        if (vertex) {
            return FarDraw.PackVertex.HOOK;
        }
        return shadowVertex ? FarDraw.PackVertex.SHADOW_HOOK : FarDraw.PackVertex.OURS;
    }
}
