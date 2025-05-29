package com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.BedrockCrackerService;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.dto.CrackerProgressInfo;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

public class InfoCommand {
    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> parent, BedrockCrackerService crackerService) {
        parent.then(ClientCommandManager.literal("info")
            .executes(context -> {
                CrackerProgressInfo progress = crackerService.getProgress();

                context.getSource().sendFeedback(Text.literal(progress.toString()));

                return Command.SINGLE_SUCCESS;
            }));
    }
}