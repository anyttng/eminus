package com.eminus.compat.iris;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class IrisMixinPlugin implements IMixinConfigPlugin {
    private static final String IRIS_PIPELINE_CLASS = "net/irisshaders/iris/pipeline/IrisRenderingPipeline.class";

    private boolean present;

    public static boolean irisPresent() {
        return IrisMixinPlugin.class.getClassLoader().getResource(IRIS_PIPELINE_CLASS) != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
        present = irisPresent();
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
