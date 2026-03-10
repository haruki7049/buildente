package dev.haruki7049.buildente;

/**
 * Contract that every user-written build script must fulfil.
 *
 * <p>The end goal of Buildente is to let users place a {@code build.java} file
 * at the root of their project. That file must contain a public class named
 * {@code build} that implements this interface:
 *
 * <pre>{@code
 * // build.java  (user-written, lives at the project root)
 * import dev.haruki7049.buildente.*;
 *
 * public class build implements BuildScript {
 *     {@literal @}Override
 *     public void build(Build b) {
 *         Executable exe = b.addExecutable("com.example.App", "src/App.java");
 *         RunStep    run = b.addRunArtifact(exe);
 *         b.getInstallStep().dependOn(exe);
 *         b.step("run", "Compile and run").dependOn(run);
 *     }
 * }
 * }</pre>
 *
 * <p>Buildente's engine ({@code ScriptRunner}) will:
 * <ol>
 *   <li>Locate {@code build.java} in the working directory.</li>
 *   <li>Compile it with {@code javax.tools.JavaCompiler}, injecting the
 *       Buildente library onto the compile-time classpath.</li>
 *   <li>Load the compiled {@code build} class via {@code URLClassLoader}.</li>
 *   <li>Cast it to {@code BuildScript} and call {@link #build(Build)}.</li>
 *   <li>Execute the step requested on the command line.</li>
 * </ol>
 */
public interface BuildScript {

    /**
     * Defines the project's build graph.
     *
     * <p>Implementations should declare {@link Executable}s, {@link RunStep}s,
     * and named top-level steps by calling the factory methods on {@code b},
     * then wire them together with {@link Step#dependOn(Step)}.
     *
     * @param b the build configuration object provided by the Buildente engine
     */
    void build(Build b);
}
