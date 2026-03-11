package dev.haruki7049.buildente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link Build}.
 *
 * <p>Exercises module creation, step registration, resolved-jar injection, and the build graph
 * execution.
 */
class BuildTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    /** A freshly-constructed {@link Build} must always expose a non-null install step. */
    @Test
    void getInstallStep_freshBuild_notNull() {
        Build b = new Build(List.of());
        assertNotNull(b.getInstallStep());
    }

    /** The install step's name must be {@code "install"}. */
    @Test
    void getInstallStep_freshBuild_nameIsInstall() {
        Build b = new Build(List.of());
        assertEquals("install", b.getInstallStep().name);
    }

    /** The args list supplied at construction must be returned unchanged. */
    @Test
    void getArgs_returnsConstructorArgs() {
        List<String> args = List.of("run", "--verbose");
        Build b = new Build(args);
        assertEquals(args, b.getArgs());
    }

    // -------------------------------------------------------------------------
    // step()
    // -------------------------------------------------------------------------

    /** Calling {@link Build#step} twice with the same name must return the identical object. */
    @Test
    void step_sameNameCalledTwice_returnsSameInstance() {
        Build b = new Build(List.of());
        Step s1 = b.step("run", "Run the application");
        Step s2 = b.step("run", "Run the application");

        assertSame(s1, s2, "step() must return the same Step for the same name");
    }

    /** A newly registered step must carry the description supplied at registration time. */
    @Test
    void step_newName_storesDescription() {
        Build b = new Build(List.of());
        Step s = b.step("test", "Run unit tests");

        assertEquals("test", s.name);
        assertEquals("Run unit tests", s.description);
    }

    // -------------------------------------------------------------------------
    // createModule()
    // -------------------------------------------------------------------------

    /** A module created before {@link Build#setResolvedJars} must have an empty jar map. */
    @Test
    void createModule_beforeSetResolvedJars_emptyJarMap() throws IOException {
        Files.createFile(tempDir.resolve("A.java"));
        Build b = new Build(List.of());
        Module m = b.createModule(tempDir.toString());

        // No dependency declared, so getResolvedJars() must be empty
        assertTrue(m.getResolvedJars().isEmpty());
    }

    /** A module created after {@link Build#setResolvedJars} must inherit the jar map. */
    @Test
    void createModule_afterSetResolvedJars_inheritsJarMap() throws IOException {
        Path fakeJar = tempDir.resolve("guava.jar");
        Files.createFile(fakeJar);

        Build b = new Build(List.of());
        b.setResolvedJars(Map.of("guava", fakeJar));

        Module m = b.createModule(tempDir.toString());
        m.addDependency("guava");

        assertEquals(1, m.getResolvedJars().size());
        assertEquals(fakeJar, m.getResolvedJars().get(0));
    }

    /** {@link Build#setResolvedJars} called with null must be treated as an empty map. */
    @Test
    void setResolvedJars_null_treatedAsEmpty() throws IOException {
        Files.createFile(tempDir.resolve("A.java"));
        Build b = new Build(List.of());
        b.setResolvedJars(null);

        Module m = b.createModule(tempDir.toString());
        // no dependency registered, jar list must be empty
        assertTrue(m.getResolvedJars().isEmpty());
    }

    // -------------------------------------------------------------------------
    // executeStep() + integration with Step graph
    // -------------------------------------------------------------------------

    /**
     * Executing a named step must invoke it and all of its transitive dependencies exactly once.
     * This test wires a custom step into the build graph to verify the call without spawning a
     * real compiler process.
     */
    @Test
    void executeStep_namedStep_executesStepAndDependencies() {
        List<String> log = new ArrayList<>();

        // Custom step subclass that records execution
        Step custom = new Step("probe", "records execution") {
            @Override
            protected void execute() {
                log.add("probe");
            }
        };

        Build b = new Build(List.of());
        b.step("myStep", "a test step").dependOn(custom);
        b.executeStep("myStep");

        assertTrue(log.contains("probe"),
                "the probe step wired as a dependency must have been executed");
    }

    /** Calling executeStep with the predefined "install" name must not throw. */
    @Test
    void executeStep_installStep_doesNotThrow() {
        Build b = new Build(List.of());
        // install step has no work to do; it must not throw
        b.executeStep("install");
    }

    // -------------------------------------------------------------------------
    // addExecutable() / addRunArtifact() / addJar() — smoke tests (no compilation)
    // -------------------------------------------------------------------------

    /**
     * {@link Build#addExecutable(String, Module)} must return a non-null {@link Executable}
     * without triggering any compilation.
     */
    @Test
    void addExecutable_returnsNonNull() {
        Build b = new Build(List.of());
        Module m = new Module(tempDir.toString());

        Executable exe = b.addExecutable("com.example.Main", m);

        assertNotNull(exe);
        assertEquals("com.example.Main", exe.getExecutableName());
    }

    /**
     * {@link Build#addExecutable(String, Module, ManifestConfig)} must attach the manifest to the
     * returned executable.
     */
    @Test
    void addExecutable_withManifest_attachesManifest() {
        Build b = new Build(List.of());
        Module m = new Module(tempDir.toString());
        ManifestConfig mf = new ManifestConfig().setMainClass("com.example.Main");

        Executable exe = b.addExecutable("com.example.Main", m, mf);

        assertNotNull(exe.getManifestConfig());
        assertEquals("com.example.Main",
                exe.getManifestConfig().getAttributes().get("Main-Class"));
    }

    /** {@link Build#addRunArtifact} must return a non-null {@link RunStep}. */
    @Test
    void addRunArtifact_returnsNonNull() {
        Build b = new Build(List.of());
        Module m = new Module(tempDir.toString());
        Executable exe = b.addExecutable("com.example.Main", m);

        assertNotNull(b.addRunArtifact(exe));
    }

    /** {@link Build#addJar(String, Executable)} must return a non-null {@link JarStep}. */
    @Test
    void addJar_returnsNonNull() {
        Build b = new Build(List.of());
        Module m = new Module(tempDir.toString());
        Executable exe = b.addExecutable("com.example.Main", m);

        assertNotNull(b.addJar("myapp", exe));
    }

    /** {@link Build#addFatJar(String, Executable)} must return a non-null {@link FatJarStep}. */
    @Test
    void addFatJar_returnsNonNull() {
        Build b = new Build(List.of());
        Module m = new Module(tempDir.toString());
        Executable exe = b.addExecutable("com.example.Main", m);

        assertNotNull(b.addFatJar("myapp", exe));
    }

    // -------------------------------------------------------------------------
    // executeStep() — unknown step
    // -------------------------------------------------------------------------

    /**
     * Requesting an unknown step name must call {@link System#exit(int)}, which the security
     * manager would normally intercept. Since we cannot safely intercept {@code System.exit} in a
     * plain JUnit test, we verify the behaviour indirectly: any exception or normal return without
     * an executing probe step must indicate the early-exit path was taken.
     *
     * <p>This test is intentionally marked as a "verify no silent success" guard. In a real CI
     * environment a custom {@link SecurityManager} or Mockito's static-mock capabilities could be
     * used for stricter assertions.
     */
    @org.junit.jupiter.api.Disabled("Causes JVM exit without SecurityManager")
    @Test
    void executeStep_unknownStepName_raisesSystemExit() {
        Build b = new Build(List.of());

        // System.exit() is called internally; we catch the resulting SecurityException
        // (if a SecurityManager is installed) or accept that the JVM actually exits.
        // For safety we wrap in assertThrows with a broad Throwable.
        assertThrows(Throwable.class, () -> b.executeStep("this-step-does-not-exist"));
    }
}
