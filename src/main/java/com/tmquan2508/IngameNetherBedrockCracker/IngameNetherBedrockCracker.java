package com.tmquan2508.IngameNetherBedrockCracker;

import com.tmquan2508.IngameNetherBedrockCracker.commands.NetherCrackerCommand;
import com.tmquan2508.IngameNetherBedrockCracker.cracker.BedrockCrackerService;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class IngameNetherBedrockCracker implements ClientModInitializer {
    public static final String MOD_ID = "ingame-nether-bedrock-cracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static BedrockCrackerService bedrockCrackerService;

    static {
        String baseLibraryName = "bedrock_cracker";
        String mappedLibraryName = System.mapLibraryName(baseLibraryName);
        String libraryPathInJar = "nativelib/" + mappedLibraryName;

        Path tempFile = null;

        try {
            ModContainer modContainer = FabricLoader.getInstance().getModContainer(MOD_ID)
                    .orElseThrow(() -> new RuntimeException("Could not find mod container for " + MOD_ID));
            Path libraryInJarPath = modContainer.findPath(libraryPathInJar)
                    .orElseThrow(() -> new IOException("Native library '" + libraryPathInJar + "' not found in mod JAR. Ensure it's in resources/native/"));

            String prefix = baseLibraryName + "_";
            String suffix;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) {
                suffix = ".dylib";
            } else if (os.contains("win")) {
                suffix = ".dll";
            } else if (os.contains("linux") || os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                suffix = ".so";
            } else {
                throw new UnsupportedOperationException("Unsupported OS for native library: " + os);
            }


            tempFile = Files.createTempFile(prefix, suffix);

            try (InputStream in = Files.newInputStream(libraryInJarPath);
                 OutputStream out = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            System.load(tempFile.toAbsolutePath().toString());
            LOGGER.info("Native library '{}' loaded successfully from temporary file: {}", mappedLibraryName, tempFile.toAbsolutePath().toString());
        } catch (Exception e) {
            String errorMessage = "Failed to load native library '" + mappedLibraryName + "'.";
            if (tempFile != null) {
                LOGGER.error("{} Attempted from: {}", errorMessage, tempFile.toAbsolutePath(), e);
            } else {
                LOGGER.error(errorMessage, e);
            }
            throw new RuntimeException(errorMessage + " Mod functionality will be limited.", e);
        } finally {
            if (tempFile != null && Files.exists(tempFile)) {
                 try {
                     tempFile.toFile().deleteOnExit();
                 } catch (Exception ex) {
                     LOGGER.warn("Could not mark temporary native library for deletion: {}", tempFile.toAbsolutePath(), ex);
                 }
            }
        }
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing {}...", MOD_ID);

        bedrockCrackerService = new BedrockCrackerService();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            NetherCrackerCommand.register(dispatcher, bedrockCrackerService);
            LOGGER.info("Registered NetherCracker command.");
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (bedrockCrackerService != null) {
                LOGGER.info("Client is stopping, shutting down BedrockCrackerService.");
                bedrockCrackerService.shutdown();
            }
        });

        LOGGER.info("{} initialized successfully.", MOD_ID);
    }
}