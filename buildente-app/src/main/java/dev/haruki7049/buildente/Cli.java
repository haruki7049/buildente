package dev.haruki7049.buildente;

import dev.haruki7049.buildente.runner.ScriptRunner;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/**
 * CLI entry point for the {@code bdt} command.
 *
 * <p>Parses command-line arguments via picocli and delegates execution to {@link ScriptRunner},
 * which locates, compiles, and runs the user's {@code Buildente.java} build script.
 *
 * <h2>Subcommands</h2>
 *
 * <ul>
 *   <li>(default) — compile and run a build step, e.g. {@code bdt install}, {@code bdt run}.
 *   <li>{@code update} — compute and write missing {@code sha256} entries in {@code
 *       deps.properties}.
 * </ul>
 */
@CommandLine.Command(
    name = "bdt",
    version = "0.1.0",
    mixinStandardHelpOptions = true,
    subcommands = {UpdateCommand.class},
    description = "Buildente — a Java build system inspired by Zig's build system")
public class Cli implements Callable<Integer> {

  /** Creates a new {@code Cli} instance with default settings. */
  public Cli() {}

  @CommandLine.Unmatched List<String> args = new ArrayList<>();

  /**
   * Executes the CLI command.
   *
   * <p>Resolves the requested build step from the first unmatched argument (defaults to {@code
   * "install"} when none is given), then invokes {@link ScriptRunner#run(Path, Build, String)}.
   *
   * @return {@code 0} on success, {@code 1} if the build script throws a {@link
   *     ScriptRunner.BuildScriptException}
   * @throws Exception if an unexpected error occurs during execution
   */
  @Override
  public Integer call() throws Exception {
    String requestedStep = args.isEmpty() ? "install" : args.get(0);
    Path scriptDir = Paths.get(System.getProperty("user.dir"));

    Build b = new Build(args);

    try {
      ScriptRunner.run(scriptDir, b, requestedStep);
    } catch (ScriptRunner.BuildScriptException e) {
      System.err.println("[buildente] ERROR: " + e.getMessage());

      if (e.getCause() != null) {
        System.err.println("[buildente] Caused by: " + e.getCause());
      }

      return 1;
    }

    return 0;
  }
}
