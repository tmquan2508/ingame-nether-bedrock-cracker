package dev.tmquan2508.netherbedrockseedcracker.buildscript;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.tasks.Exec;

public class CreateJavaBindingsTask extends Exec {

    private static final String JEXTRACT_BASE_PATH = "./jextract/build/jextract/bin/jextract";
    private static final String OS_EXTENSION = Os.isFamily(Os.FAMILY_WINDOWS) ? ".bat" : "";

    {
        this.getOutputs().upToDateWhen(task -> false);
        this.setWorkingDir(this.getProject().getRootDir());
        this.setStandardOutput(System.out);

        String jextractExecutablePath = JEXTRACT_BASE_PATH + OS_EXTENSION;
        String outputDirectory = "src/main/java";
        String targetPackageName = "com.github.netherbedrockcracker";
        String libraryName = "bedrock_cracker";
        String headerFilePath = "src/main/nbc/bedrock_cracker.h";

        this.commandLine(
            jextractExecutablePath,
            "--source",
            "--output", outputDirectory,
            "--target-package", targetPackageName,
            "--library", libraryName,
            headerFilePath
        );
    }
}