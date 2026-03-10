import dev.haruki7049.buildente.Build;
import dev.haruki7049.buildente.BuildScript;
import dev.haruki7049.buildente.Executable;
import dev.haruki7049.buildente.RunStep;

/**
 * Sample Buildente build script.
 *
 * This file is the user-facing equivalent of a {@code build.zig} in the Zig
 * build system. It is NOT compiled by Gradle ahead of time — instead, the
 * Buildente engine (ScriptRunner) compiles and loads it dynamically at runtime
 * using {@code javax.tools.JavaCompiler} and {@code URLClassLoader}.
 *
 * Rules:
 *   - The file must be named exactly "build.java".
 *   - The public class inside must be named exactly "build" (lowercase).
 *   - The class must implement {@link BuildScript}.
 *   - Only the {@code build(Build b)} method needs to be implemented.
 */
public class Buildente implements BuildScript {

    @Override
    public void build(Build b) {
        // 1. Declare a compilable Java program.
        //    Path is relative to this file's directory (buildente-app/).
        Executable exe = b.addExecutable(
            "dev.haruki7049.buildente.example.Hello",
            "examples/Hello.java"
        );

        // 2. Declare a run step that executes the compiled program.
        //    RunStep automatically depends on exe, so compilation always
        //    happens first.
        RunStep run = b.addRunArtifact(exe);

        // 3. Wire steps to top-level targets.
        //    "install" (default) -> compile only
        b.getInstallStep().dependOn(exe);

        //    "run" -> compile then execute
        b.step("run", "Compile and run the Hello example").dependOn(run);
    }
}
