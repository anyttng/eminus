package com.eminus.compat.iris.mixin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.eminus.compat.iris.PackContract;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.sugar.Local;

import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;

@Mixin(ShaderPack.class)
public abstract class ShaderPackMixin {
    private static final String CONSTRUCTOR =
            "<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V";
    private static final String INCLUDE_GRAPH = "Lnet/irisshaders/iris/shaderpack/include/IncludeGraph;<init>("
            + "Ljava/nio/file/Path;Lcom/google/common/collect/ImmutableList;Z)V";
    private static final int STARTS = 1;

    @Shadow
    @Final
    private List<String> dimensionIds;

    @ModifyArg(method = CONSTRUCTOR, at = @At(value = "INVOKE", target = INCLUDE_GRAPH), index = STARTS)
    private ImmutableList<AbsolutePackPath> eminus$addContractFiles(ImmutableList<AbsolutePackPath> starts,
            @Local(argsOnly = true) Path root) {
        ImmutableList.Builder<AbsolutePackPath> added = ImmutableList.<AbsolutePackPath>builder().addAll(starts);
        for (String path : PackContract.paths(dimensionIds)) {
            AbsolutePackPath file = AbsolutePackPath.fromAbsolutePath(path);
            if (Files.exists(file.resolved(root))) {
                added.add(file);
            }
        }
        return added.build();
    }
}
