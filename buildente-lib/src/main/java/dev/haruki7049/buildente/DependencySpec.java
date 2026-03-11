package dev.haruki7049.buildente;

/**
 * A Maven dependency coordinate in {@code groupId:artifactId:version} (GAV) form.
 *
 * <p>Declared by the user in {@code deps.properties} under the {@code <alias>.id} key:
 *
 * <pre>
 * guava.id=com.google.guava:guava:33.0.0-jre
 * guava.repo=https://repo1.maven.org/maven2
 * guava.sha256=3fd4341776428c7e0e5c18a7c10de129475b69ab9d30aeafbb5c277bb6074fa9
 * </pre>
 *
 * <p>The short <em>alias</em> (e.g. {@code "guava"}) is used by the build script to reference the
 * dependency:
 *
 * <pre>{@code
 * Module mod = b.createModule("src");
 * mod.addDependency("guava");
 * }</pre>
 */
public final class DependencySpec {

  /** User-defined alias for this dependency, used as the key prefix in {@code deps.properties}. */
  private final String alias;

  /** Maven {@code groupId} (e.g. {@code "com.google.guava"}). */
  private final String groupId;

  /** Maven {@code artifactId} (e.g. {@code "guava"}). */
  private final String artifactId;

  /** Maven version string (e.g. {@code "33.0.0-jre"}). */
  private final String version;

  private DependencySpec(String alias, String groupId, String artifactId, String version) {
    this.alias = alias;
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  // -------------------------------------------------------------------------
  // Factory
  // -------------------------------------------------------------------------

  /**
   * Parses a {@code "groupId:artifactId:version"} string into a {@link DependencySpec}.
   *
   * @param alias the short user-defined name for this dependency
   * @param gav the GAV string, e.g. {@code "com.google.guava:guava:33.0.0-jre"}
   * @return a new {@link DependencySpec}
   * @throws IllegalArgumentException if {@code gav} does not contain exactly two {@code ':'} chars
   */
  public static DependencySpec parse(String alias, String gav) {
    String[] parts = gav.split(":", -1);
    if (parts.length != 3) {
      throw new IllegalArgumentException(
          "Dependency '" + alias + "' must be in 'groupId:artifactId:version' format, got: " + gav);
    }
    return new DependencySpec(alias, parts[0].trim(), parts[1].trim(), parts[2].trim());
  }

  // -------------------------------------------------------------------------
  // Maven path derivation
  // -------------------------------------------------------------------------

  /**
   * Returns the relative path to this artifact's JAR inside a Maven repository.
   *
   * <p>For example, {@code com.google.guava:guava:33.0.0-jre} maps to:
   *
   * <pre>
   *   com/google/guava/guava/33.0.0-jre/guava-33.0.0-jre.jar
   * </pre>
   *
   * @return the relative Maven path, using {@code '/'} as separator
   */
  public String toMavenPath() {
    return groupId.replace('.', '/')
        + "/"
        + artifactId
        + "/"
        + version
        + "/"
        + artifactId
        + "-"
        + version
        + ".jar";
  }

  /**
   * Returns the full JAR download URL by appending {@link #toMavenPath()} to {@code repoUrl}.
   *
   * @param repoUrl base URL of the Maven repository, e.g. {@code "https://repo1.maven.org/maven2"}
   * @return the full download URL for this artifact's JAR
   */
  public String toJarUrl(String repoUrl) {
    String base = repoUrl.endsWith("/") ? repoUrl : repoUrl + "/";
    return base + toMavenPath();
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /**
   * Returns the user-defined alias for this dependency.
   *
   * @return the alias, e.g. {@code "guava"}
   */
  public String getAlias() {
    return alias;
  }

  /**
   * Returns the Maven {@code groupId}.
   *
   * @return the group ID, e.g. {@code "com.google.guava"}
   */
  public String getGroupId() {
    return groupId;
  }

  /**
   * Returns the Maven {@code artifactId}.
   *
   * @return the artifact ID, e.g. {@code "guava"}
   */
  public String getArtifactId() {
    return artifactId;
  }

  /**
   * Returns the Maven version string.
   *
   * @return the version, e.g. {@code "33.0.0-jre"}
   */
  public String getVersion() {
    return version;
  }

  @Override
  public String toString() {
    return alias + "=" + groupId + ":" + artifactId + ":" + version;
  }
}
