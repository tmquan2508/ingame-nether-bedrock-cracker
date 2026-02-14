package com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.enums.CrackerMode;
import com.tmquan2508.IngameNetherBedrockCracker.helpers.BedrockFinder;
import com.tmquan2508.IngameNetherBedrockCracker.helpers.BedrockValidator;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class ValidateCommand {
    private static final String[] MODES = { "NORMAL", "PAPER1_18" };

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> parent) {
        parent.then(ClientCommandManager.literal("validate")
                .then(ClientCommandManager.argument("seed", LongArgumentType.longArg())
                        .then(ClientCommandManager.argument("mode", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (String mode : MODES) {
                                        if (mode.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                                            builder.suggest(mode);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> validate(context.getSource(),
                                        LongArgumentType.getLong(context, "seed"),
                                        StringArgumentType.getString(context, "mode"))))
                        .executes(context -> validate(context.getSource(), LongArgumentType.getLong(context, "seed"),
                                "NORMAL"))));
    }

    private static int validate(FabricClientCommandSource source, long seed, String modeStr) {
        ClientWorld world = source.getWorld();
        BlockPos playerPos = source.getPlayer().getBlockPos();

        CrackerMode mode = CrackerMode.fromString(modeStr.toUpperCase(Locale.ROOT));
        if (mode == null) {
            source.sendError(Text.literal("Invalid mode: " + modeStr + ". Use NORMAL or PAPER1_18."));
            return 0;
        }

        source.sendFeedback(Text.literal("Validating bedrock for seed: ")
                .append(Text.literal(String.valueOf(seed)).formatted(Formatting.AQUA)));

        CompletableFuture.runAsync(() -> {
            try {
                List<BedrockFinder.FoundBedrock> foundBlocks = BedrockFinder.findBedrockNearby(world, playerPos, true);

                int totalChecked = 0;
                int matches = 0;

                for (BedrockFinder.FoundBedrock block : foundBlocks) {
                    boolean predictedIsBedrock = BedrockValidator.isBedrock(block.x(), block.y(), block.z(), seed,
                            world, mode);
                    if (predictedIsBedrock) {
                        matches++;
                    }
                    totalChecked++;
                }

                int finalMatches = matches;
                int finalTotal = totalChecked;

                MinecraftClient.getInstance().execute(() -> {
                    if (finalTotal == 0) {
                        source.sendError(Text.literal("No bedrock found nearby to validate against."));
                    } else {
                        double accuracy = (double) finalMatches / finalTotal * 100.0;
                        Formatting color = accuracy > 95 ? Formatting.GREEN
                                : (accuracy > 50 ? Formatting.YELLOW : Formatting.RED);

                        source.sendFeedback(Text.literal("Validation Results:")
                                .formatted(Formatting.BOLD, Formatting.GOLD));
                        source.sendFeedback(Text.literal("  - Total Blocks Checked: ")
                                .append(Text.literal(String.valueOf(finalTotal)).formatted(Formatting.WHITE)));
                        source.sendFeedback(Text.literal("  - Matches: ")
                                .append(Text.literal(String.valueOf(finalMatches)).formatted(Formatting.WHITE)));
                        source.sendFeedback(Text.literal("  - Accuracy: ")
                                .append(Text.literal(String.format("%.2f%%", accuracy)).formatted(color)));

                        if (accuracy > 99.9) {
                            source.sendFeedback(
                                    Text.literal("✔ Seed matches the bedrock patterns!").formatted(Formatting.GREEN));
                        } else {
                            source.sendFeedback(Text.literal("✖ Seed does NOT match the bedrock patterns.")
                                    .formatted(Formatting.RED));
                        }
                    }
                });
            } catch (Exception e) {
                MinecraftClient.getInstance()
                        .execute(() -> source.sendError(Text.literal("Validation Error: " + e.getMessage())));
            }
        });

        return Command.SINGLE_SUCCESS;
    }
}
