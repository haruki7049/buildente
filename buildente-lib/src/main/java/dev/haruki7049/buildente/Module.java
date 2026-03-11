package dev.haruki7049.buildente;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * A compilation unit in the Buildente build system.
 *
 * <p>Mirrors {@code std.Build.Module} from the Zig build system. A {@code Module} encapsulates
 * everything needed to compile a Java source tree:
 *
 * <ul>
 *   <li>A <strong>source directory</strong> whose {@code .java} files are discovered recursively
 *       and compiled together. This reflects how Java itself works: {@code import} statements
 *       reference packages (directories), not individual files, so the natural unit of compilation
 *       is a source root rather than a single file.
 *   <li>Extra {@code javac} options (e.g. {@code -source 17}, annotation-processor flags).
 * </ul>
 *
 * <p>Modules are created via {@link Build#createModule(String)} and then passed to {@link
 * Build#addExecutable(String, Module)} or a future {@code addLibrary} call. This separates the
 * <em>description of what to compile</em> from the <em>step that performs compilation</em>.
 *
 * <p>Example — single source directory:
 *
 * <pre>{@code
 * Module mod = b.createModule("src");
 * Executable exe = b.addExecutable("com.example.App", mod);
 * RunStep   run = b.addRunArtifact(exe);
 * }</pre>
 *
 * <p>Example — with extra compiler flags:
 *
 * <pre>{@code
 * Module mod = b.createModule("src");
 * mod.addExtraArg("-source").addExtraArg("17");
 * Executable exe = b.addExecutable("com.example.App", mod);
 * }</pre>
 */
public final class Module {

  /**
   * Path to the source directory whose {@code .java} files are compiled recursively (e.g. {@code
   * "src"} or {@code "src/main/java"}). Relative to the working directory in which the build is
   * executed.
   */
  private final String sourceDir;

  /**
   * Extra command-line options forwarded verbatim to {@code javac} (e.g. {@code "-source"}, {@code
   * "17"}). May be empty; never null.
   */
  private final List<String> extraArgs = new ArrayList<>();

  // -------------------------------------------------------------------------
  // Constructor (package-private — use Build#createModule to obtain instances)
  // -------------------------------------------------------------------------

  /**
   * Creates a module rooted at {@code sourceDir}.
   *
   * <p>Package-private: callers should use {@link Build#createModule(String)} rather than
   * constructing this directly, keeping the API consistent with {@code std.Build.createModule} in
   * Zig.
   *
   * @param sourceDir path to the source directory containing {@code .java} files, relative to the
   *     working directory
   * @throws IllegalArgumentException if {@code sourceDir} is null or blank
   */
  Module(String sourceDir) {
    if (sourceDir == null || sourceDir.isBlank()) {
      throw new IllegalArgumentException("sourceDir must not be null or blank");
    }
    this.sourceDir = sourceDir;
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /**
   * Returns the source directory path supplied at construction time.
   *
   * @return the path to the source directory (e.g. {@code "src"})
   */
  public String getSourceDir() {
    return sourceDir;
  }

  /**
   * Discovers all {@code .java} files under {@link #sourceDir} recursively and returns their paths
   * as strings.
   *
   * <p>This is called by {@link Executable} at step-execution time rather than at module-creation
   * time, so that files added to the directory after the module is declared are still picked up.
   *
   * @return an unmodifiable list of absolute or relative {@code .java} file paths; empty if none
   *     are found
   * @throws RuntimeException if the directory cannot be walked
   */
  public List<String> resolveSourceFiles() {
    Path root = Paths.get(sourceDir);
    if (!root.toFile().isDirectory()) {
      throw new IllegalStateException(
          "[buildente] Source directory does not exist or is not a directory: "
              + root.toAbsolutePath());
    }

    List<String> javaFiles = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(p -> p.toString().endsWith(".java"))
          .map(Path::toString)
          .sorted() // deterministic order
          .forEach(javaFiles::add);
    } catch (Exception e) {
      throw new RuntimeException(
          "[buildente] Failed to walk source directory: " + root.toAbsolutePath(), e);
    }

    if (javaFiles.isEmpty()) {
      throw new IllegalStateException(
          "[buildente] No .java files found under source directory: " + root.toAbsolutePath());
    }

    return Collections.unmodifiableList(javaFiles);
  }

  /**
   * Returns an unmodifiable view of the extra {@code javac} arguments registered via {@link
   * #addExtraArg(String)}.
   *
   * @return extra compiler arguments, possibly empty
   */
  public List<String> getExtraArgs() {
    return Collections.unmodifiableList(extraArgs);
  }

  // -------------------------------------------------------------------------
  // Builder-style mutators
  // -------------------------------------------------------------------------

  /**
   * Appends a single extra argument forwarded verbatim to {@code javac}. Call once per token, e.g.:
   *
   * <pre>{@code
   * mod.addExtraArg("-source").addExtraArg("17");
   * }</pre>
   *
   * @param arg a single compiler flag or value
   * @return {@code this}, for method chaining
   */
  public Module addExtraArg(String arg) {
    if (arg != null && !arg.isBlank()) {
      extraArgs.add(arg);
    }
    return this;
  }

  // -------------------------------------------------------------------------
  // Object overrides
  // -------------------------------------------------------------------------

  /**
   * Returns a human-readable summary of this module.
   *
   * @return string in the form {@code Module{sourceDir=<path>}}
   */
  @Override
  public String toString() {
    return "Module{sourceDir=" + sourceDir + "}";
  }
}
