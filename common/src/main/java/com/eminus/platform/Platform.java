package com.eminus.platform;

public interface Platform {
    boolean isClient();

    String modVersion();

    String loaderName();

    String loaderVersion();
}
