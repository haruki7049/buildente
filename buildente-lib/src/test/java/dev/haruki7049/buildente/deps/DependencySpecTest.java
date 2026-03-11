package dev.haruki7049.buildente.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DependencySpec}.
 *
 * <p>Covers parsing, Maven path derivation, URL generation, and all accessors.
 */
class DependencySpecTest {

  // -------------------------------------------------------------------------
  // parse()
  // -------------------------------------------------------------------------

  /** A well-formed GAV string must produce the correct group, artifact, and version. */
  @Test
  void parse_validGav_returnsCorrectFields() {
    DependencySpec spec = DependencySpec.parse("guava", "com.google.guava:guava:33.0.0-jre");

    assertEquals("guava", spec.getAlias());
    assertEquals("com.google.guava", spec.getGroupId());
    assertEquals("guava", spec.getArtifactId());
    assertEquals("33.0.0-jre", spec.getVersion());
  }

  /** Extra whitespace around colons must be stripped. */
  @Test
  void parse_gavWithWhitespace_trimsAllParts() {
    DependencySpec spec = DependencySpec.parse("guava", " com.google.guava : guava : 33.0.0-jre ");

    assertEquals("com.google.guava", spec.getGroupId());
    assertEquals("guava", spec.getArtifactId());
    assertEquals("33.0.0-jre", spec.getVersion());
  }

  /** A string with only one colon is not a valid GAV and must throw. */
  @Test
  void parse_twoPartGav_throwsIllegalArgument() {
    assertThrows(
        IllegalArgumentException.class, () -> DependencySpec.parse("bad", "groupId:artifactId"));
  }

  /** A string with no colons must also throw. */
  @Test
  void parse_noColonGav_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> DependencySpec.parse("bad", "justAString"));
  }

  /** A GAV string with four segments (too many colons) must throw. */
  @Test
  void parse_fourPartGav_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> DependencySpec.parse("bad", "a:b:c:d"));
  }

  // -------------------------------------------------------------------------
  // toMavenPath()
  // -------------------------------------------------------------------------

  /** Dots in the groupId must become forward-slashes in the Maven path. */
  @Test
  void toMavenPath_dotSeparatedGroupId_convertsDotsToSlashes() {
    DependencySpec spec = DependencySpec.parse("guava", "com.google.guava:guava:33.0.0-jre");

    String path = spec.toMavenPath();

    assertEquals("com/google/guava/guava/33.0.0-jre/guava-33.0.0-jre.jar", path);
  }

  /** A single-segment groupId (no dots) must still produce the correct path. */
  @Test
  void toMavenPath_singleSegmentGroupId_noSlashConversion() {
    DependencySpec spec = DependencySpec.parse("mylib", "org:mylib:1.0");

    assertEquals("org/mylib/1.0/mylib-1.0.jar", spec.toMavenPath());
  }

  // -------------------------------------------------------------------------
  // toJarUrl()
  // -------------------------------------------------------------------------

  /** When the repo URL already ends with '/', no extra slash should appear in the result. */
  @Test
  void toJarUrl_repoWithTrailingSlash_noDoubleSlash() {
    DependencySpec spec = DependencySpec.parse("guava", "com.google.guava:guava:33.0.0-jre");

    String url = spec.toJarUrl("https://repo1.maven.org/maven2/");

    assertTrue(url.startsWith("https://repo1.maven.org/maven2/com/google/guava/"));
    assertEquals(
        "https://repo1.maven.org/maven2/com/google/guava/guava/33.0.0-jre/guava-33.0.0-jre.jar",
        url);
  }

  /** When the repo URL does NOT end with '/', a slash must be appended automatically. */
  @Test
  void toJarUrl_repoWithoutTrailingSlash_appendsSlash() {
    DependencySpec spec = DependencySpec.parse("guava", "com.google.guava:guava:33.0.0-jre");

    String url = spec.toJarUrl("https://repo1.maven.org/maven2");

    assertEquals(
        "https://repo1.maven.org/maven2/com/google/guava/guava/33.0.0-jre/guava-33.0.0-jre.jar",
        url);
  }

  // -------------------------------------------------------------------------
  // toString()
  // -------------------------------------------------------------------------

  /** {@link DependencySpec#toString()} must include alias and all GAV components. */
  @Test
  void toString_includesAliasAndGav() {
    DependencySpec spec = DependencySpec.parse("guava", "com.google.guava:guava:33.0.0-jre");

    String s = spec.toString();

    assertTrue(s.contains("guava"));
    assertTrue(s.contains("com.google.guava"));
    assertTrue(s.contains("33.0.0-jre"));
  }
}
