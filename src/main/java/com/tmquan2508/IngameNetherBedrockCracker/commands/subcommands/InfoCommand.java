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
                // context.getSource().sendFeedback(Text.literal("Cracking Status: " + (progress.isCrackingActive() ? "Active" : "Inactive")));
                // context.getSource().sendFeedback(Text.literal(String.format("Progress: %.2f%% (%d / %d)",
                // progress.getPercentage(), progress.getUnitsProcessedSoFar(), progress.getTotalUnitsToProcess())));
                // context.getSource().sendFeedback(Text.literal("Seeds Found: " + progress.getSeedsFoundCount()));

                return Command.SINGLE_SUCCESS;
            }));
    }
}