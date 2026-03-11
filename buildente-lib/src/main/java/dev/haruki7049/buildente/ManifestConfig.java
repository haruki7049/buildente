package dev.haruki7049.buildente;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Programmatic builder for a JAR {@code MANIFEST.MF} file.
 *
 * <p>Use this class when you want to define the manifest inline in your {@code Buildente.java}
 * build script rather than maintaining a separate {@code MANIFEST.MF} file. The built manifest is
 * written to a temporary file and forwarded to the {@code jar} tool by {@link JarStep}.
 *
 * <p>All setter and {@code add} methods return {@code this} to support method chaining:
 *
 * <pre>{@code
 * ManifestConfig manifest = new ManifestConfig()
 *     .setMainClass("com.example.Main")
 *     .addAttribute("Class-Path", "lib/foo.jar lib/bar.jar")
 *     .addAttribute("Built-By", "Buildente");
 *
 * JarStep jar = b.addJar("myapp", exe, manifest);
 * }</pre>
 *
 * <p>{@code Manifest-Version: 1.0} is always emitted as the first line, as required by the JAR
 * specification.
 */
public final class ManifestConfig {

  /**
   * Ordered map of manifest attributes. {@code Manifest-Version} is always written first and is
   * therefore not stored here — it is emitted unconditionally by {@link #writeTo(OutputStream)}.
   */
  private final Map<String, String> attributes = new LinkedHashMap<>();

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  /**
   * Creates an empty {@code ManifestConfig}. Only {@code Manifest-Version: 1.0} will be emitted.
   */
  public ManifestConfig() {}

  // -------------------------------------------------------------------------
  // Convenience setters
  // -------------------------------------------------------------------------

  /**
   * Sets the {@code Main-Class} attribute.
   *
   * <p>This attribute is required for executable JARs launched with {@code java -jar}.
   *
   * @param mainClass the fully-qualified name of the entry-point class (e.g. {@code
   *     "com.example.Main"})
   * @return {@code this}, for method chaining
   * @throws IllegalArgumentException if {@code mainClass} is null or blank
   */
  public ManifestConfig setMainClass(String mainClass) {
    if (mainClass == null || mainClass.isBlank()) {
      throw new IllegalArgumentException("mainClass must not be null or blank");
    }
    return addAttribute("Main-Class", mainClass);
  }

  /**
   * Sets the {@code Class-Path} attribute.
   *
   * <p>Multiple JARs should be space-separated, as per the JAR specification.
   *
   * @param classPath space-separated list of relative JAR/directory paths
   * @return {@code this}, for method chaining
   * @throws IllegalArgumentException if {@code classPath} is null or blank
   */
  public ManifestConfig setClassPath(String classPath) {
    if (classPath == null || classPath.isBlank()) {
      throw new IllegalArgumentException("classPath must not be null or blank");
    }
    return addAttribute("Class-Path", classPath);
  }

  // -------------------------------------------------------------------------
  // Generic attribute setter
  // -------------------------------------------------------------------------

  /**
   * Adds (or replaces) an arbitrary manifest attribute.
   *
   * <p>Keys are case-sensitive, and the last call for a given key wins. Attempting to set {@code
   * Manifest-Version} is silently ignored because it is always emitted automatically.
   *
   * <pre>{@code
   * manifest.addAttribute("Built-By", "Buildente")
   *         .addAttribute("Implementation-Version", "1.2.3");
   * }</pre>
   *
   * @param key the manifest attribute name (e.g. {@code "Main-Class"})
   * @param value the attribute value
   * @return {@code this}, for method chaining
   * @throws IllegalArgumentException if {@code key} or {@code value} is null or blank
   */
  public ManifestConfig addAttribute(String key, String value) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Manifest attribute key must not be null or blank");
    }
    if (value == null) {
      throw new IllegalArgumentException(
          "Manifest attribute value must not be null (key: " + key + ")");
    }
    // Manifest-Version is always written first; reject attempts to override it.
    if ("Manifest-Version".equalsIgnoreCase(key)) {
      return this;
    }
    attributes.put(key, value);
    return this;
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /**
   * Returns an unmodifiable view of the custom attributes stored in this config (excludes {@code
   * Manifest-Version}, which is added automatically on write).
   *
   * @return attribute map, possibly empty
   */
  public Map<String, String> getAttributes() {
    return Collections.unmodifiableMap(attributes);
  }

  // -------------------------------------------------------------------------
  // I/O
  // -------------------------------------------------------------------------

  /**
   * Writes the manifest to the given {@link OutputStream} in the format required by the JAR
   * specification.
   *
   * <p>{@code Manifest-Version: 1.0} is always the first line. All custom attributes follow in
   * insertion order. The output is terminated with a blank line as required by the spec.
   *
   * @param out the stream to write to; the caller is responsible for closing it
   * @throws IOException if writing fails
   */
  public void writeTo(OutputStream out) throws IOException {
    PrintWriter writer = new PrintWriter(out);

    // Required first line
    writer.println("Manifest-Version: 1.0");

    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      writer.println(entry.getKey() + ": " + entry.getValue());
    }

    // Trailing newline required by the JAR spec
    writer.println();
    writer.flush();
  }

  // -------------------------------------------------------------------------
  // Object overrides
  // -------------------------------------------------------------------------

  /**
   * Returns a human-readable summary of the configured attributes.
   *
   * @return string listing all attributes including {@code Manifest-Version}
   */
  @Override
  public String toString() {
    return "ManifestConfig{Manifest-Version=1.0, attributes=" + attributes + "}";
  }
}
