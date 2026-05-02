package dev.haruki7049.buildente.deps;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Implements the {@code bdt update} logic: reads {@code deps.properties}, downloads JARs for any
 * entry that is missing a {@code sha256}, computes the hash, and writes the value back into the
 * same file.
 *
 * <p>This class belongs to {@code buildente-lib} and has no dependency on any CLI framework. The
 * thin picocli wrapper ({@code UpdateCommand} in {@code buildente-app}) delegates entirely to
 * {@link #run(Path)}.
 *
 * <h2>Algorithm</h2>
 *
 * <ol>
 *   <li>Parse {@code deps.properties} from {@code projectRoot}.
 *   <li>For each alias whose {@code sha256} is blank: download the JAR from {@code <alias>.repo}
 *       and compute the SHA-256.
 *   <li>Write the computed hashes back into {@code deps.properties} (entries that already had a
 *       hash are left untouched).
 * </ol>
 *
 * <p><strong>Note:</strong> transitive dependencies are not resolved automatically. Every JAR
 * required at compile or runtime must be listed explicitly in {@code deps.properties}. This is
 * intentional — it keeps the dependency graph auditable, matching the explicit-over-implicit
 * philosophy of the Zig build system.
 */
public final class Updater {

  // Utility class — no instances
  private Updater() {}

  /**
   * Runs the update lifecycle for the project rooted at {@code projectRoot}.
   *
   * @param projectRoot directory that contains (or should contain) {@code deps.properties}
   * @return a {@link Result} describing what happened
   */
  public static Result run(Path projectRoot) {
    Path depsPath = projectRoot.resolve(DepsProperties.FILE_NAME);

    if (!Files.exists(depsPath)) {
      return Result.noFile();
    }

    DepsProperties deps;
    try {
      deps = DepsProperties.read(depsPath);
    } catch (Exception e) {
      return Result.readError(e);
    }

    if (deps.getAliases().isEmpty()) {
      return Result.empty();
    }

    // Temporary directory for hash-computation downloads
    Path tempCacheDir = projectRoot.resolve(".buildente").resolve("update-tmp");

    int updated = 0;
    int skipped = 0;
    int errors = 0;
    StringBuilder log = new StringBuilder();

    for (String alias : deps.getAliases()) {
      DepsProperties.Entry entry = deps.getEntry(alias);

      if (entry.getSha256() != null) {
        log.append("").append(alias).append(": sha256 already present, skipping.\n");
        skipped++;
        continue;
      }

      if (entry.getRepo() == null) {
        log.append("")
            .append(alias)
            .append(": missing '")
            .append(alias)
            .append(".repo' — cannot resolve URL.\n");
        errors++;
        continue;
      }

      String url = entry.toJarUrl();
      log.append("").append(alias).append(": fetching ").append(url).append('\n');

      try {
        String hash = DependencyFetcher.computeRemoteSha256(url, tempCacheDir);
        log.append("").append(alias).append(": sha256 = ").append(hash).append('\n');
        deps = deps.withSha256(alias, hash);
        updated++;
      } catch (DependencyFetcher.DependencyFetchException e) {
        log.append("")
            .append(alias)
            .append(": ERROR — ")
            .append(e.getMessage())
            .append('\n');
        errors++;
      }
    }

    // Clean up temporary download directory
    try {
      if (Files.exists(tempCacheDir)) {
        Files.walk(tempCacheDir)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(p -> p.toFile().delete());
      }
    } catch (Exception ignored) {
      // Best-effort cleanup
    }

    if (errors > 0) {
      return Result.fetchErrors(updated, skipped, errors, log.toString());
    }

    if (updated == 0) {
      return Result.nothingToUpdate(skipped, log.toString());
    }

    try {
      deps.write(depsPath);
    } catch (Exception e) {
      return Result.writeError(e);
    }

    return Result.success(updated, skipped, log.toString());
  }

  // -------------------------------------------------------------------------
  // Result value type
  // -------------------------------------------------------------------------

  /**
   * Describes the outcome of a {@link #run(Path)} call. Callers (e.g. {@code UpdateCommand}) use
   * this to decide what to print and which exit code to return without coupling the update logic to
   * any I/O concern.
   */
  public static final class Result {

    /** Possible outcomes. */
    public enum Status {
      /** {@code deps.properties} was not found. */
      NO_FILE,
      /** {@code deps.properties} could not be parsed. */
      READ_ERROR,
      /** {@code deps.properties} exists but contains no entries. */
      EMPTY,
      /** One or more JARs could not be downloaded or hashed. */
      FETCH_ERRORS,
      /** All entries already had hashes; nothing to write. */
      NOTHING_TO_UPDATE,
      /** {@code deps.properties} could not be written back. */
      WRITE_ERROR,
      /** At least one hash was computed and written. */
      SUCCESS
    }

    private final Status status;
    private final int updated;
    private final int skipped;
    private final int errors;
    private final String log;
    private final Exception cause;

    private Result(
        Status status, int updated, int skipped, int errors, String log, Exception cause) {
      this.status = status;
      this.updated = updated;
      this.skipped = skipped;
      this.errors = errors;
      this.log = log != null ? log : "";
      this.cause = cause;
    }

    static Result noFile() {
      return new Result(Status.NO_FILE, 0, 0, 0, "", null);
    }

    static Result readError(Exception e) {
      return new Result(Status.READ_ERROR, 0, 0, 0, "", e);
    }

    static Result empty() {
      return new Result(Status.EMPTY, 0, 0, 0, "", null);
    }

    static Result fetchErrors(int updated, int skipped, int errors, String log) {
      return new Result(Status.FETCH_ERRORS, updated, skipped, errors, log, null);
    }

    static Result nothingToUpdate(int s, String l) {
      return new Result(Status.NOTHING_TO_UPDATE, 0, s, 0, l, null);
    }

    static Result writeError(Exception e) {
      return new Result(Status.WRITE_ERROR, 0, 0, 0, "", e);
    }

    static Result success(int u, int s, String l) {
      return new Result(Status.SUCCESS, u, s, 0, l, null);
    }

    /**
     * Returns the outcome category.
     *
     * @return the {@link Status} of this result
     */
    public Status getStatus() {
      return status;
    }

    /**
     * Number of {@code sha256} values newly computed and written.
     *
     * @return count of updated entries
     */
    public int getUpdated() {
      return updated;
    }

    /**
     * Number of entries skipped because they already had a hash.
     *
     * @return count of skipped entries
     */
    public int getSkipped() {
      return skipped;
    }

    /**
     * Number of aliases that could not be resolved due to errors.
     *
     * @return count of errored entries
     */
    public int getErrors() {
      return errors;
    }

    /**
     * Accumulated human-readable log lines produced during resolution.
     *
     * @return the log string, never {@code null}
     */
    public String getLog() {
      return log;
    }

    /**
     * The underlying exception, or {@code null} when not applicable.
     *
     * @return the cause exception, or {@code null}
     */
    public Exception getCause() {
      return cause;
    }

    /**
     * Returns {@code true} when the exit code should be zero.
     *
     * @return {@code true} if the update succeeded or was a no-op
     */
    public boolean isSuccess() {
      return status == Status.SUCCESS
          || status == Status.NOTHING_TO_UPDATE
          || status == Status.EMPTY
          || status == Status.NO_FILE;
    }
  }
}
