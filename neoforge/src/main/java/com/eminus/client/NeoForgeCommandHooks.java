package com.eminus.client;

import com.eminus.Eminus;
import com.eminus.cell.DetailLevel;
import com.eminus.mesh.client.MeshDump;
import com.eminus.model.client.ModelDump;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

public final class NeoForgeCommandHooks {
    private static final String BAKE = "bake";
    private static final String MESH = "mesh";
    private static final String MODELS = "models";
    private static final String LEVEL = "level";
    private static final int MIN_MODELS = 1;

    public static void register(IEventBus gameBus) {
        gameBus.addListener(RegisterClientCommandsEvent.class, event -> event.getDispatcher().register(
                Commands.literal(Eminus.MODID)
                        .then(Commands.literal(BAKE)
                                .executes(NeoForgeCommandHooks::sample)
                                .then(Commands.argument(MODELS, IntegerArgumentType.integer(MIN_MODELS))
                                        .executes(NeoForgeCommandHooks::upTo)))
                        .then(Commands.literal(MESH)
                                .then(Commands.argument(LEVEL,
                                                IntegerArgumentType.integer(DetailLevel.MIN, DetailLevel.MAX))
                                        .executes(NeoForgeCommandHooks::mesh)))));
    }

    private static int sample(CommandContext<CommandSourceStack> context) {
        return answer(context, ModelDump.sample());
    }

    private static int upTo(CommandContext<CommandSourceStack> context) {
        return answer(context, ModelDump.upTo(IntegerArgumentType.getInteger(context, MODELS)));
    }

    private static int mesh(CommandContext<CommandSourceStack> context) {
        return answer(context, MeshDump.at(IntegerArgumentType.getInteger(context, LEVEL)));
    }

    private static int answer(CommandContext<CommandSourceStack> context, Component result) {
        context.getSource().sendSuccess(() -> result, false);
        return Command.SINGLE_SUCCESS;
    }

    private NeoForgeCommandHooks() {
    }
}
