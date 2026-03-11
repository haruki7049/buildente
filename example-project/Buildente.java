import dev.haruki7049.buildente.Build;
import dev.haruki7049.buildente.BuildScript;
import dev.haruki7049.buildente.Executable;
import dev.haruki7049.buildente.JarStep;
import dev.haruki7049.buildente.ManifestConfig;
import dev.haruki7049.buildente.Module;
import dev.haruki7049.buildente.RunStep;

/**
 * Sample Buildente build script demonstrating JAR creation with a manifest attached to the
 * executable.
 *
 * <p>This file is the user-facing equivalent of a {@code build.zig} in the Zig build system. It is
 * NOT compiled by Gradle ahead of time — instead, the Buildente engine ({@code ScriptRunner})
 * compiles and loads it dynamically at runtime.
 *
 * <p>Available steps:
 *
 * <ul>
 *   <li>{@code install} (default) — compile and package a JAR; manifest is inherited from the
 *       executable declaration
 *   <li>{@code jar-override} — same executable, but a different manifest is passed explicitly to
 *       {@code addJar}, overriding the one attached to the executable
 *   <li>{@code run} — compile and execute the program directly (no JAR involved)
 * </ul>
 */
public class Buildente implements BuildScript {

  @Override
  public void build(Build b) {
    // 1. Create a Module pointing at the source directory.
    Module mod = b.createModule("src");

    // 2. Declare the manifest once, directly on the executable.
    //    Any JarStep that packages this executable will inherit it automatically
    //    — no need to pass the manifest again to addJar().
    ManifestConfig manifest =
        new ManifestConfig()
            .setMainClass("com.example.Main")
            .addAttribute("Built-By", "Buildente")
            .addAttribute("Implementation-Title", "Hello Example")
            .addAttribute("Implementation-Version", "1.0.0");

    Executable exe = b.addExecutable("com.example.Main", mod, manifest);

    // 3. JarStep inherits the manifest from exe — no manifest arg required here.
    JarStep jar = b.addJar("hello", exe);

    // 4. Explicit manifest passed to addJar() takes precedence over the one on exe.
    ManifestConfig overrideManifest =
        new ManifestConfig()
            .setMainClass("com.example.Main")
            .addAttribute("Implementation-Version", "1.0.0-debug");

    JarStep jarOverride = b.addJar("hello-debug", exe, overrideManifest);

    // 5. RunStep for direct execution (no JAR involved).
    RunStep run = b.addRunArtifact(exe);

    // 6. Wire steps to top-level targets.
    b.getInstallStep().dependOn(jar);
    b.step("jar-override", "Package JAR with an override manifest").dependOn(jarOverride);
    b.step("run", "Compile and run the Hello example").dependOn(run);
  }
}
