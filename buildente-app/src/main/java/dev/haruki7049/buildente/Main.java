package dev.haruki7049.buildente;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Buildente engine entry point.
 *
 * <p>This class is the <em>engine</em>. It no longer contains any build logic
 * itself. Instead, it delegates entirely to {@link ScriptRunner}, which:
 * <ol>
 *   <li>Locates {@code build.java} in the working directory.</li>
 *   <li>Compiles it in-process via {@code javax.tools.JavaCompiler}.</li>
 *   <li>Loads the compiled class with {@code URLClassLoader}.</li>
 *   <li>Calls {@link BuildScript#build(Build)} to populate the build graph.</li>
 *   <li>Executes the requested step.</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   ./gradlew run                   # runs the default "install" step
 *   ./gradlew run --args="run"      # runs the "run" step
 *   ./gradlew run --args="--help"   # lists available steps
 * </pre>
 */
public class Main {

    public static void main(String[] args) {
        List<String> argList = Arrays.asList(args);
        String requestedStep = argList.isEmpty() ? "install" : argList.get(0);

        if ("--help".equals(requestedStep) || "-h".equals(requestedStep)) {
            printUsage();
            return;
        }

        // The engine looks for build.java in the current working directory.
        // When launched via `./gradlew run` from the project root, Gradle sets
        // the working directory to the buildente-app/ subproject folder, which
        // is exactly where we place build.java.
        Path scriptDir = Paths.get(System.getProperty("user.dir"));

        Build b = new Build(argList);

        try {
            ScriptRunner.run(scriptDir, b, requestedStep);
        } catch (ScriptRunner.BuildScriptException e) {
            System.err.println("[buildente] ERROR: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("[buildente] Caused by: " + e.getCause());
            }
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Buildente — a Java build system inspired by Zig's build system");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  ./gradlew run                  Run the default 'install' step");
        System.out.println("  ./gradlew run --args=\"run\"     Run the 'run' step");
        System.out.println("  ./gradlew run --args=\"--help\"  Show this help message");
        System.out.println();
        System.out.println("Place a build.java file in the project root to define your build.");
    }
}
