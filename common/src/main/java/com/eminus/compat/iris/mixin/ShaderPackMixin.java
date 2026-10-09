package com.eminus.compat.iris.mixin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.eminus.compat.iris.IrisShaderPack;
import com.eminus.compat.iris.PackContract;
import com.eminus.compat.iris.dh.DhPackFiles;
import com.eminus.compat.iris.dh.DistantHorizonsPack;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.sugar.Local;

import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;

@Mixin(ShaderPack.class)
public abstract class ShaderPackMixin implements DistantHorizonsPack {
    private static final String CONSTRUCTOR =
            "<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V";
    private static final String INCLUDE_GRAPH = "Lnet/irisshaders/iris/shaderpack/include/IncludeGraph;<init>("
            + "Ljava/nio/file/Path;Lcom/google/common/collect/ImmutableList;Z)V";
    private static final int STARTS = 1;
    private static final String DISTANT_HORIZONS_ID = "distanthorizons";
    private static final String DEFINED = "";

    @Shadow
    @Final
    private List<String> dimensionIds;

    @Unique
    private boolean eminus$distantHorizons;

    @ModifyVariable(method = CONSTRUCTOR, at = @At(value = "STORE", ordinal = 0), argsOnly = true)
    private ImmutableList<StringPair> eminus$defineDistantHorizons(ImmutableList<StringPair> defines,
            @Local(argsOnly = true) Path root) {
        eminus$distantHorizons = DhPackFiles.takesDhPath(root, IrisShaderPack.distantHorizonsChosen(),
                IrisPlatformHelpers.getInstance().isModLoaded(DISTANT_HORIZONS_ID));
        return !eminus$distantHorizons ? defines
                : ImmutableList.<StringPair>builder().addAll(defines).add(new StringPair(DhPackFiles.MACRO, DEFINED))
                        .build();
    }

    @Unique
    @Override
    public boolean eminus$distantHorizons() {
        return eminus$distantHorizons;
    }

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
