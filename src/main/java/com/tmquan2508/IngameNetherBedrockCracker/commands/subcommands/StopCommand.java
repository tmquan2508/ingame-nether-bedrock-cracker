package com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.BedrockCrackerService;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

public class StopCommand {
    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> parent,
            BedrockCrackerService crackerService) {
        parent.then(ClientCommandManager.literal("stop")
                .executes(context -> {
                    IngameNetherBedrockCracker.LOGGER.info("'/inbc stop' command executed.");

                    if (crackerService.isCrackingActive()) {
                        crackerService.requestStop();
                        context.getSource().sendFeedback(Text
                                .literal("Sent stop request to the cracking process. Check /inbc info for status."));
                    } else {
                        context.getSource().sendFeedback(Text.literal("No cracking process is currently active."));
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }
}