package dev.haruki7049.buildente;

import java.util.List;
import java.util.ArrayList;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(name = "bdt", version = "0.1.0", mixinStandardHelpOptions = true, description = "Buildente — a Java build system inspired by Zig's build system")
public class Cli implements Callable<Integer> {

  @CommandLine.Unmatched
  List<String> args = new ArrayList<>();

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

  private enum SubCommand {
    run,
  }
}
