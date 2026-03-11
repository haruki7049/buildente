import dev.haruki7049.buildente.Build;
import dev.haruki7049.buildente.BuildScript;
import dev.haruki7049.buildente.Executable;
import dev.haruki7049.buildente.FatJarStep;
import dev.haruki7049.buildente.ManifestConfig;
import dev.haruki7049.buildente.Module;
import dev.haruki7049.buildente.RunStep;

/**
 * Sample Buildente build script demonstrating fat-JAR creation with external Maven dependencies.
 *
 * <p>Dependencies are declared in {@code deps.properties}. Run {@code bdt update} once after
 * adding or changing entries to compute and record {@code sha256} hashes.
 *
 * <p>Available steps:
 *
 * <ul>
 *   <li>{@code install} (default) — produce a self-contained fat JAR
 *   <li>{@code run} — compile and execute the program directly
 * </ul>
 */
public class Buildente implements BuildScript {

  @Override
  public void build(Build b) {
    // 1. Create a Module pointing at the source directory.
    Module mod = b.createModule("src");

    // 2. Declare which deps.properties aliases this module needs.
    //    Their JARs will be merged into the fat JAR automatically.
    mod.addDependency("buildente");

    // 3. Attach a manifest so the fat JAR is directly executable with java -jar.
    ManifestConfig manifest =
        new ManifestConfig()
            .setMainClass("com.example.Main")
            .addAttribute("Built-By", "Buildente")
            .addAttribute("Implementation-Title", "Hello Example")
            .addAttribute("Implementation-Version", "1.0.0");

    Executable exe = b.addExecutable("com.example.Main", mod, manifest);

    // 4. Package as a fat JAR — compiled classes + dependency JARs merged into one archive.
    FatJarStep fat = b.addFatJar("hello", exe);

    // 5. RunStep for direct execution (no JAR involved).
    RunStep run = b.addRunArtifact(exe);

    // 6. Wire to top-level steps.
    b.getInstallStep().dependOn(fat);
    b.step("run", "Compile and run the Hello example").dependOn(run);
  }
}
