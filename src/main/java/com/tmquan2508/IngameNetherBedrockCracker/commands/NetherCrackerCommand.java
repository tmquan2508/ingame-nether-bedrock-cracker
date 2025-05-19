package com.tmquan2508.IngameNetherBedrockCracker.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text; // Để gửi phản hồi cho người chơi (tùy chọn)

// Lấy logger từ class chính để tiện sử dụng
import static com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker.LOGGER;
import static com.tmquan2508.IngameNetherBedrockCracker.IngameNetherBedrockCracker.MOD_ID;


public class NetherCrackerCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // Lệnh chính /nethercracker
        dispatcher.register(ClientCommandManager.literal("nethercracker")
            .then(ClientCommandManager.literal("start")
                .executes(context -> {
                    LOGGER.info("[{}] Command /nethercracker start executed", MOD_ID);
                    context.getSource().sendFeedback(Text.literal("NetherCracker: Start command received."));
                    // TODO: Thêm logic thực sự cho lệnh start
                    return Command.SINGLE_SUCCESS; // Hoặc 1
                }))
            .then(ClientCommandManager.literal("info")
                .executes(context -> {
                    LOGGER.info("[{}] Command /nethercracker info executed", MOD_ID);
                    context.getSource().sendFeedback(Text.literal("NetherCracker: Info command received."));
                    // TODO: Thêm logic thực sự cho lệnh info
                    return Command.SINGLE_SUCCESS; // Hoặc 1
                }))
            .then(ClientCommandManager.literal("stop")
                .executes(context -> {
                    LOGGER.info("[{}] Command /nethercracker stop executed", MOD_ID);
                    context.getSource().sendFeedback(Text.literal("NetherCracker: Stop command received."));
                    // TODO: Thêm logic thực sự cho lệnh stop
                    return Command.SINGLE_SUCCESS; // Hoặc 1
                }))
            // Có thể thêm một lệnh mặc định nếu /nethercracker được gọi mà không có subcommand
            .executes(context -> {
                LOGGER.info("[{}] Command /nethercracker executed (no subcommand)", MOD_ID);
                context.getSource().sendFeedback(Text.literal("NetherCracker: Use /nethercracker <start|info|stop>"));
                return Command.SINGLE_SUCCESS; // Hoặc 1
            })
        );
    }
}