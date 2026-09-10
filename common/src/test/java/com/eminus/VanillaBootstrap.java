package com.eminus;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class VanillaBootstrap {
    private static boolean done;

    public static synchronized void ensure() {
        if (done) {
            return;
        }

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        done = true;
    }

    private VanillaBootstrap() {
    }
}
