package com.tmquan2508.IngameNetherBedrockCracker.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.BedrockCrackerService;
import com.tmquan2508.IngameNetherBedrockCracker.gameintegration.BedrockFinder; // Đảm bảo import đúng
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands.GetCommand;
import com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands.StartCommand;
import com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands.StopCommand;
import com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands.InfoCommand;

public class NetherCrackerCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                BedrockCrackerService crackerService,
                                BedrockFinder bedrockFinder) {
        LiteralArgumentBuilder<FabricClientCommandSource> mainCommandNode = ClientCommandManager.literal("nethercracker")
            .executes(context -> {
                context.getSource().sendFeedback(Text.literal("Usage: /nethercracker <start | stop | info> [args...]"));
                return Command.SINGLE_SUCCESS;
            });

        GetCommand.register(mainCommandNode, bedrockFinder);
        StartCommand.register(mainCommandNode, crackerService, bedrockFinder);
        StopCommand.register(mainCommandNode, crackerService);
        InfoCommand.register(mainCommandNode, crackerService);

        dispatcher.register(mainCommandNode);
    }
}