package com.eminus.compat.iris.mixin;

import java.util.List;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;

@Mixin(ShaderPack.class)
public interface ShaderPackAccessor {
    @Accessor("sourceProvider")
    Function<AbsolutePackPath, String> eminus$sourceProvider();

    @Accessor("shaderProperties")
    ShaderProperties eminus$shaderProperties();

    @Accessor("dimensionIds")
    List<String> eminus$dimensionIds();
}
