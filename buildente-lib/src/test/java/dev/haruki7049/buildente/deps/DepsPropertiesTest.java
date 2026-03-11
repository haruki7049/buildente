package dev.haruki7049.buildente.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link DepsProperties}.
 *
 * <p>Exercises reading, writing, and the {@code withSha256} immutable update method using a
 * temporary directory for all file-system operations.
 */
class DepsPropertiesTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // read() – happy paths
    // -------------------------------------------------------------------------

    /** A fully-populated entry must have all three fields (spec, repo, sha256) set. */
    @Test
    void read_fullEntry_parsesAllFields() throws IOException {
        Path file = writeProps(
                "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n"
                        + "guava.sha256=abc123\n");

        DepsProperties deps = DepsProperties.read(file);

        assertEquals(List.of("guava"), deps.getAliases());
        DepsProperties.Entry e = deps.getEntry("guava");
        assertNotNull(e);
        assertEquals("com.google.guava", e.getSpec().getGroupId());
        assertEquals("guava", e.getSpec().getArtifactId());
        assertEquals("33.0.0-jre", e.getSpec().getVersion());
        assertEquals("https://repo1.maven.org/maven2", e.getRepo());
        assertEquals("abc123", e.getSha256());
    }

    /** Missing {@code .repo} and {@code .sha256} must produce null fields, not an exception. */
    @Test
    void read_entryWithoutRepoAndSha256_nullFields() throws IOException {
        Path file = writeProps("guava.id=com.google.guava:guava:33.0.0-jre\n");

        DepsProperties deps = DepsProperties.read(file);

        DepsProperties.Entry e = deps.getEntry("guava");
        assertNull(e.getRepo());
        assertNull(e.getSha256());
    }

    /** Multiple aliases must all be parsed and sorted alphabetically. */
    @Test
    void read_multipleAliases_sortedAlphabetically() throws IOException {
        Path file = writeProps(
                "slf4j.id=org.slf4j:slf4j-api:2.0.13\n"
                        + "slf4j.repo=https://repo1.maven.org/maven2\n"
                        + "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n");

        DepsProperties deps = DepsProperties.read(file);

        // Aliases are always returned in sorted order
        assertEquals(List.of("guava", "slf4j"), deps.getAliases());
    }

    /** An empty file must produce an empty alias list without throwing. */
    @Test
    void read_emptyFile_emptyAliases() throws IOException {
        Path file = writeProps("");

        DepsProperties deps = DepsProperties.read(file);

        assertTrue(deps.getAliases().isEmpty());
    }

    // -------------------------------------------------------------------------
    // read() – error paths
    // -------------------------------------------------------------------------

    /** Reading a non-existent file must throw {@link IOException}. */
    @Test
    void read_missingFile_throwsIOException() {
        Path missing = tempDir.resolve("does-not-exist.properties");
        assertThrows(IOException.class, () -> DepsProperties.read(missing));
    }

    // -------------------------------------------------------------------------
    // withSha256()
    // -------------------------------------------------------------------------

    /** Updating an existing alias must return a new object with only that hash changed. */
    @Test
    void withSha256_knownAlias_updatesHash() throws IOException {
        Path file = writeProps(
                "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n");

        DepsProperties original = DepsProperties.read(file);
        DepsProperties updated = original.withSha256("guava", "deadbeef");

        // Original must remain unchanged
        assertNull(original.getEntry("guava").getSha256());
        // New instance must have the new hash
        assertEquals("deadbeef", updated.getEntry("guava").getSha256());
    }

    /** Updating a non-existent alias must throw {@link IllegalArgumentException}. */
    @Test
    void withSha256_unknownAlias_throwsIllegalArgument() throws IOException {
        Path file = writeProps("guava.id=com.google.guava:guava:33.0.0-jre\n");
        DepsProperties deps = DepsProperties.read(file);

        assertThrows(IllegalArgumentException.class,
                () -> deps.withSha256("nonexistent", "abc"));
    }

    // -------------------------------------------------------------------------
    // Entry.toJarUrl()
    // -------------------------------------------------------------------------

    /** {@link DepsProperties.Entry#toJarUrl()} must throw when repo is null. */
    @Test
    void entry_toJarUrl_nullRepo_throwsIllegalState() throws IOException {
        Path file = writeProps("guava.id=com.google.guava:guava:33.0.0-jre\n");
        DepsProperties deps = DepsProperties.read(file);

        assertThrows(IllegalStateException.class,
                () -> deps.getEntry("guava").toJarUrl());
    }

    /** {@link DepsProperties.Entry#toJarUrl()} must return a proper URL when repo is set. */
    @Test
    void entry_toJarUrl_withRepo_returnsFullUrl() throws IOException {
        Path file = writeProps(
                "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n");

        DepsProperties deps = DepsProperties.read(file);
        String url = deps.getEntry("guava").toJarUrl();

        assertTrue(url.startsWith("https://repo1.maven.org/maven2/"));
        assertTrue(url.endsWith(".jar"));
    }

    // -------------------------------------------------------------------------
    // write()
    // -------------------------------------------------------------------------

    /** Writing and re-reading a table must produce identical entries. */
    @Test
    void write_roundTrip_preservesAllFields() throws IOException {
        Path original = writeProps(
                "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n"
                        + "guava.sha256=abc123\n");

        DepsProperties deps = DepsProperties.read(original);
        Path written = tempDir.resolve("written.properties");
        deps.write(written);
        DepsProperties reread = DepsProperties.read(written);

        assertEquals(deps.getAliases(), reread.getAliases());
        DepsProperties.Entry e = reread.getEntry("guava");
        assertEquals("com.google.guava", e.getSpec().getGroupId());
        assertEquals("https://repo1.maven.org/maven2", e.getRepo());
        assertEquals("abc123", e.getSha256());
    }

    /** The written file must contain the standard header comment. */
    @Test
    void write_outputContainsHeaderComment() throws IOException {
        Path file = writeProps("guava.id=com.google.guava:guava:33.0.0-jre\n");
        DepsProperties deps = DepsProperties.read(file);

        Path out = tempDir.resolve("out.properties");
        deps.write(out);
        String content = Files.readString(out);

        assertTrue(content.contains("bdt update"), "header comment should mention 'bdt update'");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Writes raw text to a temp file and returns its path. */
    private Path writeProps(String content) throws IOException {
        Path file = tempDir.resolve("deps.properties");
        Files.writeString(file, content);
        return file;
    }
}
