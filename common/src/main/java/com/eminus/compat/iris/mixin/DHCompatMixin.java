package com.eminus.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.eminus.compat.iris.IrisFarState;
import com.eminus.compat.iris.IrisShaderPack;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.joml.Matrix4f;

@Mixin(targets = "net.irisshaders.iris.compat.dh.DHCompat")
public class DHCompatMixin {
    @ModifyReturnValue(method = "getRenderDistance()I", at = @At("RETURN"))
    private static int eminus$reachFarLayer(int chunks) {
        return IrisShaderPack.shadowReach(chunks);
    }

    @ModifyReturnValue(method = "getProjection()Lorg/joml/Matrix4f;", at = @At("RETURN"))
    private static Matrix4f eminus$farProjection(Matrix4f projection) {
        return IrisFarState.distantHorizons() ? new Matrix4f(IrisFarState.projection()) : projection;
    }

    @ModifyReturnValue(method = "getNearPlane()F", at = @At("RETURN"))
    private static float eminus$farNearPlane(float plane) {
        return IrisFarState.distantHorizons() ? IrisFarState.nearPlane() : plane;
    }

    @ModifyReturnValue(method = "getFarPlane()F", at = @At("RETURN"))
    private static float eminus$farFarPlane(float plane) {
        return IrisFarState.distantHorizons() ? IrisFarState.farPlane() : plane;
    }

    @ModifyReturnValue(method = "getDepthTex()I", at = @At("RETURN"))
    private int eminus$farDepth(int texture) {
        return IrisFarState.distantHorizons() ? IrisFarState.depth() : texture;
    }

    @ModifyReturnValue(method = "getDepthTexNoTranslucent()I", at = @At("RETURN"))
    private int eminus$farOpaqueDepth(int texture) {
        return IrisFarState.distantHorizons() ? IrisFarState.opaqueDepth() : texture;
    }
}
