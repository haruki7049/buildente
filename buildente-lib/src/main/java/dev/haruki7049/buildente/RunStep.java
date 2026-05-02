package dev.haruki7049.buildente;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A build step that runs a previously compiled {@link Executable}.
 *
 * <p>Corresponds to {@code b.addRunArtifact(exe)} in the Buildente API, mirroring {@code
 * std.Build.addRunArtifact} from the Zig build system.
 *
 * <p>This step automatically depends on the given {@link Executable}, so the source will always be
 * compiled before it is run.
 *
 * <p>If the executable's module declares dependencies via {@link Module#addDependency(String)},
 * their JAR paths (resolved from {@code deps.properties}) are appended to the {@code java -cp}
 * argument so that the program can load them at runtime.
 */
public class RunStep extends Step {

  private static final Logger LOGGER = Logger.getLogger(RunStep.class.getName());

  /** The compilation step whose output this step will execute. */
  private final Executable executable;

  /** Optional arguments forwarded to the program at runtime. */
  private final List<String> runArgs = new ArrayList<>();

  /**
   * Creates a run step for the given executable. Automatically adds {@code executable} as a
   * dependency.
   *
   * @param executable the compiled artifact to run
   */
  public RunStep(Executable executable) {
    super("run:" + executable.getExecutableName());
    this.executable = executable;
    this.dependOn(executable);
  }

  /**
   * Appends runtime arguments that are passed to the program on the command line.
   *
   * @param newArgs arguments to forward to the JVM process
   */
  public void addArgs(List<String> newArgs) {
    this.runArgs.addAll(newArgs);
  }

  /**
   * Invokes {@code java} via {@link ProcessBuilder} to run the compiled class.
   *
   * <p>The classpath is composed of:
   *
   * <ol>
   *   <li>{@link Executable#OUTPUT_DIR} — the compiled class files.
   *   <li>Any dependency JARs declared on the module via {@link Module#addDependency(String)}.
   * </ol>
   *
   * @throws RuntimeException if execution fails or the process is interrupted
   */
  @Override
  protected void execute() {
    String className = executable.getExecutableName();
    LOGGER.info("Running " + className + " ...");

    try {
      List<String> command = new ArrayList<>();
      command.add("java");
      command.add("-cp");
      command.add(buildClasspath());
      command.add(className);
      command.addAll(runArgs);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.inheritIO();

      Process process = pb.start();
      int exitCode = process.waitFor();

      if (exitCode != 0) {
        throw new RuntimeException(
            "java exited with code " + exitCode + " for class: " + className);
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Execution interrupted: " + className, e);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to run: " + className, e);
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Builds the {@code -cp} value: {@link Executable#OUTPUT_DIR} followed by any resolved dependency
   * JARs, joined with the platform path separator.
   *
   * @return the classpath string
   */
  private String buildClasspath() {
    List<String> entries = new ArrayList<>();
    entries.add(Executable.OUTPUT_DIR);

    for (Path jar : executable.getModule().getResolvedJars()) {
      entries.add(jar.toString());
    }

    return String.join(File.pathSeparator, entries);
  }
}
