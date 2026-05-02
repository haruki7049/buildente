package dev.haruki7049.buildente;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A build step that compiles a Java {@link Module} into bytecode using {@code javac}.
 *
 * <p>Corresponds to {@code b.addExecutable(name, module)} in the Buildente API, mirroring {@code
 * std.Build.addExecutable} from the Zig build system.
 *
 * <p>At execution time this step calls {@link Module#resolveSourceFiles()} to discover all {@code
 * .java} files under the module's source directory recursively, then passes them all to a single
 * {@code javac} invocation.
 *
 * <p>Compiled {@code .class} files are placed under {@value #OUTPUT_DIR}.
 *
 * <p>If the module declares dependencies via {@link Module#addDependency(String)}, their resolved
 * JAR paths (from {@code deps.properties}) are forwarded to {@code javac} via {@code -classpath}.
 */
public class Executable extends Step {

  private static final Logger LOGGER = Logger.getLogger(Executable.class.getName());

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

  /**
   * Optional manifest configuration that {@link JarStep} inherits automatically when no manifest is
   * supplied explicitly to {@link Build#addJar}. May be {@code null}.
   */
  private final ManifestConfig manifestConfig;

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /**
   * Creates an executable step backed by a {@link Module}, with no manifest attached.
   *
   * @param name the fully-qualified class name of the entry point
   * @param module the module describing the source directory to compile
   */
  public Executable(String name, Module module) {
    this(name, module, null);
  }

  /**
   * Creates an executable step backed by a {@link Module} with an attached {@link ManifestConfig}.
   *
   * @param name the fully-qualified class name of the entry point
   * @param module the module describing the source directory to compile
   * @param manifestConfig the manifest configuration to embed when packaging a JAR, or {@code null}
   */
  public Executable(String name, Module module, ManifestConfig manifestConfig) {
    super("compile:" + name);
    this.executableName = name;
    this.module = module;
    this.manifestConfig = manifestConfig;
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /**
   * Returns the logical name (entry-point class name) of this executable.
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

  /**
   * Returns the {@link ManifestConfig} attached to this executable, if any.
   *
   * @return the manifest config, or {@code null} if none was supplied
   */
  public ManifestConfig getManifestConfig() {
    return manifestConfig;
  }

  // -------------------------------------------------------------------------
  // Step implementation
  // -------------------------------------------------------------------------

  /**
   * Invokes {@code javac} via {@link ProcessBuilder} to compile all {@code .java} files found under
   * the module's source directory. Output is placed in {@value #OUTPUT_DIR}.
   *
   * <p>Command structure:
   *
   * <pre>
   *   javac [extraArgs...] [-classpath dep1.jar:dep2.jar] -d OUTPUT_DIR file1.java ...
   * </pre>
   *
   * @throws RuntimeException if source discovery, compilation, or process handling fails
   */
  @Override
  protected void execute() {
    String sourceDir = module.getSourceDir();
    LOGGER.info("Compiling sources under " + sourceDir + " ...");

    new File(OUTPUT_DIR).mkdirs();

    List<String> sourceFiles = module.resolveSourceFiles();
    LOGGER.info("Found " + sourceFiles.size() + " source file(s)");

    try {
      List<String> command = buildJavacCommand(sourceFiles);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.inheritIO();

      Process process = pb.start();
      int exitCode = process.waitFor();

      if (exitCode != 0) {
        throw new RuntimeException(
            "javac exited with code " + exitCode + " for module: " + module);
      }

      LOGGER.info("Compiled -> " + OUTPUT_DIR);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Compilation interrupted for module: " + module, e);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to compile module: " + module, e);
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Assembles the full {@code javac} command.
   *
   * <ol>
   *   <li>{@code javac}
   *   <li>Extra args from {@link Module#getExtraArgs()}
   *   <li>{@code -classpath dep1.jar:dep2.jar} (only if dependencies are declared)
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

    command.addAll(module.getExtraArgs());

    List<Path> depJars = module.getResolvedJars();
    if (!depJars.isEmpty()) {
      command.add("-classpath");
      command.add(
          depJars.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
    }

    command.add("-d");
    command.add(OUTPUT_DIR);

    command.addAll(sourceFiles);

    return command;
  }
}
