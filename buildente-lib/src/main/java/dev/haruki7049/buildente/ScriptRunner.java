package dev.haruki7049.buildente;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Locates, compiles, and executes a user-written {@code Buildente.java} script.
 *
 * <h2>How it works</h2>
 *
 * <ol>
 *   <li><strong>Locate</strong> – looks for {@code Buildente.java} in the directory supplied to
 *       {@link #run(Path, Build, String)}.
 *   <li><strong>Resolve dependencies</strong> – if {@code deps.properties} is present, all entries
 *       must already have a {@code sha256} value (written by {@code bdt update}). JARs are fetched
 *       from the content-addressed cache (downloading from Maven if cold) and injected into the
 *       {@link Build} instance via {@link Build#setResolvedJars(Map)}.
 *   <li><strong>Compile</strong> – invokes the external {@code javac} command via {@link
 *       ProcessBuilder}, forwarding the current JVM classpath so the script can import {@code
 *       dev.haruki7049.buildente.*}.
 *   <li><strong>Load</strong> – loads the compiled {@code Buildente} class from the {@code
 *       .buildente/} output directory inside the project root via {@link URLClassLoader}.
 *   <li><strong>Execute</strong> – casts the class to {@link BuildScript}, calls {@link
 *       BuildScript#build(Build)}, then runs the requested step.
 * </ol>
 *
 * <h2>Dependency resolution</h2>
 *
 * <p>All dependency metadata — JAR ID, repository URL, and SHA-256 hash — lives in the single file
 * {@code deps.properties}. There is no separate lock file. Run {@code bdt update} once after adding
 * or changing entries to compute and record the {@code sha256} values.
 *
 * <h2>Limitations (POC)</h2>
 *
 * <ul>
 *   <li>The script class must be named exactly {@code Buildente}.
 *   <li>Only a single source file is supported in this proof-of-concept.
 *   <li>{@code javac} must be available on {@code PATH} at runtime.
 *   <li>Transitive Maven dependencies are not resolved automatically; declare all JARs explicitly.
 * </ul>
 */
public final class ScriptRunner {

  /** Name of the build script file Buildente looks for. */
  public static final String SCRIPT_FILE = "Buildente.java";

  /** Name of the compiled class Buildente instantiates. */
  private static final String SCRIPT_CLASS = "Buildente";

  /** Name of the subdirectory created inside the project root to hold compiled script classes. */
  private static final String BUILDENTE_DIR = ".buildente";

  // Utility class — no instances
  private ScriptRunner() {}

  /**
   * Runs a {@code Buildente.java} script located in {@code scriptDir}.
   *
   * <p>This method encapsulates the full lifecycle: locate → resolve deps → compile → load →
   * build-graph definition → step execution.
   *
   * @param scriptDir directory that contains {@code Buildente.java}; also used as the project root
   *     for placing the {@code .buildente/} output directory
   * @param b the {@link Build} instance passed to the script
   * @param stepName the top-level step to execute after the graph is defined
   * @throws BuildScriptException if any phase of the lifecycle fails
   */
  public static void run(Path scriptDir, Build b, String stepName) {
    Path scriptFile = scriptDir.resolve(SCRIPT_FILE);

    // ------------------------------------------------------------------ 1. Locate
    if (!scriptFile.toFile().exists()) {
      throw new BuildScriptException(
          "No build script found. Expected: " + scriptFile.toAbsolutePath());
    }
    System.out.println("[buildente] Found script: " + scriptFile.toAbsolutePath());

    // ------------------------------------------------------------------ 2. Resolve dependencies
    resolveDependencies(scriptDir, b);

    // ------------------------------------------------------------------ 3. Compile
    Path outputDir = compile(scriptFile, scriptDir);

    // ------------------------------------------------------------------ 4. Load
    BuildScript script = load(outputDir);

    // ------------------------------------------------------------------ 5. Define graph
    System.out.println("[buildente] Configuring build graph...");
    script.build(b);

    // ------------------------------------------------------------------ 6. Execute
    System.out.println("[buildente] Build started. Step: " + stepName);
    b.executeStep(stepName);
    System.out.println("[buildente] Build finished.");
  }

  // ------------------------------------------------------------------
  // Phase 2: dependency resolution
  // ------------------------------------------------------------------

  /**
   * Checks for {@code deps.properties}. When present, every entry must have a non-null {@code
   * sha256} (written by {@code bdt update}); if any are missing the build is aborted. Verified JARs
   * are injected into {@code b} via {@link Build#setResolvedJars(Map)}.
   *
   * @param projectRoot the project root directory
   * @param b the {@link Build} instance to inject resolved jars into
   * @throws BuildScriptException if {@code deps.properties} is malformed, any entry lacks a {@code
   *     sha256}, or any JAR cannot be fetched or verified
   */
  private static void resolveDependencies(Path projectRoot, Build b) {
    Path depsPath = projectRoot.resolve(DepsProperties.FILE_NAME);

    if (!Files.exists(depsPath)) {
      return; // No dependencies declared — nothing to do
    }

    DepsProperties deps;
    try {
      deps = DepsProperties.read(depsPath);
    } catch (Exception e) {
      throw new BuildScriptException(
          "Failed to read " + DepsProperties.FILE_NAME + ": " + e.getMessage(), e);
    }

    if (deps.getAliases().isEmpty()) {
      return;
    }

    System.out.println(
        "[buildente] Fetching " + deps.getAliases().size() + " package(s) from deps.properties...");

    try {
      Map<String, Path> resolvedJars = DependencyFetcher.fetchAll(deps);
      b.setResolvedJars(resolvedJars);
    } catch (DependencyFetcher.DependencyFetchException e) {
      throw new BuildScriptException("Dependency resolution failed: " + e.getMessage(), e);
    }

    System.out.println("[buildente] All dependencies resolved.");
  }

  // ------------------------------------------------------------------
  // Phase 3: compile Buildente.java using the external javac command
  // ------------------------------------------------------------------

  /**
   * Compiles {@code scriptFile} by invoking the external {@code javac} command via {@link
   * ProcessBuilder}. The current JVM classpath is forwarded via {@code -classpath} so the script
   * can reference Buildente library classes.
   *
   * <p>Compiled {@code .class} files are written to {@code <projectRoot>/.buildente/}.
   *
   * <p>Command structure:
   *
   * <pre>
   *   javac -classpath &lt;currentClasspath&gt; -d &lt;outputDir&gt; Buildente.java
   * </pre>
   *
   * @param scriptFile path to {@code Buildente.java}
   * @param projectRoot project root directory; the {@code .buildente/} output directory is created
   *     here
   * @return path to the directory containing the compiled {@code .class} files
   * @throws BuildScriptException if the output directory cannot be created, {@code javac} exits
   *     with a non-zero code, or the process is interrupted
   */
  private static Path compile(Path scriptFile, Path projectRoot) {
    File outputDir;
    try {
      outputDir = resolveOutputDir(projectRoot);
    } catch (Exception e) {
      throw new BuildScriptException("Failed to create output directory", e);
    }

    System.out.println("[buildente] Compiling script -> " + outputDir.getAbsolutePath());

    // Forward the full JVM classpath so the script can import buildente classes
    String classpath = System.getProperty("java.class.path");

    List<String> command = new ArrayList<>();
    command.add("javac");
    command.add("-classpath");
    command.add(classpath);
    command.add("-d");
    command.add(outputDir.getAbsolutePath());
    command.add(scriptFile.toAbsolutePath().toString());

    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.inheritIO();

      Process process = pb.start();
      int exitCode = process.waitFor();

      if (exitCode != 0) {
        throw new BuildScriptException(
            "Script compilation failed. javac exited with code " + exitCode);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BuildScriptException("Script compilation interrupted", e);
    } catch (BuildScriptException e) {
      throw e;
    } catch (Exception e) {
      throw new BuildScriptException("Unexpected error during script compilation", e);
    }

    return outputDir.toPath();
  }

  // ------------------------------------------------------------------
  // Phase 4: load the compiled class via URLClassLoader
  // ------------------------------------------------------------------

  /**
   * Loads the compiled {@code Buildente} class from {@code outputDir} and instantiates it as a
   * {@link BuildScript}.
   *
   * @param outputDir directory produced by {@link #compile(Path, Path)}
   * @return an instance of the user's {@code Buildente} class
   * @throws BuildScriptException if loading or instantiation fails
   */
  private static BuildScript load(Path outputDir) {
    try {
      URL[] urls = {outputDir.toUri().toURL()};

      // Use the current class loader as parent so buildente library classes
      // are visible inside the loaded script.
      ClassLoader parent = ScriptRunner.class.getClassLoader();

      try (URLClassLoader loader = new URLClassLoader(urls, parent)) {
        Class<?> scriptClass = loader.loadClass(SCRIPT_CLASS);

        if (!BuildScript.class.isAssignableFrom(scriptClass)) {
          throw new BuildScriptException(
              "Class '"
                  + SCRIPT_CLASS
                  + "' in Buildente.java must implement "
                  + BuildScript.class.getName());
        }

        System.out.println("[buildente] Loaded script class: " + scriptClass.getName());
        return (BuildScript) scriptClass.getDeclaredConstructor().newInstance();
      }

    } catch (BuildScriptException e) {
      throw e;
    } catch (Exception e) {
      throw new BuildScriptException("Failed to load script class '" + SCRIPT_CLASS + "'", e);
    }
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  /**
   * Returns the {@code .buildente/} directory inside {@code projectRoot}, creating it if it does
   * not already exist.
   *
   * @param projectRoot the project root directory
   * @return the {@code File} representing {@code <projectRoot>/.buildente}
   * @throws IllegalStateException if the directory cannot be created
   */
  private static File resolveOutputDir(Path projectRoot) {
    File outputDir = projectRoot.resolve(BUILDENTE_DIR).toFile();
    if (!outputDir.exists() && !outputDir.mkdirs()) {
      throw new IllegalStateException("Cannot create output directory: " + outputDir);
    }
    return outputDir;
  }

  // ------------------------------------------------------------------
  // Exception type
  // ------------------------------------------------------------------

  /** Thrown when any phase of the script lifecycle (locate / compile / load) fails. */
  public static final class BuildScriptException extends RuntimeException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public BuildScriptException(String message) {
      super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public BuildScriptException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
