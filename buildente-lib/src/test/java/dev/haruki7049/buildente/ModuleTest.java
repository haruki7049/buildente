package dev.haruki7049.buildente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link Module}.
 *
 * <p>Covers constructor validation, dependency declaration, source file discovery, and the
 * extra-arg accumulator.
 */
class ModuleTest {

  @TempDir Path tempDir;

  // -------------------------------------------------------------------------
  // Constructor validation
  // -------------------------------------------------------------------------

  /** A null sourceDir must be rejected immediately. */
  @Test
  void constructor_nullSourceDir_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> new Module(null));
  }

  /** A blank sourceDir must also be rejected. */
  @Test
  void constructor_blankSourceDir_throwsIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> new Module("   "));
  }

  /** A valid sourceDir must produce a Module with that dir accessible. */
  @Test
  void constructor_validSourceDir_storesDir() {
    Module m = new Module("src");
    assertEquals("src", m.getSourceDir());
  }

  // -------------------------------------------------------------------------
  // addDependency() / getResolvedJars()
  // -------------------------------------------------------------------------

  /** A null alias passed to addDependency must be silently ignored. */
  @Test
  void addDependency_null_ignored() {
    Module m = new Module("src");
    m.addDependency(null); // must not throw or add anything

    assertTrue(
        m.getResolvedJars().isEmpty(), "no jars should be registered after adding a null alias");
  }

  /** A blank alias passed to addDependency must also be ignored. */
  @Test
  void addDependency_blank_ignored() {
    Module m = new Module("src");
    m.addDependency("   ");

    assertTrue(m.getResolvedJars().isEmpty());
  }

  /** A valid alias backed by a resolved jar map must return the correct path. */
  @Test
  void addDependency_knownAlias_resolvesToJarPath() throws IOException {
    Path fakeJar = tempDir.resolve("guava.jar");
    Files.createFile(fakeJar);

    Module m = new Module("src", Map.of("guava", fakeJar));
    m.addDependency("guava");

    List<Path> jars = m.getResolvedJars();
    assertEquals(1, jars.size());
    assertEquals(fakeJar, jars.get(0));
  }

  /** Multiple dependencies must be returned in declaration order. */
  @Test
  void addDependency_multipleAliases_preservesOrder() throws IOException {
    Path jar1 = tempDir.resolve("a.jar");
    Path jar2 = tempDir.resolve("b.jar");
    Files.createFile(jar1);
    Files.createFile(jar2);

    Module m = new Module("src", Map.of("a", jar1, "b", jar2));
    m.addDependency("a").addDependency("b");

    List<Path> jars = m.getResolvedJars();
    assertEquals(jar1, jars.get(0));
    assertEquals(jar2, jars.get(1));
  }

  /** Requesting a jar for an alias not present in the resolved map must throw. */
  @Test
  void getResolvedJars_unknownAlias_throwsIllegalState() {
    Module m = new Module("src", Map.of()); // empty jar map
    m.addDependency("nonexistent");

    assertThrows(IllegalStateException.class, m::getResolvedJars);
  }

  // -------------------------------------------------------------------------
  // addExtraArg()
  // -------------------------------------------------------------------------

  /** A null extra arg must be ignored without throwing. */
  @Test
  void addExtraArg_null_ignored() {
    Module m = new Module("src");
    m.addExtraArg(null);

    assertTrue(m.getExtraArgs().isEmpty());
  }

  /** A blank extra arg must also be ignored. */
  @Test
  void addExtraArg_blank_ignored() {
    Module m = new Module("src");
    m.addExtraArg("  ");

    assertTrue(m.getExtraArgs().isEmpty());
  }

  /** Multiple valid args must be accumulated in order. */
  @Test
  void addExtraArg_multipleArgs_appendedInOrder() {
    Module m = new Module("src");
    m.addExtraArg("-source").addExtraArg("17");

    assertEquals(List.of("-source", "17"), m.getExtraArgs());
  }

  // -------------------------------------------------------------------------
  // resolveSourceFiles()
  // -------------------------------------------------------------------------

  /** Pointing at a non-existent directory must throw an IllegalStateException. */
  @Test
  void resolveSourceFiles_nonExistentDir_throwsIllegalState() {
    Module m = new Module(tempDir.resolve("does-not-exist").toString());

    assertThrows(IllegalStateException.class, m::resolveSourceFiles);
  }

  /** An existing directory with no .java files must also throw. */
  @Test
  void resolveSourceFiles_emptyDir_throwsIllegalState() {
    Module m = new Module(tempDir.toString());

    assertThrows(IllegalStateException.class, m::resolveSourceFiles);
  }

  /** All .java files in a directory tree must be discovered and sorted. */
  @Test
  void resolveSourceFiles_withJavaFiles_returnsSortedList() throws IOException {
    Files.createFile(tempDir.resolve("B.java"));
    Files.createFile(tempDir.resolve("A.java"));

    Module m = new Module(tempDir.toString());
    List<String> files = m.resolveSourceFiles();

    assertEquals(2, files.size());
    assertTrue(files.get(0).endsWith("A.java"), "A.java should come first after sorting");
    assertTrue(files.get(1).endsWith("B.java"));
  }

  /** Non-.java files in the source tree must be excluded from the result. */
  @Test
  void resolveSourceFiles_ignoresNonJavaFiles() throws IOException {
    Files.createFile(tempDir.resolve("Main.java"));
    Files.createFile(tempDir.resolve("README.md"));

    Module m = new Module(tempDir.toString());
    List<String> files = m.resolveSourceFiles();

    assertEquals(1, files.size());
    assertTrue(files.get(0).endsWith("Main.java"));
  }
}
