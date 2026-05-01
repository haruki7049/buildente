package dev.haruki7049.buildente;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A build step that packages compiled class files into a JAR archive.
 *
 * <p>Corresponds to the concept of {@code b.addJar(name, exe)} in the Buildente API. This step
 * automatically depends on the supplied {@link Executable}, ensuring that compilation always
 * precedes packaging.
 *
 * <p>The produced JAR is placed under {@value #OUTPUT_DIR} and named {@code <jarName>.jar}:
 *
 * <pre>
 *   build/libs/myapp.jar
 * </pre>
 *
 * <h2>Manifest options</h2>
 *
 * <p>Three manifest strategies are supported:
 *
 * <ol>
 *   <li><strong>No manifest</strong> — the {@code jar} tool generates a default manifest:
 *       <pre>{@code
 * JarStep jar = b.addJar("myapp", exe);
 * }</pre>
 *   <li><strong>Programmatic manifest</strong> — attributes are declared inline via {@link
 *       ManifestConfig}:
 *       <pre>{@code
 * ManifestConfig mf = new ManifestConfig()
 *     .setMainClass("com.example.Main");
 * JarStep jar = b.addJar("myapp", exe, mf);
 * }</pre>
 *   <li><strong>File-based manifest</strong> — an existing {@code MANIFEST.MF} file is forwarded
 *       directly to {@code jar}:
 *       <pre>{@code
 * JarStep jar = b.addJarWithManifestFile("myapp", exe, "META-INF/MANIFEST.MF");
 * }</pre>
 * </ol>
 *
 * <p>Internally this step invokes the external {@code jar} command via {@link ProcessBuilder},
 * which must be available on {@code PATH} at build time (it ships with every JDK).
 */
public class JarStep extends Step {

  private static final Logger LOGGER = Logger.getLogger(JarStep.class.getName());

  /** Directory where all produced JAR files are placed. */
  public static final String OUTPUT_DIR = "build/libs";

  /** Name of the produced JAR file, without the {@code .jar} extension. */
  private final String jarName;

  /** Compilation step whose output directory is packaged into the JAR. */
  private final Executable executable;

  /**
   * Optional programmatic manifest configuration. Mutually exclusive with {@link
   * #manifestFilePath}.
   */
  private final ManifestConfig manifestConfig;

  /**
   * Optional path to an existing {@code MANIFEST.MF} file. Mutually exclusive with {@link
   * #manifestConfig}.
   */
  private final String manifestFilePath;

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  /**
   * Creates a JAR step with no custom manifest.
   *
   * <p>If the supplied {@link Executable} was created with an attached {@link ManifestConfig} (via
   * {@link Build#addExecutable(String, Module, ManifestConfig)}), that manifest is inherited
   * automatically. Otherwise the {@code jar} tool generates a minimal default {@code MANIFEST.MF}.
   *
   * @param jarName the base name of the output JAR (without {@code .jar} extension)
   * @param executable the compilation step whose output is packaged
   */
  public JarStep(String jarName, Executable executable) {
    this(jarName, executable, (ManifestConfig) null, null);
  }

  /**
   * Creates a JAR step with a programmatic manifest defined by a {@link ManifestConfig}.
   *
   * @param jarName the base name of the output JAR (without {@code .jar} extension)
   * @param executable the compilation step whose output is packaged
   * @param manifestConfig manifest attributes to embed; {@code null} means no custom manifest
   */
  public JarStep(String jarName, Executable executable, ManifestConfig manifestConfig) {
    this(jarName, executable, manifestConfig, null);
  }

  /**
   * Creates a JAR step that forwards an existing {@code MANIFEST.MF} file to the {@code jar} tool.
   *
   * @param jarName the base name of the output JAR (without {@code .jar} extension)
   * @param executable the compilation step whose output is packaged
   * @param manifestFilePath path to an existing {@code MANIFEST.MF} file (relative to the working
   *     directory); {@code null} means no custom manifest
   */
  public JarStep(String jarName, Executable executable, String manifestFilePath) {
    this(jarName, executable, null, manifestFilePath);
  }

  /**
   * Primary constructor. Exactly one of {@code manifestConfig} and {@code manifestFilePath} should
   * be non-null; if both are provided, {@code manifestConfig} takes precedence.
   */
  private JarStep(
      String jarName,
      Executable executable,
      ManifestConfig manifestConfig,
      String manifestFilePath) {
    super("jar:" + jarName);
    if (jarName == null || jarName.isBlank()) {
      throw new IllegalArgumentException("jarName must not be null or blank");
    }
    if (executable == null) {
      throw new IllegalArgumentException("executable must not be null");
    }
    this.jarName = jarName;
    this.executable = executable;
    this.manifestConfig = manifestConfig;
    this.manifestFilePath = manifestFilePath;
    // Packaging always follows compilation
    this.dependOn(executable);
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /**
   * Returns the base name of the JAR file (without {@code .jar} extension).
   *
   * @return the JAR name
   */
  public String getJarName() {
    return jarName;
  }

  /**
   * Returns the path of the produced JAR file relative to the working directory.
   *
   * @return path such as {@code "build/libs/myapp.jar"}
   */
  public String getOutputJarPath() {
    return OUTPUT_DIR + "/" + jarName + ".jar";
  }

  // -------------------------------------------------------------------------
  // Step implementation
  // -------------------------------------------------------------------------

  /**
   * Invokes the external {@code jar} command via {@link ProcessBuilder} to create the JAR archive.
   *
   * <p>If a {@link ManifestConfig} was supplied, its contents are written to a temporary file and
   * forwarded to {@code jar} via {@code --manifest}. If a manifest file path was supplied, it is
   * forwarded directly. Otherwise {@code jar} generates a default manifest.
   *
   * <p>Command structure (with manifest):
   *
   * <pre>
   *   jar --create --file=build/libs/&lt;name&gt;.jar --manifest=&lt;mf&gt; -C build/classes .
   * </pre>
   *
   * <p>Command structure (without manifest):
   *
   * <pre>
   *   jar --create --file=build/libs/&lt;name&gt;.jar -C build/classes .
   * </pre>
   *
   * @throws RuntimeException if directory creation, manifest writing, {@code jar} invocation, or
   *     process handling fails
   */
  @Override
  protected void execute() {
    LOGGER.info("[buildente] Packaging JAR: " + getOutputJarPath() + " ...");

    // Ensure the output directory exists
    new File(OUTPUT_DIR).mkdirs();

    // Resolve effective manifest file path (temp file or user-supplied)
    Path tempManifest = null;
    String effectiveManifestPath = null;

    try {
      // Resolution order: explicit ManifestConfig > file path > executable-attached config > none
      ManifestConfig effectiveConfig =
          manifestConfig != null ? manifestConfig : executable.getManifestConfig();

      if (effectiveConfig != null) {
        tempManifest = writeTempManifest(effectiveConfig);
        effectiveManifestPath = tempManifest.toAbsolutePath().toString();
        LOGGER.info("[buildente] Using programmatic manifest: " + effectiveConfig);
      } else if (manifestFilePath != null && !manifestFilePath.isBlank()) {
        File mf = new File(manifestFilePath);
        if (!mf.exists()) {
          throw new RuntimeException(
              "[buildente] Manifest file not found: " + mf.getAbsolutePath());
        }
        effectiveManifestPath = mf.getAbsolutePath();
        LOGGER.info("[buildente] Using manifest file: " + effectiveManifestPath);
      }

      List<String> command = buildJarCommand(effectiveManifestPath);
      runCommand(command);

      LOGGER.info("[buildente] JAR created -> " + getOutputJarPath());

    } finally {
      // Always clean up the temporary manifest, even on failure
      if (tempManifest != null) {
        try {
          Files.deleteIfExists(tempManifest);
        } catch (IOException ignored) {
          // Non-critical cleanup failure
        }
      }
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Writes the given {@link ManifestConfig} to a temporary file and returns its path.
   *
   * @param config the manifest to serialise
   * @return path of the newly created temporary file
   * @throws RuntimeException if the file cannot be created or written
   */
  private Path writeTempManifest(ManifestConfig config) {
    try {
      Path tmp = Files.createTempFile("buildente-manifest-", ".mf");
      try (FileOutputStream fos = new FileOutputStream(tmp.toFile())) {
        config.writeTo(fos);
      }
      return tmp;
    } catch (IOException e) {
      throw new RuntimeException("[buildente] Failed to write temporary manifest file", e);
    }
  }

  /**
   * Assembles the full {@code jar} command.
   *
   * @param manifestPath path to the manifest file to embed, or {@code null} for no custom manifest
   * @return fully assembled command list
   */
  private List<String> buildJarCommand(String manifestPath) {
    List<String> command = new ArrayList<>();
    command.add("jar");
    command.add("--create");
    command.add("--file=" + getOutputJarPath());

    if (manifestPath != null) {
      command.add("--manifest=" + manifestPath);
    }

    // Package all compiled classes from the executable's output directory
    command.add("-C");
    command.add(Executable.OUTPUT_DIR);
    command.add(".");

    return command;
  }

  /**
   * Runs the given command via {@link ProcessBuilder}, inheriting stdio from the parent process.
   *
   * @param command the command and its arguments
   * @throws RuntimeException if the process exits with a non-zero code or is interrupted
   */
  private void runCommand(List<String> command) {
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.inheritIO();

      Process process = pb.start();
      int exitCode = process.waitFor();

      if (exitCode != 0) {
        throw new RuntimeException(
            "[buildente] jar exited with code " + exitCode + " for: " + jarName);
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("[buildente] JAR packaging interrupted: " + jarName, e);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("[buildente] Failed to create JAR: " + jarName, e);
    }
  }
}
