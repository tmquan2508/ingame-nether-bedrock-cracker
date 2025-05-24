package dev.tmquan2508.netherbedrockseedcracker.buildscript;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class CopyNativeLibTask extends DefaultTask {

    @TaskAction
    public void copyNativeLib() throws IOException {
        String libName = System.mapLibraryName("bedrock_cracker");
        File projectDir = getProject().getProjectDir();

        Path srcLibPath = new File(projectDir, "src/main/nbc/target/release/" + libName).toPath();
        Path destLibPath = new File(projectDir, "src/main/resources/native/" + libName).toPath();
        
        Files.createDirectories(destLibPath.getParent());

        Files.copy(srcLibPath, destLibPath, StandardCopyOption.REPLACE_EXISTING);
        getLogger().lifecycle("Copied " + libName + " to src/main/resources/native/");
    }
}