package com.eminus.platform;

import java.nio.file.Path;

public interface Platform {
    boolean isClient();

    String modVersion();

    String loaderName();

    String loaderVersion();

    Path configDir();
}
