package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.eminus.compat.iris.PackContract;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.helpers.StringPair;

@Mixin(StandardMacros.class)
public class StandardMacrosMixin {
    private static final String DEFINED = "";

    @ModifyReturnValue(method = "createStandardEnvironmentDefines", at = @At("RETURN"))
    private static ImmutableList<StringPair> eminus$defineContract(ImmutableList<StringPair> defines) {
        return ImmutableList.<StringPair>builder()
                .addAll(defines)
                .add(new StringPair(PackContract.MACRO, DEFINED))
                .add(new StringPair(PackContract.VERSION_MACRO, Integer.toString(PackContract.VERSION)))
                .build();
    }
}
