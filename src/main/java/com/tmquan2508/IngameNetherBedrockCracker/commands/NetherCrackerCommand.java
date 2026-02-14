package com.tmquan2508.IngameNetherBedrockCracker.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.BedrockCrackerService;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands.GetCommand;
import com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands.StartCommand;
import com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands.StopCommand;
import com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands.InfoCommand;
import com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands.ValidateCommand;

public class NetherCrackerCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, BedrockCrackerService crackerService) {
        LiteralArgumentBuilder<FabricClientCommandSource> mainCommandNode = ClientCommandManager.literal("inbc")
            .executes(context -> {
                context.getSource().sendFeedback(Text.literal(
                    "§aCommand List:\n" +
                    "§f/inbc start <mode> <threads> §7start cracker\n" +
                    "§f/inbc info §7get infomation of running progress\n" +
                    "§f/inbc stop §7stop cracker\n" +
                    "§f/inbc get [fullScan] §7get list of bedrock coordinates (default: false)" +
                    "\n§f/inbc validate <seed> <mode> §7validate seed with bedrock (overworld and nether)"
                ));
                return Command.SINGLE_SUCCESS;
            });

        GetCommand.register(mainCommandNode);
        StartCommand.register(mainCommandNode, crackerService);
        StopCommand.register(mainCommandNode, crackerService);
        InfoCommand.register(mainCommandNode, crackerService);
        ValidateCommand.register(mainCommandNode);

        dispatcher.register(mainCommandNode);
    }
}