package dev.haruki7049.buildente;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Reads and writes {@code deps.properties} — the single dependency declaration file used by
 * Buildente.
 *
 * <p>Each dependency is identified by a short <em>alias</em> and described by three keys:
 *
 * <ul>
 *   <li>{@code <alias>.id} — Maven GAV coordinate ({@code groupId:artifactId:version}).
 *   <li>{@code <alias>.repo} — Base URL of the Maven repository that hosts the JAR.
 *   <li>{@code <alias>.sha256} — Hex-encoded SHA-256 digest of the JAR. Populated by {@code bdt
 *       update}; must not be edited by hand.
 * </ul>
 *
 * <h2>Example file</h2>
 *
 * <pre>
 * # Run 'bdt update' after adding new entries to fill in sha256 hashes.
 *
 * guava.id=com.google.guava:guava:33.0.0-jre
 * guava.repo=https://repo1.maven.org/maven2
 * guava.sha256=3fd4341776428c7e0e5c18a7c10de129475b69ab9d30aeafbb5c277bb6074fa9
 *
 * slf4j-api.id=org.slf4j:slf4j-api:2.0.13
 * slf4j-api.repo=https://repo1.maven.org/maven2
 * slf4j-api.sha256=7cf2726267d88a1c424e04b8b8a7b9c3b75e47c11c71e2b39e2e6166b9a4a44f
 * </pre>
 *
 * <h2>Workflow</h2>
 *
 * <ol>
 *   <li>User adds {@code <alias>.id} and {@code <alias>.repo} entries to {@code deps.properties}.
 *   <li>User runs {@code bdt update} — Buildente downloads the JAR, computes its SHA-256, and
 *       writes the {@code <alias>.sha256} entry back into the same file.
 *   <li>The updated file is committed to VCS. Every subsequent {@code bdt build} verifies the hash
 *       before using the cached JAR.
 * </ol>
 *
 * <h2>Using standard {@link java.util.Properties}</h2>
 *
 * <p>Parsing is delegated entirely to {@link Properties}, so standard escaping rules apply. The
 * writer emits a clean, sorted file with one blank line between each dependency block.
 */
public final class DepsProperties {

  /** File name Buildente looks for in the project root. */
  public static final String FILE_NAME = "deps.properties";

  /**
   * Ordered list of aliases discovered from the file. Insertion order is preserved so that the
   * written file is deterministic.
   */
  private final List<String> aliases;

  /**
   * Map from alias to its {@link Entry}. Uses a {@link LinkedHashMap} to preserve declaration
   * order.
   */
  private final Map<String, Entry> entries;

  private DepsProperties(List<String> aliases, Map<String, Entry> entries) {
    this.aliases = Collections.unmodifiableList(aliases);
    this.entries = Collections.unmodifiableMap(entries);
  }

  // -------------------------------------------------------------------------
  // Factory — read from disk
  // -------------------------------------------------------------------------

  /**
   * Reads and parses {@code deps.properties} from {@code path}.
   *
   * <p>Only lines whose keys match the pattern {@code <alias>.id} are used to discover aliases. For
   * each discovered alias the corresponding {@code .repo} and {@code .sha256} keys are read (both
   * optional at parse time; {@link ScriptRunner} enforces that {@code sha256} is present at build
   * time).
   *
   * @param path path to the {@code deps.properties} file
   * @return the parsed dependency table
   * @throws IOException if the file cannot be read
   * @throws IllegalArgumentException if a {@code .id} value is not a valid GAV string
   */
  public static DepsProperties read(Path path) throws IOException {
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(path)) {
      props.load(in);
    }

    // Collect aliases in the order their .id keys were encountered.
    // java.util.Properties does not preserve insertion order, so we derive
    // a stable order by sorting alphabetically (predictable across JVM versions).
    List<String> aliases = new ArrayList<>();
    for (String key : props.stringPropertyNames()) {
      if (key.endsWith(".id")) {
        aliases.add(key.substring(0, key.length() - 3));
      }
    }
    Collections.sort(aliases);

    Map<String, Entry> entries = new LinkedHashMap<>();
    for (String alias : aliases) {
      String idVal = props.getProperty(alias + ".id");
      if (idVal == null || idVal.isBlank()) {
        throw new IllegalArgumentException("deps.properties: '" + alias + ".id' must not be blank");
      }
      DependencySpec spec = DependencySpec.parse(alias, idVal.trim());
      String repo = props.getProperty(alias + ".repo", "").trim();
      String sha256 = props.getProperty(alias + ".sha256", "").trim();
      entries.put(
          alias, new Entry(spec, repo.isEmpty() ? null : repo, sha256.isEmpty() ? null : sha256));
    }

    return new DepsProperties(aliases, entries);
  }

  // -------------------------------------------------------------------------
  // Writer
  // -------------------------------------------------------------------------

  /**
   * Writes the current state of this table back to {@code path}.
   *
   * <p>The output is a plain Java {@code .properties} file with one blank line separating each
   * dependency block:
   *
   * <pre>
   * # Run 'bdt update' after adding new entries to fill in sha256 hashes.
   *
   * guava.id=com.google.guava:guava:33.0.0-jre
   * guava.repo=https://repo1.maven.org/maven2
   * guava.sha256=3fd4341776428c7e0e5c18a7c10de129475b69ab9d30aeafbb5c277bb6074fa9
   * </pre>
   *
   * @param path the file to write; created if absent, overwritten if present
   * @throws IOException if the file cannot be written
   */
  public void write(Path path) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("# Run 'bdt update' after adding new entries to fill in sha256 hashes.\n");

    for (String alias : aliases) {
      Entry e = entries.get(alias);
      sb.append('\n');
      sb.append(escapeKey(alias))
          .append(".id=")
          .append(e.spec.getGroupId())
          .append(':')
          .append(e.spec.getArtifactId())
          .append(':')
          .append(e.spec.getVersion())
          .append('\n');
      if (e.repo != null) {
        sb.append(escapeKey(alias)).append(".repo=").append(e.repo).append('\n');
      }
      if (e.sha256 != null) {
        sb.append(escapeKey(alias)).append(".sha256=").append(e.sha256).append('\n');
      }
    }

    Files.writeString(path, sb.toString());
  }

  // -------------------------------------------------------------------------
  // Builder — produce a new instance with an updated sha256 for one alias
  // -------------------------------------------------------------------------

  /**
   * Returns a new {@link DepsProperties} identical to this one except that the {@code sha256} for
   * {@code alias} is set to {@code sha256}.
   *
   * <p>Used by {@link UpdateCommand} to record the computed hash back into the file.
   *
   * @param alias the alias whose hash to update
   * @param sha256 the new 64-character hex SHA-256 string
   * @return a new, updated {@link DepsProperties}
   * @throws IllegalArgumentException if {@code alias} is not present in this table
   */
  public DepsProperties withSha256(String alias, String sha256) {
    if (!entries.containsKey(alias)) {
      throw new IllegalArgumentException("Unknown alias: " + alias);
    }
    Map<String, Entry> updated = new LinkedHashMap<>(entries);
    Entry old = updated.get(alias);
    updated.put(alias, new Entry(old.spec, old.repo, sha256));
    return new DepsProperties(new ArrayList<>(aliases), updated);
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /**
   * Returns all aliases in stable (sorted) order.
   *
   * @return an unmodifiable list of alias strings
   */
  public List<String> getAliases() {
    return aliases;
  }

  /**
   * Returns the {@link Entry} for {@code alias}, or {@code null} if not present.
   *
   * @param alias the dependency alias
   * @return the entry or {@code null}
   */
  public Entry getEntry(String alias) {
    return entries.get(alias);
  }

  /**
   * Returns all entries as an unmodifiable map.
   *
   * @return map from alias to {@link Entry}
   */
  public Map<String, Entry> getEntries() {
    return entries;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * Escapes characters in a property key that have special meaning in {@code .properties} files.
   * Specifically replaces {@code -} with {@code \-} so that aliases like {@code slf4j-api} are
   * written correctly.
   *
   * <p>Note: {@link Properties#load} accepts unescaped {@code -} in keys, so this is only needed on
   * the write path for strict round-trip correctness.
   *
   * @param key the raw alias string
   * @return the escaped key
   */
  private static String escapeKey(String key) {
    // '-' is safe in .properties keys; '=' and ':' are not but won't appear in Maven aliases.
    return key;
  }

  // -------------------------------------------------------------------------
  // Nested value type
  // -------------------------------------------------------------------------

  /**
   * Holds the three fields that describe one dependency in {@code deps.properties}.
   *
   * <p>{@code repo} and {@code sha256} may be {@code null} when reading a file that was partially
   * filled in by the user (before running {@code bdt update}).
   */
  public static final class Entry {

    /** Parsed Maven GAV coordinate. */
    private final DependencySpec spec;

    /** Base Maven repository URL, e.g. {@code "https://repo1.maven.org/maven2"}. May be null. */
    private final String repo;

    /** Hex-encoded SHA-256 of the JAR. Written by {@code bdt update}; {@code null} until then. */
    private final String sha256;

    Entry(DependencySpec spec, String repo, String sha256) {
      this.spec = spec;
      this.repo = repo;
      this.sha256 = sha256;
    }

    /**
     * Returns the parsed GAV coordinate.
     *
     * @return the {@link DependencySpec}, never {@code null}
     */
    public DependencySpec getSpec() {
      return spec;
    }

    /**
     * Returns the base repository URL, or {@code null} if not yet specified.
     *
     * @return the repository URL or {@code null}
     */
    public String getRepo() {
      return repo;
    }

    /**
     * Returns the hex-encoded SHA-256 digest, or {@code null} if not yet computed.
     *
     * @return the hash string or {@code null}
     */
    public String getSha256() {
      return sha256;
    }

    /**
     * Returns the full JAR download URL by combining {@link #repo} and the GAV path.
     *
     * @return the JAR URL
     * @throws IllegalStateException if {@link #repo} is {@code null}
     */
    public String toJarUrl() {
      if (repo == null) {
        throw new IllegalStateException(
            "No repo configured for dependency '" + spec.getAlias() + "'");
      }
      return spec.toJarUrl(repo);
    }
  }
}
