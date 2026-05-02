package dev.haruki7049.buildente;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A compilation unit in the Buildente build system.
 *
 * <p>Mirrors {@code std.Build.Module} from the Zig build system. A {@code Module} encapsulates
 * everything needed to compile a Java source tree:
 *
 * <ul>
 *   <li>A <strong>source directory</strong> whose {@code .java} files are discovered recursively
 *       and compiled together.
 *   <li>Extra {@code javac} options (e.g. {@code -source 17}, annotation-processor flags).
 *   <li><strong>Dependencies</strong> declared by alias (e.g. {@code "guava"}), resolved at build
 *       time from {@code deps.properties} to local JAR paths and forwarded to {@code javac} via
 *       {@code -classpath}.
 * </ul>
 *
 * <p>Modules are created via {@link Build#createModule(String)}.
 *
 * <p>Example — declaring dependencies:
 *
 * <pre>{@code
 * Module mod = b.createModule("src");
 * mod.addDependency("guava");      // alias from deps.properties
 * mod.addDependency("slf4j-api");
 * Executable exe = b.addExecutable("com.example.App", mod);
 * }</pre>
 */
public final class Module {

  /**
   * Path to the source directory whose {@code .java} files are compiled recursively (e.g. {@code
   * "src"} or {@code "src/main/java"}). Relative to the working directory.
   */
  private final String sourceDir;

  /** Extra command-line options forwarded verbatim to {@code javac}. May be empty; never null. */
  private final List<String> extraArgs = new ArrayList<>();

  /**
   * Dependency aliases declared via {@link #addDependency(String)}, resolved to local JAR paths at
   * build time via {@link #resolvedJarMap}.
   */
  private final List<String> dependencyNames = new ArrayList<>();

  /**
   * Map from alias to cached JAR path, injected by {@link Build} from the results of {@link
   * DependencyFetcher#fetchAll(DepsProperties)}. Empty when there is no {@code deps.properties}.
   */
  private final Map<String, Path> resolvedJarMap;

  // -------------------------------------------------------------------------
  // Constructors (package-private — use Build#createModule to obtain instances)
  // -------------------------------------------------------------------------

  /**
   * Creates a module rooted at {@code sourceDir} with no pre-resolved jars.
   *
   * @param sourceDir path to the source directory containing {@code .java} files
   */
  Module(String sourceDir) {
    this(sourceDir, Collections.emptyMap());
  }

  /**
   * Creates a module rooted at {@code sourceDir} with a pre-populated jar map supplied by {@link
   * Build}.
   *
   * @param sourceDir path to the source directory containing {@code .java} files
   * @param resolvedJarMap live map from alias → cached JAR {@link Path}
   */
  Module(String sourceDir, Map<String, Path> resolvedJarMap) {
    if (sourceDir == null || sourceDir.isBlank()) {
      throw new IllegalArgumentException("sourceDir must not be null or blank");
    }
    this.sourceDir = sourceDir;
    this.resolvedJarMap = resolvedJarMap;
  }

  // -------------------------------------------------------------------------
  // Dependency declaration
  // -------------------------------------------------------------------------

  /**
   * Declares that this module requires the dependency identified by {@code alias}.
   *
   * <p>{@code alias} must match a key in {@code deps.properties}. At compilation time the
   * corresponding JAR is added to the {@code javac -classpath}. At runtime the same JAR is included
   * in the {@code java -cp} list.
   *
   * @param alias the dependency alias as declared in {@code deps.properties}
   * @return {@code this}, for method chaining
   */
  public Module addDependency(String alias) {
    if (alias != null && !alias.isBlank()) {
      dependencyNames.add(alias);
    }
    return this;
  }

  /**
   * Returns the resolved local JAR paths for all dependencies declared via {@link
   * #addDependency(String)}.
   *
   * @return list of absolute paths to cached dependency JARs, in declaration order
   * @throws IllegalStateException if any declared alias is absent from the resolved jar map
   */
  public List<Path> getResolvedJars() {
    List<Path> jars = new ArrayList<>();
    for (String alias : dependencyNames) {
      Path jar = resolvedJarMap.get(alias);
      if (jar == null) {
        throw new IllegalStateException(
            "Dependency '"
                + alias
                + "' is not in deps.properties or has no sha256.\n"
                + "  Run 'bdt update' to populate it.");
      }
      jars.add(jar);
    }
    return Collections.unmodifiableList(jars);
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
   * @return an unmodifiable list of {@code .java} file paths; empty if none are found
   * @throws RuntimeException if the directory cannot be walked
   */
  public List<String> resolveSourceFiles() {
    Path root = Paths.get(sourceDir);
    if (!root.toFile().isDirectory()) {
      throw new IllegalStateException(
          "Source directory does not exist or is not a directory: "
              + root.toAbsolutePath());
    }

    List<String> javaFiles = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(p -> p.toString().endsWith(".java"))
          .map(Path::toString)
          .sorted()
          .forEach(javaFiles::add);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to walk source directory: " + root.toAbsolutePath(), e);
    }

    if (javaFiles.isEmpty()) {
      throw new IllegalStateException(
          "No .java files found under source directory: " + root.toAbsolutePath());
    }

    return Collections.unmodifiableList(javaFiles);
  }

  /**
   * Returns an unmodifiable view of the extra {@code javac} arguments.
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
   * Appends a single extra argument forwarded verbatim to {@code javac}. Call once per token:
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

  @Override
  public String toString() {
    return "Module{sourceDir=" + sourceDir + "}";
  }
}
