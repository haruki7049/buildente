package dev.haruki7049.buildente;

import java.util.Arrays;
import java.util.List;

/**
 * Entry point for the Buildente build system demo.
 *
 * <p>This class acts as a sample build script that demonstrates the Buildente API.
 * It mirrors the pattern of a {@code build.zig} file in the Zig build system:
 *
 * <pre>{@code
 * // Typical usage of the Buildente API:
 * Build b = new Build(argList);
 * Executable exe = b.addExecutable("Hello", "examples/Hello.java");
 * RunStep    run = b.addRunArtifact(exe);
 * b.step("run", "Compile and run the example").dependOn(run);
 * b.getInstallStep().dependOn(exe);
 * }</pre>
 *
 * <p>Supported steps:
 * <ul>
 *   <li>{@code install} (default) – compile the example source file</li>
 *   <li>{@code run}               – compile and run the example</li>
 * </ul>
 *
 * <p>Usage: {@code ./gradlew run [--args="<step>"]}
 * <br>Example: {@code ./gradlew run --args="run"}
 */
public class Main {

    public static void main(String[] args) {
        List<String> argList = Arrays.asList(args);

        // Determine which step the user requested (default: "install")
        String requestedStep = argList.isEmpty() ? "install" : argList.get(0);

        if ("--help".equals(requestedStep) || "-h".equals(requestedStep)) {
            printUsage();
            return;
        }

        // ------------------------------------------------------------------
        // Build graph definition (mirrors a user-written build.java script)
        // ------------------------------------------------------------------
        Build b = new Build(argList);

        // 1. Declare a compilable Java program
        Executable exe = b.addExecutable(
            "dev.haruki7049.buildente.example.Hello",
            "buildente-app/examples/Hello.java"
        );

        // 2. Declare a step to run the compiled program
        RunStep run = b.addRunArtifact(exe);

        // 3. Wire steps to top-level targets
        //    "install" -> compile only
        b.getInstallStep().dependOn(exe);

        //    "run" -> compile then execute
        b.step("run", "Compile and run the Hello example").dependOn(run);

        // ------------------------------------------------------------------
        // Execute
        // ------------------------------------------------------------------
        System.out.println("[buildente] Build started. Step: " + requestedStep);
        b.executeStep(requestedStep);
        System.out.println("[buildente] Build finished.");
    }

    /** Prints usage information to standard output. */
    private static void printUsage() {
        System.out.println("Buildente — a Java build system inspired by Zig's build system");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  ./gradlew run                  Run the default 'install' step");
        System.out.println("  ./gradlew run --args=\"run\"     Run the 'run' step");
        System.out.println("  ./gradlew run --args=\"--help\"  Show this help message");
    }
}