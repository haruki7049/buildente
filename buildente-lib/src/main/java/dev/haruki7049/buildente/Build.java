package dev.haruki7049.buildente;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Central configuration object for a Buildente build.
 *
 * <p>Build scripts receive an instance of this class and use it to declare executables, run steps,
 * and named steps. The overall design mirrors {@code std.Build} from the Zig build system.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Build b = new Build(Arrays.asList(args));
 *
 * Module     mod = b.createModule("src");
 * Executable exe = b.addExecutable("Hello", mod);
 * RunStep    run = b.addRunArtifact(exe);
 *
 * b.step("run", "Compile and run Hello").dependOn(run);
 * b.getInstallStep().dependOn(exe);
 *
 * b.executeStep(args.length > 0 ? args[0] : "install");
 * }</pre>
 *
 * <p>When {@code deps.properties} is present and has been updated via {@code bdt update}, {@code
 * ScriptRunner} calls {@link #setResolvedJars(Map)} before invoking the user script. Every {@link
 * Module} created afterward via {@link #createModule(String)} automatically inherits the resolved
 * jar map so that {@link Module#addDependency(String)} can look up JAR paths at compile time.
 */
public class Build {

  private static final Logger LOGGER = Logger.getLogger(Build.class.getName());

  /**
   * Registry of all named top-level steps, keyed by step name. Uses LinkedHashMap to preserve
   * insertion order for help output.
   */
  private final Map<String, Step> steps = new LinkedHashMap<>();

  /** The default step executed when no step name is supplied. */
  private final Step defaultStep;

  /** Raw command-line arguments passed to the build process. */
  private final List<String> args;

  /**
   * Map from dependency alias to cached JAR {@link Path}, populated by {@code ScriptRunner} from
   * the contents of {@code deps.properties}. Empty when no {@code deps.properties} is present or it
   * contains no entries.
   */
  private Map<String, Path> resolvedJars = Collections.emptyMap();

  /**
   * Creates a Build instance.
   *
   * @param args command-line arguments (first element is typically the step name)
   */
  public Build(List<String> args) {
    this.args = args;
    this.defaultStep = new NamedStep("install", "Copy build artifacts to the output prefix");
    this.steps.put("install", this.defaultStep);
  }

  // -------------------------------------------------------------------------
  // Dependency injection (called by ScriptRunner before user script runs)
  // -------------------------------------------------------------------------

  /**
   * Injects the resolved dependency JAR map, populated from {@code deps.properties} by {@code
   * ScriptRunner}.
   *
   * <p>This method must be called <em>before</em> the user script's {@link
   * BuildScript#build(Build)} runs, so that any {@link Module} created by the script inherits the
   * map automatically.
   *
   * @param jars map from alias (e.g. {@code "guava"}) to absolute JAR {@link Path}
   */
  public void setResolvedJars(Map<String, Path> jars) {
    this.resolvedJars =
        jars != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(jars))
            : Collections.emptyMap();
  }

  // -------------------------------------------------------------------------
  // Factory methods (mirrors the Zig build API)
  // -------------------------------------------------------------------------

  /**
   * Creates a {@link Module} backed by the given source directory.
   *
   * <p>The module automatically receives the current {@link #resolvedJars} map, so that calls to
   * {@link Module#addDependency(String)} on the returned module are backed by the data from {@code
   * deps.properties}.
   *
   * <p>Mirrors {@code b.createModule(.{ .root_source_file = b.path("src/main.zig") })} in Zig,
   * adapted for Java's package-centric model.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Module mod = b.createModule("src");
   * mod.addDependency("guava");
   * mod.addExtraArg("-source").addExtraArg("17");
   * Executable exe = b.addExecutable("com.example.App", mod);
   * }</pre>
   *
   * @param sourceDir path to the source directory containing {@code .java} files, relative to the
   *     working directory (e.g. {@code "src"} or {@code "src/main/java"})
   * @return a new, mutable {@link Module} instance backed by the current resolved-jars map
   */
  public Module createModule(String sourceDir) {
    return new Module(sourceDir, resolvedJars);
  }

  /**
   * Creates a compilation step for the given {@link Module}.
   *
   * <p>Mirrors {@code b.addExecutable(.{ .name = name, .root_module = module })} in Zig.
   *
   * @param name the fully-qualified entry-point class name (e.g. {@code "com.example.App"})
   * @param module the module describing the source directory and compiler flags
   * @return a new {@link Executable} step (not yet wired to any top-level step)
   */
  public Executable addExecutable(String name, Module module) {
    return new Executable(name, module);
  }

  /**
   * Creates a compilation step for the given {@link Module} with an attached {@link
   * ManifestConfig}.
   *
   * <p>The manifest is not used during compilation. It is carried on the returned {@link
   * Executable} so that a subsequent {@link #addJar(String, Executable)} call can inherit it
   * automatically:
   *
   * <pre>{@code
   * ManifestConfig mf = new ManifestConfig()
   *     .setMainClass("com.example.Main")
   *     .addAttribute("Built-By", "Buildente");
   *
   * Executable exe = b.addExecutable("com.example.Main", mod, mf);
   * JarStep jar = b.addJar("myapp", exe);
   * b.getInstallStep().dependOn(jar);
   * }</pre>
   *
   * @param name the fully-qualified entry-point class name (e.g. {@code "com.example.App"})
   * @param module the module describing the source directory and compiler flags
   * @param manifest the manifest configuration to embed when packaging a JAR
   * @return a new {@link Executable} step carrying {@code manifest}
   */
  public Executable addExecutable(String name, Module module, ManifestConfig manifest) {
    return new Executable(name, module, manifest);
  }

  /**
   * Creates a run step that executes a compiled artifact. Mirrors {@code b.addRunArtifact(exe)} in
   * Zig.
   *
   * @param exe the executable to run
   * @return a new {@link RunStep} that automatically depends on {@code exe}
   */
  public RunStep addRunArtifact(Executable exe) {
    return new RunStep(exe);
  }

  /**
   * Creates a JAR packaging step with no custom manifest.
   *
   * @param jarName the base name of the output JAR (without {@code .jar} extension)
   * @param exe the compilation step whose output is packaged
   * @return a new {@link JarStep} that automatically depends on {@code exe}
   */
  public JarStep addJar(String jarName, Executable exe) {
    return new JarStep(jarName, exe);
  }

  /**
   * Creates a JAR packaging step with a programmatic manifest defined by a {@link ManifestConfig}.
   *
   * @param jarName the base name of the output JAR (without {@code .jar} extension)
   * @param exe the compilation step whose output is packaged
   * @param manifest manifest attributes to embed in the JAR
   * @return a new {@link JarStep} that automatically depends on {@code exe}
   */
  public JarStep addJar(String jarName, Executable exe, ManifestConfig manifest) {
    return new JarStep(jarName, exe, manifest);
  }

  /**
   * Creates a JAR packaging step that forwards an existing {@code MANIFEST.MF} file to the {@code
   * jar} tool.
   *
   * @param jarName the base name of the output JAR (without {@code .jar} extension)
   * @param exe the compilation step whose output is packaged
   * @param manifestFilePath path to an existing {@code MANIFEST.MF} file, relative to the working
   *     directory
   * @return a new {@link JarStep} that automatically depends on {@code exe}
   */
  public JarStep addJarWithManifestFile(String jarName, Executable exe, String manifestFilePath) {
    return new JarStep(jarName, exe, manifestFilePath);
  }

  /**
   * Creates a fat-JAR (uber-JAR) packaging step that merges the compiled class files and all
   * dependency JARs declared on the module into a single self-contained archive.
   *
   * <p>The produced JAR can be executed directly with {@code java -jar} without any external
   * classpath configuration. The {@link ManifestConfig} attached to {@code exe} is used as the
   * manifest; set {@code Main-Class} on it to make the fat JAR executable.
   *
   * <pre>{@code
   * ManifestConfig mf = new ManifestConfig().setMainClass("com.example.Main");
   * Executable exe = b.addExecutable("com.example.Main", mod, mf);
   *
   * FatJarStep fat = b.addFatJar("myapp", exe);
   * b.getInstallStep().dependOn(fat);
   * }</pre>
   *
   * @param jarName the base name of the output JAR (without {@code .jar} extension)
   * @param exe the compilation step whose output is packaged together with its dependencies
   * @return a new {@link FatJarStep} that automatically depends on {@code exe}
   */
  public FatJarStep addFatJar(String jarName, Executable exe) {
    return new FatJarStep(jarName, exe);
  }

  /**
   * Returns (or lazily creates) a named top-level step. Mirrors {@code b.step(name, description)}
   * in Zig.
   *
   * @param name the step name, e.g. {@code "run"}
   * @param description short description shown in help output
   * @return the existing or newly created step
   */
  public Step step(String name, String description) {
    return this.steps.computeIfAbsent(name, k -> new NamedStep(k, description));
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /**
   * Returns the default {@code install} step.
   *
   * @return the install step
   */
  public Step getInstallStep() {
    return this.defaultStep;
  }

  /**
   * Returns the raw command-line arguments supplied to this build.
   *
   * @return unmodifiable view of the argument list
   */
  public List<String> getArgs() {
    return this.args;
  }

  // -------------------------------------------------------------------------
  // Execution
  // -------------------------------------------------------------------------

  /**
   * Executes the named top-level step and all of its transitive dependencies.
   *
   * @param stepName the name of the step to execute
   */
  public void executeStep(String stepName) {
    Step target = steps.get(stepName);
    if (target == null) {
      LOGGER.severe("[buildente] Unknown step: '" + stepName + "'");
      printAvailableSteps();
      System.exit(1);
    }
    target.make();
  }

  /** Prints all registered top-level steps to standard output. */
  public void printAvailableSteps() {
    LOGGER.info("[buildente] Available steps:");
    for (Map.Entry<String, Step> entry : steps.entrySet()) {
      String desc = entry.getValue().description;
      if (desc != null && !desc.isEmpty()) {
        LOGGER.info(String.format("  %-16s %s", entry.getKey(), desc));
      } else {
        LOGGER.info("  " + entry.getKey());
      }
    }
  }

  // -------------------------------------------------------------------------
  // Internal step type for named (grouping) steps
  // -------------------------------------------------------------------------

  /**
   * A lightweight step that serves as a named anchor in the build graph. It performs no work
   * itself; its purpose is to aggregate dependencies (e.g. {@code install} or {@code run}).
   */
  private static final class NamedStep extends Step {

    NamedStep(String name, String description) {
      super(name, description);
    }

    @Override
    protected void execute() {
      // Named steps are pure aggregators; no work is done here.
    }
  }
}
