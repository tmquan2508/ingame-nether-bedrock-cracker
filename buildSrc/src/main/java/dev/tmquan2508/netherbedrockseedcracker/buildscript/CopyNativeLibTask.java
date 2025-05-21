package dev.tmquan2508.netherbedrockseedcracker.buildscript;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CopyNativeLibTask extends DefaultTask {

    @TaskAction
    public void copyAndModifyNativeLib() throws Exception {
        String baseName = "bedrock_cracker";
        String libName = System.mapLibraryName(baseName);
        String targetArchPath = "";

        File projectDir = getProject().getProjectDir();
        File submoduleBuildDir = new File(projectDir, "src/main/nbc/target/" + targetArchPath + "release/");
        File srcLibFile = new File(submoduleBuildDir, libName);

        if (!srcLibFile.exists()) {
            String targetArg = targetArchPath.isEmpty() ? "" : " --target " + targetArchPath.substring(0, targetArchPath.length() -1);
            throw new IllegalStateException("Native library not found at: " + srcLibFile.getAbsolutePath() +
                ".\nMake sure the Rust submodule (src/main/nbc) is built first (e.g., using 'cargo build --release" +
                targetArg + "').");
        }

        File destResourcesDir = new File(projectDir, "src/main/resources/native");
        if (!destResourcesDir.exists()) {
            if (!destResourcesDir.mkdirs()) {
                throw new IOException("Failed to create destination directory: " + destResourcesDir.getAbsolutePath());
            }
        }
        Path destLibPath = destResourcesDir.toPath().resolve(libName);

        Files.copy(srcLibFile.toPath(), destLibPath, StandardCopyOption.REPLACE_EXISTING);
        getLogger().lifecycle("Copied native library: " + srcLibFile.getName() + " to " + destLibPath);

        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac")) {
            String newInstallName = libName;

            List<String> command = new ArrayList<>(Arrays.asList(
                "install_name_tool",
                "-id",
                newInstallName,
                destLibPath.toAbsolutePath().toString()
            ));

            getLogger().lifecycle("Attempting to change install name of " + destLibPath.getFileName() + " to '" + newInstallName + "' using command: " + String.join(" ", command));

            ExecResult result = getProject().exec(execSpec -> {
                execSpec.commandLine(command);
                execSpec.setIgnoreExitValue(true);
            });

            if (result.getExitValue() == 0) {
                getLogger().lifecycle("Successfully changed install name for " + destLibPath.getFileName());
                List<String> otoolCommand = new ArrayList<>(Arrays.asList(
                    "otool",
                    "-L",
                    destLibPath.toAbsolutePath().toString()
                ));
                getLogger().lifecycle("Verifying install name with command: " + String.join(" ", otoolCommand));
                getProject().exec(execSpec -> {
                    execSpec.commandLine(otoolCommand);
                });
            } else {
                getLogger().error("Failed to change install name for " + destLibPath.getFileName() + ". Exit code: " + result.getExitValue());
            }
        } else {
            getLogger().lifecycle("Skipping install_name_tool step on non-macOS system: " + osName);
        }
    }
}