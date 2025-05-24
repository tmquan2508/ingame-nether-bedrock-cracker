package com.tmquan2508.IngameNetherBedrockCracker;

import com.tmquan2508.IngameNetherBedrockCracker.commands.NetherCrackerCommand;
// Import các service và helper mới
import com.tmquan2508.IngameNetherBedrockCracker.cracker.BedrockCrackerService;
import com.tmquan2508.IngameNetherBedrockCracker.gameintegration.BedrockFinder; // Nếu bạn đã chuyển BedrockFinder vào gói này

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents; // Để gọi shutdown
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

    // Khai báo các service
    private static BedrockCrackerService bedrockCrackerService; // Giữ static để dễ truy cập từ shutdown hook nếu cần
    private BedrockFinder bedrockFinder; // Không cần static nếu chỉ truyền vào command

    static {
        String baseLibraryName = "bedrock_cracker";
        String mappedLibraryName = System.mapLibraryName(baseLibraryName);
        String libraryPathInJar = "native/" + mappedLibraryName; // Đường dẫn trong JAR

        Path tempFile = null;

        try {
            ModContainer modContainer = FabricLoader.getInstance().getModContainer(MOD_ID)
                    .orElseThrow(() -> new RuntimeException("Could not find mod container for " + MOD_ID));
            Path libraryInJarPath = modContainer.findPath(libraryPathInJar)
                    .orElseThrow(() -> new IOException("Native library '" + libraryPathInJar + "' not found in mod JAR. Ensure it's in resources/native/"));

            // Tạo tên file tạm thời chính xác hơn
            String prefix = baseLibraryName + "_";
            String suffix;
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) {
                suffix = ".dylib";
            } else if (os.contains("win")) {
                suffix = ".dll";
            } else if (os.contains("linux") || os.contains("nix") || os.contains("nux") || os.contains("aix")) { // Mở rộng cho Linux
                suffix = ".so";
            } else {
                // Fallback hoặc throw lỗi nếu OS không được hỗ trợ rõ ràng
                throw new UnsupportedOperationException("Unsupported OS for native library: " + os);
            }


            tempFile = Files.createTempFile(prefix, suffix);
            // tempFile.toFile().deleteOnExit(); // deleteOnExit có thể không đáng tin cậy trong mọi trường hợp

            try (InputStream in = Files.newInputStream(libraryInJarPath); // Sử dụng Files.newInputStream
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
            // Cân nhắc việc không throw RuntimeException ở đây để mod vẫn có thể load
            // và chỉ các tính năng native không hoạt động, thay vì làm crash game.
            // Tuy nhiên, nếu native lib là cốt lõi, throw là hợp lý.
            throw new RuntimeException(errorMessage + " Mod functionality will be limited.", e);
        } finally {
            // Dọn dẹp file tạm sau khi load (hoặc nếu load thất bại)
            // deleteOnExit() không phải lúc nào cũng hoạt động, đặc biệt nếu JVM bị kill đột ngột.
            // Cân nhắc việc xóa thủ công nếu có thể, nhưng việc này phức tạp hơn.
            // Với client mod, deleteOnExit() thường là đủ.
            if (tempFile != null && Files.exists(tempFile)) {
                 try {
                     // LOGGER.info("Attempting to mark temporary native library for deletion on exit: {}", tempFile.toAbsolutePath());
                     // Files.deleteIfExists(tempFile); // Xóa ngay có thể gây lỗi "library already loaded in another classloader" nếu load lại mod
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

        // Khởi tạo services
        bedrockCrackerService = new BedrockCrackerService();
        bedrockFinder = new BedrockFinder();
        
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            NetherCrackerCommand.register(dispatcher, bedrockCrackerService, bedrockFinder);
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