package dev.haruki7049.buildente.deps;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Downloads artifact JARs from Maven repositories, verifies their SHA-256 digests, and stores them
 * in a content-addressed local cache.
 *
 * <h2>Cache structure</h2>
 *
 * <p>JARs are cached under {@code ~/.buildente/cache/} using the SHA-256 hash as the filename:
 *
 * <pre>
 *   ~/.buildente/cache/
 *     3fd4341776428c7e0e5c18a7c10de129475b69ab9d30aeafbb5c277bb6074fa9.jar
 * </pre>
 *
 * <p>This is content-addressed storage: if a file already exists at the expected SHA-256 path, the
 * download is skipped entirely. This mirrors how Zig's package cache works.
 *
 * <h2>Security</h2>
 *
 * <p>Every downloaded JAR is verified against the {@code sha256} recorded in {@code
 * deps.properties}. A mismatch aborts the build immediately. The cached file is only written once
 * the hash is confirmed, so a partial download cannot corrupt the cache.
 */
public final class DependencyFetcher {

  private static final Logger LOGGER = Logger.getLogger(DependencyFetcher.class.getName());

  /**
   * Subdirectory under the user's home directory used as the cache root. Mirrors how Zig stores its
   * package cache under {@code ~/.cache/zig/}.
   */
  private static final String CACHE_DIR_NAME = ".buildente/cache";

  /** Buffer size for streaming downloads and hash computation. */
  private static final int BUFFER_SIZE = 64 * 1024;

  // Utility class — no instances
  private DependencyFetcher() {}

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  /**
   * Fetches all packages declared in {@code deps} and returns a map from alias to the local cached
   * JAR {@link Path}.
   *
   * <p>Entries without a {@code sha256} value are rejected immediately — they have not been locked
   * yet. Run {@code bdt update} to populate them.
   *
   * @param deps the parsed {@code deps.properties} file
   * @return map from alias to the absolute path of the cached JAR
   * @throws DependencyFetchException if any entry lacks a hash, any download fails, or any hash
   *     does not match
   */
  public static Map<String, Path> fetchAll(DepsProperties deps) {
    Path cacheDir = resolveCacheDir();
    ensureDirectory(cacheDir);

    Map<String, Path> result = new LinkedHashMap<>();
    for (String alias : deps.getAliases()) {
      DepsProperties.Entry entry = deps.getEntry(alias);
      LOGGER.info("Resolving dependency: " + alias);
      Path jar = fetchOne(alias, entry, cacheDir);
      result.put(alias, jar);
    }
    return result;
  }

  /**
   * Downloads a single artifact JAR, verifies it, and returns the path to the cached copy.
   *
   * <p>If the cache already holds a file whose name equals the expected SHA-256, the download is
   * skipped and the cached file is returned immediately.
   *
   * @param alias the dependency alias (used only for log messages and error context)
   * @param entry the {@link DepsProperties.Entry} carrying the JAR URL and expected SHA-256
   * @param cacheDir local directory where cached JARs are stored
   * @return path to the verified cached JAR
   * @throws DependencyFetchException if the entry has no hash, the download fails, or the hash does
   *     not match
   */
  public static Path fetchOne(String alias, DepsProperties.Entry entry, Path cacheDir) {
    if (entry.getSha256() == null) {
      throw new DependencyFetchException(
          "Dependency '"
              + alias
              + "' has no sha256 in deps.properties.\n"
              + "  Run 'bdt update' to compute and record it.");
    }

    Path cachedJar = cacheDir.resolve(entry.getSha256() + ".jar");

    if (Files.exists(cachedJar)) {
      LOGGER.info("  cache hit  -> " + cachedJar.getFileName());
      return cachedJar;
    }

    String url = entry.toJarUrl();
    LOGGER.info("  downloading " + url);
    Path tempFile = cacheDir.resolve(entry.getSha256() + ".jar.tmp");

    try {
      download(url, tempFile);
      String actualHash = sha256Hex(tempFile);

      if (!actualHash.equalsIgnoreCase(entry.getSha256())) {
        Files.deleteIfExists(tempFile);
        throw new DependencyFetchException(
            "SHA-256 mismatch for '"
                + alias
                + "'!\n"
                + "  expected: "
                + entry.getSha256()
                + "\n"
                + "  actual:   "
                + actualHash
                + "\n"
                + "The sha256 in deps.properties may be wrong. Run 'bdt update' to recompute it.");
      }

      // Move temp → final cache location atomically
      Files.move(tempFile, cachedJar, StandardCopyOption.ATOMIC_MOVE);
      LOGGER.info("  cached     -> " + cachedJar.getFileName());
      return cachedJar;

    } catch (DependencyFetchException e) {
      throw e;
    } catch (Exception e) {
      throw new DependencyFetchException("Failed to fetch '" + alias + "': " + e.getMessage(), e);
    } finally {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException ignored) {
        // Best-effort cleanup
      }
    }
  }

  // -------------------------------------------------------------------------
  // Hash computation (used by Updater when filling in sha256 entries)
  // -------------------------------------------------------------------------

  /**
   * Downloads a JAR from {@code url} into a temporary file, computes its SHA-256 hex digest, and
   * deletes the temporary file.
   *
   * <p>Called by {@link Updater} when populating {@code sha256} entries in {@code deps.properties}.
   *
   * @param url the URL to download from
   * @param cacheDir directory to use for the temporary download
   * @return the hex-encoded SHA-256 digest of the downloaded bytes
   * @throws DependencyFetchException if the download or hash computation fails
   */
  public static String computeRemoteSha256(String url, Path cacheDir) {
    ensureDirectory(cacheDir);
    Path tempFile = cacheDir.resolve("update-" + System.nanoTime() + ".tmp");
    try {
      download(url, tempFile);
      String hash = sha256Hex(tempFile);
      Files.deleteIfExists(tempFile);
      return hash;
    } catch (DependencyFetchException e) {
      throw e;
    } catch (Exception e) {
      throw new DependencyFetchException("Failed to download " + url + ": " + e.getMessage(), e);
    } finally {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException ignored) {
        // Best-effort cleanup
      }
    }
  }

  // -------------------------------------------------------------------------
  // Internals
  // -------------------------------------------------------------------------

  /**
   * Streams the content of {@code url} into {@code dest}.
   *
   * @param url HTTP/HTTPS URL to download
   * @param dest local path to write to
   * @throws IOException if the connection fails or the server returns a non-2xx status
   */
  private static void download(String url, Path dest) throws IOException {
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(30_000);
    conn.setReadTimeout(120_000);
    conn.connect();

    int status = conn.getResponseCode();
    if (status < 200 || status >= 300) {
      throw new IOException("HTTP " + status + " fetching " + url);
    }

    try (InputStream in = conn.getInputStream();
        OutputStream out = Files.newOutputStream(dest)) {
      byte[] buf = new byte[BUFFER_SIZE];
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
      }
    } finally {
      conn.disconnect();
    }
  }

  /**
   * Computes the SHA-256 digest of the file at {@code path} and returns it as a lower-case hex
   * string.
   *
   * @param path the file to hash
   * @return 64-character lower-case hex string
   * @throws IOException if the file cannot be read
   */
  static String sha256Hex(Path path) throws IOException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] buf = new byte[BUFFER_SIZE];
      try (InputStream in = Files.newInputStream(path)) {
        int n;
        while ((n = in.read(buf)) != -1) {
          md.update(buf, 0, n);
        }
      }
      byte[] digest = md.digest();
      StringBuilder sb = new StringBuilder(64);
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("SHA-256 not available", e);
    }
  }

  /**
   * Returns the path to the Buildente JAR cache directory.
   *
   * @return {@code ~/.buildente/cache}
   */
  private static Path resolveCacheDir() {
    return Path.of(System.getProperty("user.home"), CACHE_DIR_NAME);
  }

  private static void ensureDirectory(Path dir) {
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      throw new DependencyFetchException(
          "Cannot create cache directory: " + dir.toAbsolutePath(), e);
    }
  }

  // -------------------------------------------------------------------------
  // Exception
  // -------------------------------------------------------------------------

  /** Thrown when a dependency cannot be downloaded or its hash does not match. */
  public static final class DependencyFetchException extends RuntimeException {

    /**
     * Constructs a new fetch exception with a detail message.
     *
     * @param message the detail message
     */
    public DependencyFetchException(String message) {
      super(message);
    }

    /**
     * Constructs a new fetch exception with a detail message and a cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public DependencyFetchException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
