package com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands;

import com.github.netherbedrockcracker.bedrock_cracker_h;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.BedrockCrackerService;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.dto.BlockInput;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.enums.CrackerMode;
import com.tmquan2508.IngameNetherBedrockCracker.helpers.BedrockFinder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class StartCommand {
    private static final String[] GENERATION_TYPES = {"NORMAL", "PAPER1_18"};
    private static final int MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> parent, BedrockCrackerService crackerService) {
        parent.then(ClientCommandManager.literal("start")
            .then(ClientCommandManager.argument("generation", StringArgumentType.string())
                .suggests(StartCommand::suggestGenerationTypes)
                .then(ClientCommandManager.argument("threads", IntegerArgumentType.integer(1, MAX_THREADS))
                    .executes(context -> {
                        ClientPlayerEntity player = context.getSource().getPlayer();
                        ClientWorld world = context.getSource().getWorld();

                        if (player == null || world == null) {
                            context.getSource().sendError(Text.literal("Command must be run by a player in a world."));
                            return 0;
                        }

                        if (!world.getRegistryKey().getValue().getPath().endsWith("the_nether")) {
                            context.getSource().sendError(Text.literal("This command can only be used in The Nether."));
                            return 0;
                        }

                        String generationTypeArg = StringArgumentType.getString(context, "generationType");
                        int threadsArg = IntegerArgumentType.getInteger(context, "threads");

                        CrackerMode crackerMode = CrackerMode.fromString(generationTypeArg.toUpperCase(Locale.ROOT));
                        if (crackerMode == null) {
                            String validOptions = String.join(", ", GENERATION_TYPES);
                            context.getSource().sendError(Text.literal("Invalid generationType '" + generationTypeArg + "'. Allowed types are: " + validOptions));
                            return 0;
                        }

                        if (crackerService.isCrackingActive()) {
                            context.getSource().sendError(Text.literal("A cracking process is already running. Use /nethercracker stop first or /nethercracker info."));
                            return 0;
                        }

                        IngameNetherBedrockCracker.LOGGER.info("'/nethercracker start' command initiated. Type: {}, Threads: {}", crackerMode.name(), threadsArg);
                        context.getSource().sendFeedback(Text.literal("Scanning for bedrock formations... This might take a moment."));

                        CompletableFuture.runAsync(() -> {
                            try {
                                BlockPos playerPos = player.getBlockPos();
                                List<BedrockFinder.FoundBedrock> foundRawBedrock = BedrockFinder.findBedrockNearby(world, playerPos);

                                MinecraftClient.getInstance().execute(() -> {
                                    if (foundRawBedrock.isEmpty()) {
                                        context.getSource().sendError(Text.literal("No bedrock found at y=" +
                                            BedrockFinder.Y_LEVEL_FLOOR + " or y=" + BedrockFinder.Y_LEVEL_CEILING +
                                            " within " + BedrockFinder.SEARCH_RADIUS_BLOCKS + " blocks."));
                                        return;
                                    }

                                    IngameNetherBedrockCracker.LOGGER.info("Found {} bedrock positions. Preparing for cracking.", foundRawBedrock.size());
                                    context.getSource().sendFeedback(Text.literal("Found " + foundRawBedrock.size() + " bedrock positions. Starting cracker..."));

                                    List<BlockInput> blockInputs = new ArrayList<>();
                                    for (BedrockFinder.FoundBedrock bedrock : foundRawBedrock) {
                                        blockInputs.add(new BlockInput(bedrock.x(), bedrock.y(), bedrock.z(), bedrock_cracker_h.BlockType_BEDROCK()));
                                    }

                                    boolean started = crackerService.startCracking(blockInputs, crackerMode, threadsArg);

                                    if (started) {
                                        context.getSource().sendFeedback(Text.literal("Bedrock cracking process initiated. Use /nethercracker info for progress."));
                                    } else {
                                        context.getSource().sendError(Text.literal("Failed to start cracking process. It might already be running or an internal error occurred. Check logs."));
                                    }
                                });
                            } catch (Exception e) {
                                IngameNetherBedrockCracker.LOGGER.error("Error during bedrock scanning or crack initiation in StartCommand: ", e);
                                MinecraftClient.getInstance().execute(() ->
                                    context.getSource().sendError(Text.literal("An error occurred: " + e.getMessage()))
                                );
                            }
                        });
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
        );
    }

    private static CompletableFuture<Suggestions> suggestGenerationTypes(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toUpperCase(Locale.ROOT);
        for (String type : GENERATION_TYPES) {
            if (type.startsWith(input)) {
                builder.suggest(type);
            }
        }
        return builder.buildFuture();
    }
}