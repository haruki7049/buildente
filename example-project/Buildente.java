import dev.haruki7049.buildente.Build;
import dev.haruki7049.buildente.BuildScript;
import dev.haruki7049.buildente.Executable;
import dev.haruki7049.buildente.Module;
import dev.haruki7049.buildente.RunStep;

/**
 * Sample Buildente build script.
 *
 * <p>This file is the user-facing equivalent of a {@code build.zig} in the Zig build system. It is
 * NOT compiled by Gradle ahead of time — instead, the Buildente engine ({@code ScriptRunner})
 * compiles and loads it dynamically at runtime using {@code javax.tools.JavaCompiler} and {@code
 * URLClassLoader}.
 *
 * <p>Rules:
 * <ul>
 *   <li>The file must be named exactly {@code Buildente.java}.
 *   <li>The public class inside must be named exactly {@code Buildente}.
 *   <li>The class must implement {@link BuildScript}.
 *   <li>Only the {@code build(Build b)} method needs to be implemented.
 * </ul>
 */
public class Buildente implements BuildScript {

  @Override
  public void build(Build b) {
    // 1. Create a Module by pointing at the source directory.
    //    All .java files under "src/" are discovered recursively and compiled
    //    together in a single javac invocation — matching how Java's import
    //    system works: packages reference directories, not individual files.
    Module mod = b.createModule("src");

    // Optional: forward extra javac flags through the module.
    // mod.addExtraArg("-source").addExtraArg("17");

    // 2. Declare an executable backed by the module.
    //    Mirrors b.addExecutable(.{ .name = "...", .root_module = mod }) in Zig.
    Executable exe = b.addExecutable("com.example.Main", mod);

    // 3. Declare a run step that executes the compiled program.
    //    RunStep automatically depends on exe, so compilation always happens first.
    RunStep run = b.addRunArtifact(exe);

    // 4. Wire steps to top-level targets.
    //    "install" (default) -> compile only
    b.getInstallStep().dependOn(exe);

    //    "run" -> compile then execute
    b.step("run", "Compile and run the Hello example").dependOn(run);
  }
}
