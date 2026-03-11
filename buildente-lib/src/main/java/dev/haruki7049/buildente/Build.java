package dev.haruki7049.buildente;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 */
public class Build {

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
  // Factory methods (mirrors the Zig build API)
  // -------------------------------------------------------------------------

  /**
   * Creates a {@link Module} backed by the given source directory.
   *
   * <p>Mirrors {@code b.createModule(.{ .root_source_file = b.path("src/main.zig") })} in Zig,
   * adapted for Java's package-centric model. Because Java {@code import} statements reference
   * packages (i.e. directory hierarchies) rather than individual files, the natural compilation
   * unit is a <em>source root directory</em>. At build time {@link Module#resolveSourceFiles()}
   * walks the directory recursively and passes every discovered {@code .java} file to a single
   * {@code javac} invocation, ensuring cross-package references within the module are resolved.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Module mod = b.createModule("src");
   * mod.addExtraArg("-source").addExtraArg("17");
   * Executable exe = b.addExecutable("com.example.App", mod);
   * }</pre>
   *
   * @param sourceDir path to the source directory containing {@code .java} files, relative to the
   *     working directory (e.g. {@code "src"} or {@code "src/main/java"})
   * @return a new, mutable {@link Module} instance
   */
  public Module createModule(String sourceDir) {
    return new Module(sourceDir);
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
   * Returns the default {@code install} step. Wire compilation or copy steps here to make them run
   * by default.
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
      System.err.println("[buildente] Unknown step: '" + stepName + "'");
      printAvailableSteps();
      System.exit(1);
    }
    target.make();
  }

  /**
   * Prints all registered top-level steps to standard output. Useful for implementing {@code
   * --help} or listing available steps.
   */
  public void printAvailableSteps() {
    System.out.println("[buildente] Available steps:");
    for (Map.Entry<String, Step> entry : steps.entrySet()) {
      String desc = entry.getValue().description;
      if (desc != null && !desc.isEmpty()) {
        System.out.printf("  %-16s %s%n", entry.getKey(), desc);
      } else {
        System.out.println("  " + entry.getKey());
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
