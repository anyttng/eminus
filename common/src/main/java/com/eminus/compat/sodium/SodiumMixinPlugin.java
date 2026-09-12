package com.eminus.compat.sodium;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class SodiumMixinPlugin implements IMixinConfigPlugin {
    private static final String SODIUM_UNIFORMS_CLASS =
            "net/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager.class";

    private boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        present = SodiumMixinPlugin.class.getClassLoader().getResource(SODIUM_UNIFORMS_CLASS) != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return present;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
