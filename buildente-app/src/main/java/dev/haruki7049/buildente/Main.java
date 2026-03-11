package dev.haruki7049.buildente;

import picocli.CommandLine;

/**
 * Main class which includes an entry point.
 *
 * <p>Instantiates the picocli {@link CommandLine} with {@link Cli} and forwards the JVM exit code
 * returned by the command execution.
 */
public class Main {

  /** Creates a new {@code Main} instance with default settings. */
  public Main() {}

  /**
   * Application entry point.
   *
   * @param args command-line arguments forwarded to picocli
   */
  public static void main(String[] args) {
    CommandLine cli = new CommandLine(new Cli());
    System.exit(cli.execute(args));
  }
}
