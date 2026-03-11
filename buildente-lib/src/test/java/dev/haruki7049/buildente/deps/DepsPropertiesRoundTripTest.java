package dev.haruki7049.buildente.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for {@link DepsProperties} that exercise the full read → mutate → write →
 * re-read round-trip using the real file system.
 *
 * <p>Unlike the unit tests in {@link DepsPropertiesTest}, these tests verify that the serialised
 * form produced by {@link DepsProperties#write(Path)} is valid input for a subsequent
 * {@link DepsProperties#read(Path)} call, including edge cases such as aliases that contain hyphens
 * and entries whose {@code sha256} is filled in by {@link DepsProperties#withSha256}.
 */
class DepsPropertiesRoundTripTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Single-dependency round-trip
    // -------------------------------------------------------------------------

    /**
     * A single fully-populated entry must survive a write/re-read cycle without data loss.
     */
    @Test
    void roundTrip_singleEntry_allFieldsPreserved() throws IOException {
        Path file = writeDepsFile(
                "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n"
                        + "guava.sha256=aabbcc\n");

        DepsProperties original = DepsProperties.read(file);

        Path out = tempDir.resolve("written.properties");
        original.write(out);

        DepsProperties reread = DepsProperties.read(out);

        assertEquals(original.getAliases(), reread.getAliases());
        assertEntryEquals(original.getEntry("guava"), reread.getEntry("guava"));
    }

    // -------------------------------------------------------------------------
    // Multi-dependency round-trip
    // -------------------------------------------------------------------------

    /**
     * Multiple entries must all survive a write/re-read cycle, and alias order must be preserved
     * (alphabetical).
     */
    @Test
    void roundTrip_multipleEntries_allPreservedInAlphaOrder() throws IOException {
        Path file = writeDepsFile(
                "slf4j.id=org.slf4j:slf4j-api:2.0.13\n"
                        + "slf4j.repo=https://repo1.maven.org/maven2\n"
                        + "slf4j.sha256=zzz\n"
                        + "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n"
                        + "guava.sha256=aabbcc\n");

        DepsProperties original = DepsProperties.read(file);
        Path out = tempDir.resolve("written.properties");
        original.write(out);
        DepsProperties reread = DepsProperties.read(out);

        // Aliases must be in alphabetical order after the round-trip
        assertEquals(List.of("guava", "slf4j"), reread.getAliases());
        assertEntryEquals(original.getEntry("guava"), reread.getEntry("guava"));
        assertEntryEquals(original.getEntry("slf4j"), reread.getEntry("slf4j"));
    }

    // -------------------------------------------------------------------------
    // withSha256 + write round-trip
    // -------------------------------------------------------------------------

    /**
     * An entry whose {@code sha256} was null and is then filled by {@link
     * DepsProperties#withSha256} must be written and re-read with the new hash.
     */
    @Test
    void roundTrip_withSha256ThenWrite_hashPersistedOnDisk() throws IOException {
        Path file = writeDepsFile(
                "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n");

        DepsProperties original = DepsProperties.read(file);
        assertNull(original.getEntry("guava").getSha256(), "sha256 should be null before update");

        DepsProperties updated = original.withSha256("guava", "deadbeef1234");
        Path out = tempDir.resolve("updated.properties");
        updated.write(out);

        DepsProperties reread = DepsProperties.read(out);
        assertEquals("deadbeef1234", reread.getEntry("guava").getSha256());
    }

    // -------------------------------------------------------------------------
    // Alias with hyphen
    // -------------------------------------------------------------------------

    /**
     * Aliases that contain a hyphen (e.g. {@code slf4j-api}) must round-trip correctly because
     * hyphens are legal in {@code .properties} keys.
     */
    @Test
    void roundTrip_aliasWithHyphen_preservedCorrectly() throws IOException {
        Path file = writeDepsFile(
                "slf4j-api.id=org.slf4j:slf4j-api:2.0.13\n"
                        + "slf4j-api.repo=https://repo1.maven.org/maven2\n"
                        + "slf4j-api.sha256=cafebabe\n");

        DepsProperties original = DepsProperties.read(file);
        Path out = tempDir.resolve("hyphen.properties");
        original.write(out);
        DepsProperties reread = DepsProperties.read(out);

        assertEquals(List.of("slf4j-api"), reread.getAliases());
        assertEquals("cafebabe", reread.getEntry("slf4j-api").getSha256());
    }

    // -------------------------------------------------------------------------
    // Empty file round-trip
    // -------------------------------------------------------------------------

    /**
     * An empty {@code deps.properties} must write and re-read as empty without throwing.
     */
    @Test
    void roundTrip_emptyFile_remainsEmpty() throws IOException {
        Path file = writeDepsFile("");
        DepsProperties original = DepsProperties.read(file);

        Path out = tempDir.resolve("empty-out.properties");
        original.write(out);
        DepsProperties reread = DepsProperties.read(out);

        assertEquals(original.getAliases(), reread.getAliases());
        assertEquals(0, reread.getAliases().size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Path writeDepsFile(String content) throws IOException {
        Path file = tempDir.resolve("deps.properties");
        Files.writeString(file, content);
        return file;
    }

    private static void assertEntryEquals(
            DepsProperties.Entry expected, DepsProperties.Entry actual) {
        assertEquals(expected.getSpec().getGroupId(), actual.getSpec().getGroupId());
        assertEquals(expected.getSpec().getArtifactId(), actual.getSpec().getArtifactId());
        assertEquals(expected.getSpec().getVersion(), actual.getSpec().getVersion());
        assertEquals(expected.getRepo(), actual.getRepo());
        assertEquals(expected.getSha256(), actual.getSha256());
    }
}
