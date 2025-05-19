package com.tmquan2508.IngameNetherBedrockCracker;

import com.tmquan2508.IngameNetherBedrockCracker.bridge.NativeBedrockCrackerLibrary;
import com.tmquan2508.IngameNetherBedrockCracker.commands.NetherCrackerCommand;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class IngameNetherBedrockCracker implements ClientModInitializer {
    public static final String MOD_ID = "ingame-nether-bedrock-cracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final AtomicBoolean nativeLibraryLoaded = new AtomicBoolean(false);

    public static Thread currentTask = null;
    public static final AtomicBoolean isCracking = new AtomicBoolean(false);
    public static long startTimeMillis = 0;
    public static final List<Long> foundSeeds = new CopyOnWriteArrayList<>();
    public static final NativeBedrockCrackerLibrary.NativeMemoryHolder nativeMemoryHolder = new NativeBedrockCrackerLibrary.NativeMemoryHolder();
    public static final Object LOCK = new Object();


    static {
        LOGGER.info("[{}] Static initializer: Attempting to load native library...", MOD_ID);
        String baseLibraryName = "bedrock_cracker";
        String mappedLibraryName = System.mapLibraryName(baseLibraryName);
        String libraryPathInJar = "native/" + mappedLibraryName;

        Path tempFile = null;

        try {
            ModContainer modContainer = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Could not find ModContainer for " + MOD_ID));

            Path libraryInJar = modContainer.findPath(libraryPathInJar)
                .orElseThrow(() -> new IOException("Native library '" + libraryPathInJar + "' not found in JAR."));

            String suffix;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) suffix = ".dylib";
            else if (os.contains("win")) suffix = ".dll";
            else suffix = ".so";

            tempFile = Files.createTempFile(baseLibraryName + "_", suffix);
            tempFile.toFile().deleteOnExit();

            LOGGER.info("[{}] Copying native library from JAR path '{}' to temporary file '{}'",
                MOD_ID, libraryInJar, tempFile.toAbsolutePath());

            try (InputStream in = Files.newInputStream(libraryInJar);
                 OutputStream out = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            String loadedLibraryPath = tempFile.toAbsolutePath().toString();
            LOGGER.info("[{}] Attempting to load native library from: {}", MOD_ID, loadedLibraryPath);

            System.load(loadedLibraryPath);

            nativeLibraryLoaded.set(true);
            LOGGER.info("[{}] SUCCESSFULLY LOADED NATIVE LIBRARY: {}", MOD_ID, mappedLibraryName);

        } catch (UnsatisfiedLinkError ule) {
            LOGGER.error("[{}] FAILED TO LOAD NATIVE LIBRARY (UnsatisfiedLinkError): {}", MOD_ID, mappedLibraryName, ule);
            if (tempFile != null) {
                LOGGER.error("[{}] Library was at temporary path: {}", MOD_ID, tempFile.toAbsolutePath());
            }
            LOGGER.error("[{}] java.library.path = {}", MOD_ID, System.getProperty("java.library.path"));
        } catch (Throwable t) {
            LOGGER.error("[{}] CRITICAL FAILURE DURING NATIVE LIBRARY LOADING for '{}':", MOD_ID, mappedLibraryName, t);
            if (tempFile != null) {
                LOGGER.error("[{}] Library was at temporary path: {}", MOD_ID, tempFile.toAbsolutePath());
            }
             nativeLibraryLoaded.set(false);
        }
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("[{}] onInitializeClient called.", MOD_ID);
        if (nativeLibraryLoaded.get()) {
            LOGGER.info("[{}] Native library was loaded successfully. Mod can proceed.", MOD_ID);
        } else {
            LOGGER.error("[{}] Native library FAILED to load. Mod will NOT function correctly.", MOD_ID);
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            NetherCrackerCommand.register(dispatcher);
        });
        LOGGER.info("[{}] Registered client commands.", MOD_ID);
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (isCracking.get() && (currentTask == null || !currentTask.isAlive())) {
                if (currentTask != null && currentTask.isInterrupted()){
                     LOGGER.info("[{}] Cracker task was confirmed interrupted or finished unexpectedly after interrupt.", MOD_ID);
                } else {
                     LOGGER.warn("[{}] Cracker task finished or died unexpectedly.", MOD_ID);
                }
                isCracking.set(false);
                currentTask = null;
            }
        });
    }
}