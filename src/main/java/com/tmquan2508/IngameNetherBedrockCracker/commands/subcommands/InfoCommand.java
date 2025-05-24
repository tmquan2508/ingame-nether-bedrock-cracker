package com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.BedrockCrackerService; // Thêm import
import com.tmquan2508.IngameNetherBedrockCracker.cracker.dto.CrackerProgressInfo; // Thêm import
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text; // Thêm import

public class InfoCommand {
    // Sửa signature của phương thức register
    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> parent,
                                BedrockCrackerService crackerService) {
        parent.then(ClientCommandManager.literal("info")
            .executes(context -> {
                IngameNetherBedrockCracker.LOGGER.info("'/nethercracker info' command executed.");

                CrackerProgressInfo progress = crackerService.getProgress();

                // Gửi thông tin progress cho người chơi
                context.getSource().sendFeedback(Text.literal(progress.toString()));
                // Ví dụ chi tiết hơn nếu muốn:
                // context.getSource().sendFeedback(Text.literal("Cracking Status: " + (progress.isCrackingActive() ? "Active" : "Inactive")));
                // context.getSource().sendFeedback(Text.literal(String.format("Progress: %.2f%% (%d / %d)",
                // progress.getPercentage(), progress.getUnitsProcessedSoFar(), progress.getTotalUnitsToProcess())));
                // context.getSource().sendFeedback(Text.literal("Seeds Found: " + progress.getSeedsFoundCount()));

                return Command.SINGLE_SUCCESS;
            }));
    }
}