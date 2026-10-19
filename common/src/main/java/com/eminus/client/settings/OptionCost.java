package com.eminus.client.settings;

import org.jspecify.annotations.Nullable;

public enum OptionCost {
    LOD_QUALITY(Restart.LOD_QUALITY, null),
    LOD_DISTANCE(Restart.REBUILD, null),
    DETAIL_DISTANCE(Restart.REBUILD, null),
    SHADER_PACK_LOD(Restart.RELOAD_PACK, null),
    LOD_ANIMATIONS(Restart.RELOAD_PACK, SettingImpact.MEDIUM),
    VOXEL_SIZE(Restart.REREAD, SettingImpact.HIGH),
    TEXTURES(Restart.REBUILD, SettingImpact.LOW),
    LEAVES(Restart.REBUILD, SettingImpact.MEDIUM),
    GRASS_AND_FLOWERS(Restart.REBUILD, SettingImpact.LOW),
    CORNER_OCCLUSION(Restart.REBUILD, SettingImpact.MEDIUM);

    public enum Restart {
        REBUILD("rebuild"),
        REREAD("reread"),
        LOD_QUALITY("lod_quality"),
        RELOAD_PACK("reload_pack");

        private final String key;

        Restart(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private final Restart restart;
    private final @Nullable SettingImpact impact;

    OptionCost(Restart restart, @Nullable SettingImpact impact) {
        this.restart = restart;
        this.impact = impact;
    }

    public Restart restart() {
        return restart;
    }

    public @Nullable SettingImpact impact() {
        return impact;
    }
}
