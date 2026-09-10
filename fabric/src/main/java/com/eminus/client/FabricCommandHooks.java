package com.eminus.client;

import com.eminus.Eminus;
import com.eminus.cell.DetailLevel;
import com.eminus.mesh.client.MeshDump;
import com.eminus.model.client.ModelDump;
import com.eminus.render.arena.client.ArenaDump;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.network.chat.Component;

public final class FabricCommandHooks {
    private static final String BAKE = "bake";
    private static final String MESH = "mesh";
    private static final String ARENA = "arena";
    private static final String MODELS = "models";
    private static final String LEVEL = "level";
    private static final int MIN_MODELS = 1;

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal(Eminus.MODID)
                        .then(ClientCommands.literal(BAKE)
                                .executes(FabricCommandHooks::sample)
                                .then(ClientCommands.argument(MODELS, IntegerArgumentType.integer(MIN_MODELS))
                                        .executes(FabricCommandHooks::upTo)))
                        .then(ClientCommands.literal(MESH)
                                .then(ClientCommands.argument(LEVEL,
                                                IntegerArgumentType.integer(DetailLevel.MIN, DetailLevel.MAX))
                                        .executes(FabricCommandHooks::mesh)))
                        .then(ClientCommands.literal(ARENA)
                                .then(ClientCommands.argument(LEVEL,
                                                IntegerArgumentType.integer(DetailLevel.MIN, DetailLevel.MAX))
                                        .executes(FabricCommandHooks::arena)))));
    }

    private static int sample(CommandContext<FabricClientCommandSource> context) {
        return answer(context, ModelDump.sample());
    }

    private static int upTo(CommandContext<FabricClientCommandSource> context) {
        return answer(context, ModelDump.upTo(IntegerArgumentType.getInteger(context, MODELS)));
    }

    private static int mesh(CommandContext<FabricClientCommandSource> context) {
        return answer(context, MeshDump.at(IntegerArgumentType.getInteger(context, LEVEL)));
    }

    private static int arena(CommandContext<FabricClientCommandSource> context) {
        return answer(context, ArenaDump.at(IntegerArgumentType.getInteger(context, LEVEL)));
    }

    private static int answer(CommandContext<FabricClientCommandSource> context, Component result) {
        context.getSource().sendFeedback(result);
        return Command.SINGLE_SUCCESS;
    }

    private FabricCommandHooks() {
    }
}
