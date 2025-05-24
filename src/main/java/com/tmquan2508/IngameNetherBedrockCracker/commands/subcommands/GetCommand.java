package com.tmquan2508.IngameNetherBedrockCracker.commands.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tmquan2508.IngameNetherBedrockCracker.helpers.BedrockFinder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class GetCommand {
    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> parent) {
        parent.then(ClientCommandManager.literal("get").executes(context -> {
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

            BlockPos senderPos = player.getBlockPos();
            List<BedrockFinder.FoundBedrock> bedrockBlocks =
                BedrockFinder.findBedrockNearby(world, senderPos);

            if (bedrockBlocks.isEmpty()) {
                context.getSource().sendFeedback(Text.literal("No bedrock found at y=" +
                    BedrockFinder.Y_LEVEL_FLOOR + " or y=" + BedrockFinder.Y_LEVEL_CEILING +
                    " within " + BedrockFinder.SEARCH_RADIUS_BLOCKS + " blocks."));
                return Command.SINGLE_SUCCESS;
            }

            StringBuilder fullListBuilder = new StringBuilder();
            for (BedrockFinder.FoundBedrock bedrock : bedrockBlocks) {
                String line = String.format("%d %d %d BEDROCK", bedrock.x(), bedrock.y(), bedrock.z());
                fullListBuilder.append(line).append("\n");
            }

            String fullListStringToCopy = fullListBuilder.toString().trim();

            MutableText copyMessage = Text.empty()
                .append(Text.literal("[").setStyle(Style.EMPTY.withColor(Formatting.WHITE)))
                .append(Text.literal("Click to copy all " + bedrockBlocks.size() + " locations")
                    .setStyle(Style.EMPTY
                        .withColor(Formatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, fullListStringToCopy))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Copy to clipboard")))
                    ))
                .append(Text.literal("]").setStyle(Style.EMPTY.withColor(Formatting.WHITE)));

            context.getSource().sendFeedback(copyMessage);
            return Command.SINGLE_SUCCESS;
        }));
    }
}