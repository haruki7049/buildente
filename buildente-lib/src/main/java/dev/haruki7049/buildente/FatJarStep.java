package dev.haruki7049.buildente;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A build step that produces an uber-JAR (fat JAR) by merging the compiled class files and all
 * dependency JARs into a single self-contained archive.
 *
 * <p>Corresponds to {@code b.addFatJar(name, exe)} in the Buildente API. The produced JAR can be
 * executed directly with {@code java -jar} without any external classpath configuration.
 *
 * <p>The produced JAR is placed at {@code build/libs/<jarName>.jar}, the same location used by
 * {@link JarStep}.
 *
 * <h2>Merging strategy</h2>
 *
 * <ol>
 *   <li>Open a new {@link ZipOutputStream} for the output JAR.
 *   <li>Write {@code META-INF/MANIFEST.MF} first, constructed from the {@link ManifestConfig}
 *       attached to the {@link Executable} (or an empty manifest if none is present).
 *   <li>Walk {@link Executable#OUTPUT_DIR} recursively and write every {@code .class} file.
 *   <li>For each dependency JAR resolved from {@code deps.properties}, open it as a
 *       {@link ZipInputStream} and copy every entry except {@code META-INF/MANIFEST.MF} (which
 *       was already written in step 2) and duplicate entries (first writer wins).
 * </ol>
 *
 * <p>All merging is done in-process using the standard {@link java.util.zip} API — no external
 * {@code jar} command or temporary directories are required.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * Module mod = b.createModule("src");
 * mod.addDependency("guava");
 *
 * ManifestConfig mf = new ManifestConfig().setMainClass("com.example.Main");
 * Executable exe = b.addExecutable("com.example.Main", mod, mf);
 *
 * FatJarStep fat = b.addFatJar("myapp", exe);
 * b.getInstallStep().dependOn(fat);
 * }</pre>
 */
public final class FatJarStep extends Step {

  /** Buffer size used when copying ZIP entry bytes. */
  private static final int BUFFER_SIZE = 64 * 1024;

  /** Name of the produced JAR file, without the {@code .jar} extension. */
  private final String jarName;

  /** Compilation step whose output directory is packaged into the fat JAR. */
  private final Executable executable;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  /**
   * Creates a fat-JAR step. Automatically adds {@code executable} as a dependency so that
   * compilation always precedes packaging.
   *
   * @param jarName the base name of the output JAR (without {@code .jar} extension)
   * @param executable the compilation step whose output is packaged
   * @throws IllegalArgumentException if {@code jarName} is blank or {@code executable} is null
   */
  public FatJarStep(String jarName, Executable executable) {
    super("fatjar:" + jarName);
    if (jarName == null || jarName.isBlank()) {
      throw new IllegalArgumentException("jarName must not be null or blank");
    }
    if (executable == null) {
      throw new IllegalArgumentException("executable must not be null");
    }
    this.jarName = jarName;
    this.executable = executable;
    // Packaging always follows compilation
    this.dependOn(executable);
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /**
   * Returns the base name of the fat JAR (without {@code .jar} extension).
   *
   * @return the JAR name
   */
  public String getJarName() {
    return jarName;
  }

  /**
   * Returns the path of the produced fat JAR relative to the working directory.
   *
   * @return path such as {@code "build/libs/myapp.jar"}
   */
  public String getOutputJarPath() {
    return JarStep.OUTPUT_DIR + "/" + jarName + ".jar";
  }

  // -------------------------------------------------------------------------
  // Step implementation
  // -------------------------------------------------------------------------

  /**
   * Merges the compiled class files and all resolved dependency JARs into a single output JAR.
   *
   * <p>The manifest is written first. Duplicate ZIP entries are silently skipped (first writer
   * wins). {@code META-INF/MANIFEST.MF} entries from dependency JARs are always skipped.
   *
   * @throws RuntimeException if any I/O operation fails
   */
  @Override
  protected void execute() {
    System.out.println("[buildente] Packaging fat JAR: " + getOutputJarPath() + " ...");

    new File(JarStep.OUTPUT_DIR).mkdirs();

    Path outputPath = Path.of(getOutputJarPath());

    // Track written entry names to skip duplicates
    Set<String> written = new HashSet<>();

    try (ZipOutputStream zos =
        new ZipOutputStream(new FileOutputStream(outputPath.toFile()))) {

      // 1. Write MANIFEST.MF
      writeManifest(zos, written);

      // 2. Write compiled class files from build/classes
      writeClassFiles(zos, written);

      // 3. Merge each dependency JAR
      List<Path> depJars = executable.getModule().getResolvedJars();
      for (Path depJar : depJars) {
        mergeJar(zos, depJar, written);
      }

    } catch (IOException e) {
      throw new RuntimeException(
          "[buildente] Failed to create fat JAR '" + jarName + "': " + e.getMessage(), e);
    }

    System.out.println("[buildente] Fat JAR created -> " + getOutputJarPath());
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Writes {@code META-INF/MANIFEST.MF} into the output ZIP stream.
   *
   * <p>The manifest content is taken from the {@link ManifestConfig} attached to the {@link
   * Executable}, if any. If no manifest is configured, a minimal two-line manifest is written so
   * that the JAR is always well-formed.
   *
   * @param zos the output ZIP stream
   * @param written the set of already-written entry names (updated in place)
   * @throws IOException if the entry cannot be written
   */
  private void writeManifest(ZipOutputStream zos, Set<String> written) throws IOException {
    String manifestEntry = "META-INF/MANIFEST.MF";
    writeDirectoryEntry(zos, written, "META-INF/");

    zos.putNextEntry(new ZipEntry(manifestEntry));
    ManifestConfig config = executable.getManifestConfig();
    if (config != null) {
      config.writeTo(zos);
    } else {
      // Minimal valid manifest
      zos.write("Manifest-Version: 1.0\n".getBytes());
    }
    zos.closeEntry();
    written.add(manifestEntry);
  }

  /**
   * Walks {@link Executable#OUTPUT_DIR} recursively and writes every file into the output ZIP
   * stream, preserving relative paths.
   *
   * @param zos the output ZIP stream
   * @param written the set of already-written entry names (updated in place)
   * @throws IOException if any file cannot be read or written
   */
  private void writeClassFiles(ZipOutputStream zos, Set<String> written) throws IOException {
    Path classesRoot = Path.of(Executable.OUTPUT_DIR);
    if (!Files.isDirectory(classesRoot)) {
      throw new IllegalStateException(
          "[buildente] Compiled classes directory not found: " + classesRoot.toAbsolutePath()
              + ". Was the compile step executed?");
    }

    Files.walk(classesRoot)
        .filter(p -> !Files.isDirectory(p))
        .sorted()
        .forEach(
            p -> {
              String entryName = classesRoot.relativize(p).toString().replace('\\', '/');
              if (written.contains(entryName)) {
                return;
              }
              try {
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(p, zos);
                zos.closeEntry();
                written.add(entryName);
              } catch (IOException e) {
                throw new RuntimeException(
                    "[buildente] Failed to add class file '" + entryName + "': " + e.getMessage(),
                    e);
              }
            });
  }

  /**
   * Opens {@code jarPath} as a {@link ZipInputStream} and copies every entry — except {@code
   * META-INF/MANIFEST.MF} and duplicates — into the output ZIP stream.
   *
   * @param zos the output ZIP stream
   * @param jarPath the dependency JAR to merge
   * @param written the set of already-written entry names (updated in place)
   * @throws IOException if the dependency JAR cannot be read
   */
  private void mergeJar(ZipOutputStream zos, Path jarPath, Set<String> written)
      throws IOException {
    System.out.println("[buildente]   merging " + jarPath.getFileName());

    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jarPath))) {
      ZipEntry entry;
      byte[] buf = new byte[BUFFER_SIZE];

      while ((entry = zis.getNextEntry()) != null) {
        String name = entry.getName();

        // Always skip manifests from dependencies — ours was already written
        if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
          zis.closeEntry();
          continue;
        }

        // Skip duplicate entries (first writer wins)
        if (written.contains(name)) {
          zis.closeEntry();
          continue;
        }

        zos.putNextEntry(new ZipEntry(name));

        if (!entry.isDirectory()) {
          int n;
          while ((n = zis.read(buf)) != -1) {
            zos.write(buf, 0, n);
          }
        }

        zos.closeEntry();
        zis.closeEntry();
        written.add(name);
      }
    }
  }

  /**
   * Writes a directory entry into the ZIP stream if it has not been written already.
   *
   * @param zos the output ZIP stream
   * @param written the set of already-written entry names (updated in place)
   * @param dirName the directory name, must end with {@code '/'}
   * @throws IOException if the entry cannot be written
   */
  private static void writeDirectoryEntry(ZipOutputStream zos, Set<String> written, String dirName)
      throws IOException {
    if (!written.contains(dirName)) {
      zos.putNextEntry(new ZipEntry(dirName));
      zos.closeEntry();
      written.add(dirName);
    }
  }
}
