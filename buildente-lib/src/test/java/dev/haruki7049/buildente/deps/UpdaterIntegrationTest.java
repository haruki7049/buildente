package dev.haruki7049.buildente.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for {@link Updater#run(Path)}.
 *
 * <p>These tests exercise all {@link Updater.Result.Status} branches that can be reached without
 * network access:
 *
 * <ul>
 *   <li>{@link Updater.Result.Status#NO_FILE} — project root has no {@code deps.properties}
 *   <li>{@link Updater.Result.Status#EMPTY} — {@code deps.properties} is present but empty
 *   <li>{@link Updater.Result.Status#NOTHING_TO_UPDATE} — all entries already have a {@code sha256}
 *   <li>{@link Updater.Result.Status#FETCH_ERRORS} — an entry has no {@code .repo} configured
 * </ul>
 *
 * <p>The {@link Updater.Result.Status#SUCCESS} and {@link Updater.Result.Status#WRITE_ERROR}
 * statuses require either a live network or OS-level permission manipulation and are therefore
 * covered by separate end-to-end or manual tests.
 */
class UpdaterIntegrationTest {

    @TempDir
    Path projectRoot;

    // -------------------------------------------------------------------------
    // NO_FILE
    // -------------------------------------------------------------------------

    /**
     * When no {@code deps.properties} file exists in the project root, the result status must be
     * {@link Updater.Result.Status#NO_FILE} and the run must be considered successful.
     */
    @Test
    void run_noFile_returnsNoFileStatus() {
        Updater.Result result = Updater.run(projectRoot);

        assertEquals(Updater.Result.Status.NO_FILE, result.getStatus());
        assertTrue(result.isSuccess(), "NO_FILE should be treated as a successful (no-op) result");
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getErrors());
    }

    // -------------------------------------------------------------------------
    // EMPTY
    // -------------------------------------------------------------------------

    /**
     * When {@code deps.properties} exists but contains no entries, the status must be
     * {@link Updater.Result.Status#EMPTY} and the run must be considered successful.
     */
    @Test
    void run_emptyFile_returnsEmptyStatus() throws IOException {
        createDepsFile("");

        Updater.Result result = Updater.run(projectRoot);

        assertEquals(Updater.Result.Status.EMPTY, result.getStatus());
        assertTrue(result.isSuccess());
    }

    /**
     * A file containing only a comment (no dependency entries) must also yield
     * {@link Updater.Result.Status#EMPTY}.
     */
    @Test
    void run_commentOnlyFile_returnsEmptyStatus() throws IOException {
        createDepsFile("# nothing here\n");

        Updater.Result result = Updater.run(projectRoot);

        assertEquals(Updater.Result.Status.EMPTY, result.getStatus());
    }

    // -------------------------------------------------------------------------
    // NOTHING_TO_UPDATE
    // -------------------------------------------------------------------------

    /**
     * When every entry in {@code deps.properties} already has a {@code sha256} value, no
     * downloads should occur and the status must be
     * {@link Updater.Result.Status#NOTHING_TO_UPDATE}.
     */
    @Test
    void run_allEntriesAlreadyHaveSha256_returnsNothingToUpdate() throws IOException {
        createDepsFile(
                "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n"
                        + "guava.sha256=aabbccddeeff\n");

        Updater.Result result = Updater.run(projectRoot);

        assertEquals(Updater.Result.Status.NOTHING_TO_UPDATE, result.getStatus());
        assertTrue(result.isSuccess());
        assertEquals(0, result.getUpdated());
        assertEquals(1, result.getSkipped(),
                "the one already-hashed entry should be counted as skipped");
    }

    /**
     * Multiple entries all with pre-existing hashes must all be counted as skipped.
     */
    @Test
    void run_multipleEntriesAllHashed_skippedCountMatchesEntryCount() throws IOException {
        createDepsFile(
                "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n"
                        + "guava.sha256=aaa\n"
                        + "slf4j.id=org.slf4j:slf4j-api:2.0.13\n"
                        + "slf4j.repo=https://repo1.maven.org/maven2\n"
                        + "slf4j.sha256=bbb\n");

        Updater.Result result = Updater.run(projectRoot);

        assertEquals(Updater.Result.Status.NOTHING_TO_UPDATE, result.getStatus());
        assertEquals(2, result.getSkipped());
        assertEquals(0, result.getUpdated());
    }

    // -------------------------------------------------------------------------
    // FETCH_ERRORS
    // -------------------------------------------------------------------------

    /**
     * An entry whose {@code sha256} is absent AND whose {@code repo} is also absent cannot be
     * resolved. The run must report {@link Updater.Result.Status#FETCH_ERRORS} and
     * {@link Updater.Result#isSuccess()} must return false.
     */
    @Test
    void run_entryMissingRepo_returnsFetchErrors() throws IOException {
        createDepsFile("guava.id=com.google.guava:guava:33.0.0-jre\n");
        // No guava.repo, no guava.sha256 → can't resolve URL

        Updater.Result result = Updater.run(projectRoot);

        assertEquals(Updater.Result.Status.FETCH_ERRORS, result.getStatus());
        assertFalse(result.isSuccess());
        assertEquals(1, result.getErrors());
    }

    /**
     * When one entry has a hash and another is missing its repo, the result must still be
     * {@link Updater.Result.Status#FETCH_ERRORS} with error count of 1 and skipped count of 1.
     */
    @Test
    void run_mixedHashedAndMissingRepo_fetchErrorsWithSkippedCount() throws IOException {
        createDepsFile(
                "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n"
                        + "guava.sha256=aaa\n"
                        + "missing.id=org.example:missing-lib:1.0\n");
        // 'missing' has no .repo and no .sha256

        Updater.Result result = Updater.run(projectRoot);

        assertEquals(Updater.Result.Status.FETCH_ERRORS, result.getStatus());
        assertEquals(1, result.getErrors());
        assertEquals(1, result.getSkipped());
    }

    // -------------------------------------------------------------------------
    // Log content
    // -------------------------------------------------------------------------

    /**
     * The result log must mention alias names so users can identify what was processed.
     */
    @Test
    void run_nothingToUpdate_logMentionsAlias() throws IOException {
        createDepsFile(
                "guava.id=com.google.guava:guava:33.0.0-jre\n"
                        + "guava.repo=https://repo1.maven.org/maven2\n"
                        + "guava.sha256=aaa\n");

        Updater.Result result = Updater.run(projectRoot);

        assertTrue(result.getLog().contains("guava"),
                "log should mention the alias that was processed");
    }

    /**
     * The result log must never be null, even for statuses that produce no log output.
     */
    @Test
    void run_noFile_logIsNeverNull() {
        Updater.Result result = Updater.run(projectRoot);
        assertFalse(result.getLog() == null, "log must never be null");
    }

    // -------------------------------------------------------------------------
    // READ_ERROR
    // -------------------------------------------------------------------------

    /**
     * A {@code deps.properties} file that contains a malformed GAV string must produce a
     * {@link Updater.Result.Status#READ_ERROR} result, because {@link DepsProperties#read} will
     * throw an {@link IllegalArgumentException} which the Updater catches.
     */
    @Test
    void run_malformedGav_returnsReadError() throws IOException {
        createDepsFile("bad.id=notAGavString\n");

        Updater.Result result = Updater.run(projectRoot);

        assertEquals(Updater.Result.Status.READ_ERROR, result.getStatus());
        assertFalse(result.isSuccess());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void createDepsFile(String content) throws IOException {
        Files.writeString(projectRoot.resolve(DepsProperties.FILE_NAME), content);
    }
}
