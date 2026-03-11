package dev.haruki7049.buildente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ManifestConfig}.
 *
 * <p>Covers validation, attribute storage, serialisation, and the special handling of
 * {@code Manifest-Version}.
 */
class ManifestConfigTest {

    // -------------------------------------------------------------------------
    // setMainClass()
    // -------------------------------------------------------------------------

    /** A null main class must be rejected with an {@link IllegalArgumentException}. */
    @Test
    void setMainClass_null_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManifestConfig().setMainClass(null));
    }

    /** A blank main class must also be rejected. */
    @Test
    void setMainClass_blank_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManifestConfig().setMainClass("  "));
    }

    /** A valid main class must be stored under the {@code Main-Class} key. */
    @Test
    void setMainClass_valid_storedAsMainClassAttribute() {
        ManifestConfig cfg = new ManifestConfig().setMainClass("com.example.Main");

        assertEquals("com.example.Main", cfg.getAttributes().get("Main-Class"));
    }

    // -------------------------------------------------------------------------
    // setClassPath()
    // -------------------------------------------------------------------------

    /** A null class-path must be rejected. */
    @Test
    void setClassPath_null_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManifestConfig().setClassPath(null));
    }

    /** A blank class-path must be rejected. */
    @Test
    void setClassPath_blank_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManifestConfig().setClassPath("  "));
    }

    /** A valid class-path string must be stored under the {@code Class-Path} key. */
    @Test
    void setClassPath_valid_storedAsClassPathAttribute() {
        ManifestConfig cfg = new ManifestConfig().setClassPath("lib/foo.jar lib/bar.jar");

        assertEquals("lib/foo.jar lib/bar.jar", cfg.getAttributes().get("Class-Path"));
    }

    // -------------------------------------------------------------------------
    // addAttribute()
    // -------------------------------------------------------------------------

    /** A null key must be rejected. */
    @Test
    void addAttribute_nullKey_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManifestConfig().addAttribute(null, "value"));
    }

    /** A blank key must be rejected. */
    @Test
    void addAttribute_blankKey_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManifestConfig().addAttribute("  ", "value"));
    }

    /** A null value must be rejected. */
    @Test
    void addAttribute_nullValue_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new ManifestConfig().addAttribute("Built-By", null));
    }

    /** Attempting to set {@code Manifest-Version} must be silently ignored. */
    @Test
    void addAttribute_manifestVersionKey_silentlyIgnored() {
        ManifestConfig cfg = new ManifestConfig().addAttribute("Manifest-Version", "2.0");

        // The key must not appear in the attributes map
        assertTrue(cfg.getAttributes().isEmpty(),
                "Manifest-Version should never be stored in the attributes map");
    }

    /** The same key set twice must keep the last value (last-write-wins). */
    @Test
    void addAttribute_duplicateKey_lastValueWins() {
        ManifestConfig cfg = new ManifestConfig()
                .addAttribute("Built-By", "first")
                .addAttribute("Built-By", "second");

        assertEquals("second", cfg.getAttributes().get("Built-By"));
    }

    // -------------------------------------------------------------------------
    // writeTo()
    // -------------------------------------------------------------------------

    /** The first line of the manifest must always be {@code Manifest-Version: 1.0}. */
    @Test
    void writeTo_firstLineIsManifestVersion() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ManifestConfig().writeTo(out);
        String content = out.toString(StandardCharsets.UTF_8);

        assertTrue(content.startsWith("Manifest-Version: 1.0"),
                "First line must be 'Manifest-Version: 1.0'");
    }

    /** Custom attributes must appear in the output after the version line. */
    @Test
    void writeTo_customAttributeAppearsInOutput() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ManifestConfig()
                .setMainClass("com.example.Main")
                .addAttribute("Built-By", "Buildente")
                .writeTo(out);
        String content = out.toString(StandardCharsets.UTF_8);

        assertTrue(content.contains("Main-Class: com.example.Main"));
        assertTrue(content.contains("Built-By: Buildente"));
    }

    /** The manifest output must end with a blank line as required by the JAR spec. */
    @Test
    void writeTo_endsWithBlankLine() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ManifestConfig().writeTo(out);
        String content = out.toString(StandardCharsets.UTF_8);

        assertTrue(content.endsWith("\n\n") || content.endsWith("\r\n\r\n"),
                "Manifest must end with a blank line");
    }

    // -------------------------------------------------------------------------
    // toString()
    // -------------------------------------------------------------------------

    /** {@link ManifestConfig#toString()} must mention {@code Manifest-Version} and attributes. */
    @Test
    void toString_containsVersionAndAttributes() {
        ManifestConfig cfg = new ManifestConfig().setMainClass("com.example.Main");
        String s = cfg.toString();

        assertTrue(s.contains("Manifest-Version"), "toString must include Manifest-Version");
        assertTrue(s.contains("Main-Class"), "toString must include Main-Class");
    }
}
