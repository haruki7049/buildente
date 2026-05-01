package dev.haruki7049.buildente;

import dev.haruki7049.buildente.deps.DepsProperties;
import dev.haruki7049.buildente.deps.Updater;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.logging.Logger;
import picocli.CommandLine;

/**
 * CLI subcommand: {@code bdt update}.
 *
 * <p>This class belongs to {@code buildente-app}. It is a thin picocli wrapper that delegates all
 * logic to {@link Updater} (in {@code buildente-lib}), prints the resulting log, and maps the
 * {@link Updater.Result} to an exit code.
 */
@CommandLine.Command(
    name = "update",
    description =
        "Compute and record sha256 hashes for any deps.properties entries that are missing them.")
public class UpdateCommand implements Callable<Integer> {

  private static final Logger LOGGER = Logger.getLogger(UpdateCommand.class.getName());

  /** Creates a new {@code UpdateCommand} instance with default settings. */
  public UpdateCommand() {}

  /**
   * Runs {@link Updater#run(java.nio.file.Path)} and maps the result to a process exit code.
   *
   * @return {@code 0} on success, {@code 1} on any failure
   */
  @Override
  public Integer call() {
    Updater.Result result = Updater.run(Paths.get(System.getProperty("user.dir")));

    // Log accumulated lines from the resolution phase
    for (String line : result.getLog().split("\n")) {
      if (!line.isEmpty()) {
        LOGGER.info(line);
      }
    }

    switch (result.getStatus()) {
      case NO_FILE:
        LOGGER.severe(
            "[buildente] No "
                + DepsProperties.FILE_NAME
                + " found in the current directory.\n"
                + "  Create one with <alias>.id and <alias>.repo entries,"
                + " then re-run 'bdt update'.");
        return 1;

      case READ_ERROR:
        LOGGER.severe(
            "[buildente] Failed to read "
                + DepsProperties.FILE_NAME
                + ": "
                + result.getCause().getMessage());
        return 1;

      case EMPTY:
        LOGGER.info(
            "[buildente] No entries found in " + DepsProperties.FILE_NAME + ". Nothing to update.");
        return 0;

      case FETCH_ERRORS:
        LOGGER.severe(
            "[buildente] "
                + result.getErrors()
                + " error(s) occurred. "
                + DepsProperties.FILE_NAME
                + " not written.");
        return 1;

      case NOTHING_TO_UPDATE:
        LOGGER.info("[buildente] All sha256 entries already present. Nothing to write.");
        return 0;

      case WRITE_ERROR:
        LOGGER.severe(
            "[buildente] Failed to write "
                + DepsProperties.FILE_NAME
                + ": "
                + result.getCause().getMessage());
        return 1;

      case SUCCESS:
        LOGGER.info("[buildente] Updated " + DepsProperties.FILE_NAME + ".");
        LOGGER.info(
            "[buildente] Commit " + DepsProperties.FILE_NAME + " to version control.");
        return 0;

      default:
        LOGGER.severe("[buildente] Unknown result status: " + result.getStatus());
        return 1;
    }
  }
}
