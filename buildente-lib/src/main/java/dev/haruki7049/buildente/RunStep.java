package dev.haruki7049.buildente;

import java.util.ArrayList;
import java.util.List;

/**
 * A build step that runs a previously compiled {@link Executable}.
 *
 * <p>Corresponds to {@code b.addRunArtifact(exe)} in the Buildente API, mirroring {@code
 * std.Build.addRunArtifact} from the Zig build system.
 *
 * <p>This step automatically depends on the given {@link Executable}, so the source will always be
 * compiled before it is run.
 */
public class RunStep extends Step {

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
    // Compilation must always precede execution
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
   * Invokes {@code java} via {@link ProcessBuilder} to run the compiled class. The classpath is set
   * to {@link Executable#OUTPUT_DIR}.
   *
   * @throws RuntimeException if execution fails or the process is interrupted
   */
  @Override
  protected void execute() {
    String className = executable.getExecutableName();
    System.out.println("[buildente] Running " + className + " ...");

    try {
      List<String> command = new ArrayList<>();
      command.add("java");
      command.add("-cp");
      command.add(Executable.OUTPUT_DIR);
      command.add(className);
      command.addAll(runArgs);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.inheritIO();

      Process process = pb.start();
      int exitCode = process.waitFor();

      if (exitCode != 0) {
        throw new RuntimeException(
            "[buildente] java exited with code " + exitCode + " for class: " + className);
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("[buildente] Execution interrupted: " + className, e);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("[buildente] Failed to run: " + className, e);
    }
  }
}
