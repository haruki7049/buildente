package dev.haruki7049.buildente;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/**
 * Locates, compiles, and executes a user-written {@code build.java} script.
 *
 * <h2>How it works</h2>
 *
 * <ol>
 *   <li><strong>Locate</strong> – looks for {@code build.java} in the directory supplied to {@link
 *       #run(Path, Build, String)}.
 *   <li><strong>Compile</strong> – invokes {@link javax.tools.JavaCompiler} in-process (no external
 *       {@code javac} process), forwarding the current JVM classpath so the script can import
 *       {@code dev.haruki7049.buildente.*}.
 *   <li><strong>Load</strong> – loads the compiled {@code build} class from the {@code .buildente/}
 *       output directory inside the project root via {@link URLClassLoader}.
 *   <li><strong>Execute</strong> – casts the class to {@link BuildScript}, calls {@link
 *       BuildScript#build(Build)}, then runs the requested step.
 * </ol>
 *
 * <h2>Limitations (POC)</h2>
 *
 * <ul>
 *   <li>The script class must be named exactly {@code build} (lowercase).
 *   <li>Only a single source file is supported in this proof-of-concept.
 *   <li>Requires a JDK (not just a JRE) at runtime for {@code javax.tools}.
 * </ul>
 */
public final class ScriptRunner {

  /** Name of the build script file Buildente looks for. */
  public static final String SCRIPT_FILE = "Buildente.java";

  /** Name of the compiled class Buildente instantiates. */
  private static final String SCRIPT_CLASS = "Buildente";

  /**
   * Name of the subdirectory created inside the project root to hold compiled script classes. This
   * replaces the former system-temp-based approach.
   */
  private static final String BUILDENTE_DIR = ".buildente";

  // Utility class — no instances
  private ScriptRunner() {}

  /**
   * Runs a {@code build.java} script located in {@code scriptDir}.
   *
   * <p>This method encapsulates the full lifecycle: locate → compile → load → build-graph
   * definition → step execution.
   *
   * @param scriptDir directory that contains {@code build.java}; also used as the project root for
   *     placing the {@code .buildente/} output directory
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

    // ------------------------------------------------------------------ 2. Compile
    Path outputDir = compile(scriptFile, scriptDir);

    // ------------------------------------------------------------------ 3. Load
    BuildScript script = load(outputDir);

    // ------------------------------------------------------------------ 4. Define graph
    System.out.println("[buildente] Configuring build graph...");
    script.build(b);

    // ------------------------------------------------------------------ 5. Execute
    System.out.println("[buildente] Build started. Step: " + stepName);
    b.executeStep(stepName);
    System.out.println("[buildente] Build finished.");
  }

  // ------------------------------------------------------------------
  // Phase 2: compile build.java using javax.tools (in-process)
  // ------------------------------------------------------------------

  /**
   * Compiles {@code scriptFile} using the in-process Java compiler. The current JVM classpath is
   * forwarded so the script can reference Buildente library classes.
   *
   * <p>Compiled {@code .class} files are written to {@code <projectRoot>/.buildente/}.
   *
   * @param scriptFile path to {@code build.java}
   * @param projectRoot project root directory; the {@code .buildente/} output directory is created
   *     here
   * @return path to the directory containing the compiled {@code .class} files
   * @throws BuildScriptException if the compiler is unavailable or compilation fails
   */
  private static Path compile(Path scriptFile, Path projectRoot) {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new BuildScriptException(
          "javax.tools.JavaCompiler not available. "
              + "Make sure you are running on a JDK, not just a JRE.");
    }

    // Create (or reuse) the .buildente directory inside the project root
    File outputDir;
    try {
      outputDir = resolveOutputDir(projectRoot);
    } catch (Exception e) {
      throw new BuildScriptException("Failed to create output directory", e);
    }

    System.out.println("[buildente] Compiling script -> " + outputDir.getAbsolutePath());

    // Forward the full JVM classpath so the script can import buildente classes
    String classpath = System.getProperty("java.class.path");

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

    List<String> options =
        Arrays.asList("-classpath", classpath, "-d", outputDir.getAbsolutePath());

    try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var compilationUnits = fileManager.getJavaFileObjects(scriptFile.toFile());

      JavaCompiler.CompilationTask task =
          compiler.getTask(
              null, // writer (null = System.err)
              fileManager,
              diagnostics,
              options,
              null, // annotation processors
              compilationUnits);

      boolean success = task.call();

      // Always print diagnostics (warnings + errors)
      for (var d : diagnostics.getDiagnostics()) {
        System.err.println("[buildente] " + d);
      }

      if (!success) {
        throw new BuildScriptException("Script compilation failed. See diagnostics above.");
      }
    } catch (BuildScriptException e) {
      throw e;
    } catch (Exception e) {
      throw new BuildScriptException("Unexpected error during script compilation", e);
    }

    return outputDir.toPath();
  }

  // ------------------------------------------------------------------
  // Phase 3: load the compiled class via URLClassLoader
  // ------------------------------------------------------------------

  /**
   * Loads the compiled {@code build} class from {@code outputDir} and instantiates it as a {@link
   * BuildScript}.
   *
   * @param outputDir directory produced by {@link #compile(Path, Path)}
   * @return an instance of the user's {@code build} class
   * @throws BuildScriptException if loading or instantiation fails
   */
  private static BuildScript load(Path outputDir) {
    try {
      // URLClassLoader needs the output dir + original classpath so that
      // any classes the script depends on are also resolvable.
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
                  + "' in build.java must implement "
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
   * <p>Unlike the former {@code createTempOutputDir()} approach, this directory is persistent and
   * lives alongside the project source tree rather than in the system temp area.
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
