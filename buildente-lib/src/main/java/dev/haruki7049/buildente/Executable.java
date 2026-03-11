package dev.haruki7049.buildente;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A build step that compiles a Java {@link Module} into bytecode using {@code javac}.
 *
 * <p>Corresponds to {@code b.addExecutable(name, module)} in the Buildente API, mirroring {@code
 * std.Build.addExecutable} from the Zig build system.
 *
 * <p>At execution time this step calls {@link Module#resolveSourceFiles()} to discover all {@code
 * .java} files under the module's source directory recursively, then passes them all to a single
 * {@code javac} invocation. Compiling the entire source tree in one pass is necessary because Java
 * {@code import} statements reference packages (directories), not individual files — the compiler
 * must see all classes in a package simultaneously to resolve cross-file references.
 *
 * <p>Compiled {@code .class} files are placed under {@value #OUTPUT_DIR}.
 */
public class Executable extends Step {

  /** Directory where all compiled class files are placed. */
  public static final String OUTPUT_DIR = "build/classes";

  /**
   * The fully-qualified class name used as the entry point when the artifact is executed (e.g.
   * {@code "com.example.App"}).
   */
  private final String executableName;

  /**
   * The module that describes the source directory to compile and any extra {@code javac} flags.
   */
  private final Module module;

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /**
   * Creates an executable step backed by a {@link Module}.
   *
   * <p>This is the only non-deprecated constructor. Modules decouple the description of what to
   * compile (source directory, compiler flags) from the step that performs compilation.
   *
   * @param name the fully-qualified class name of the entry point (e.g. {@code "com.example.App"})
   * @param module the module describing the source directory to compile
   */
  public Executable(String name, Module module) {
    super("compile:" + name);
    this.executableName = name;
    this.module = module;
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /**
   * Returns the logical name (entry-point class name) of this executable. Used by {@link RunStep}
   * to construct the {@code java} command.
   *
   * @return the fully-qualified class name, e.g. {@code "com.example.App"}
   */
  public String getExecutableName() {
    return executableName;
  }

  /**
   * Returns the {@link Module} that describes the compilation unit backing this executable.
   *
   * @return the module, never {@code null}
   */
  public Module getModule() {
    return module;
  }

  // -------------------------------------------------------------------------
  // Step implementation
  // -------------------------------------------------------------------------

  /**
   * Invokes {@code javac} via {@link ProcessBuilder} to compile all {@code .java} files found under
   * the module's source directory. Output is placed in {@value #OUTPUT_DIR}.
   *
   * <p>The compilation command is assembled as:
   *
   * <pre>
   *   javac [extraArgs...] -d OUTPUT_DIR file1.java file2.java ...
   * </pre>
   *
   * <p>All source files are passed in a single {@code javac} invocation so that cross-package
   * references within the same module are resolved correctly.
   *
   * @throws RuntimeException if source discovery, compilation, or process handling fails
   */
  @Override
  protected void execute() {
    String sourceDir = module.getSourceDir();
    System.out.println("[buildente] Compiling sources under " + sourceDir + " ...");

    // Ensure the output directory exists before invoking javac
    new File(OUTPUT_DIR).mkdirs();

    // Discover all .java files under the source directory at step-execution time
    List<String> sourceFiles = module.resolveSourceFiles();
    System.out.println("[buildente] Found " + sourceFiles.size() + " source file(s)");

    try {
      List<String> command = buildJavacCommand(sourceFiles);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.inheritIO();

      Process process = pb.start();
      int exitCode = process.waitFor();

      if (exitCode != 0) {
        throw new RuntimeException(
            "[buildente] javac exited with code " + exitCode + " for module: " + module);
      }

      System.out.println("[buildente] Compiled -> " + OUTPUT_DIR);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("[buildente] Compilation interrupted for module: " + module, e);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("[buildente] Failed to compile module: " + module, e);
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Assembles the full {@code javac} command from the module's configuration and the list of
   * discovered source files.
   *
   * <p>Command structure:
   *
   * <ol>
   *   <li>{@code javac}
   *   <li>Any extra args from {@link Module#getExtraArgs()}
   *   <li>{@code -d OUTPUT_DIR}
   *   <li>All discovered {@code .java} file paths
   * </ol>
   *
   * @param sourceFiles the {@code .java} files discovered by {@link Module#resolveSourceFiles()}
   * @return the fully assembled command list
   */
  private List<String> buildJavacCommand(List<String> sourceFiles) {
    List<String> command = new ArrayList<>();
    command.add("javac");

    // Extra compiler flags (e.g. -source 17, -encoding UTF-8)
    command.addAll(module.getExtraArgs());

    // Output directory
    command.add("-d");
    command.add(OUTPUT_DIR);

    // All source files from the directory tree
    command.addAll(sourceFiles);

    return command;
  }
}
