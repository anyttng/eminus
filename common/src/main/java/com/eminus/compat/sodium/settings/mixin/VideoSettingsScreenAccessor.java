package com.eminus.compat.sodium.settings.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.Screen;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen")
public interface VideoSettingsScreenAccessor {
    @Accessor("prevScreen")
    Screen eminus$prevScreen();
}
