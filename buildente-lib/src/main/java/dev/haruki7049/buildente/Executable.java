package dev.haruki7049.buildente;

import java.io.File;

/**
 * A build step that compiles a Java source file into bytecode using {@code javac}.
 *
 * <p>Corresponds to {@code b.addExecutable(name, source)} in the Buildente API,
 * mirroring {@code std.Build.addExecutable} from the Zig build system.
 *
 * <p>Compiled {@code .class} files are placed under {@value #OUTPUT_DIR}.
 */
public class Executable extends Step {

    /** Directory where all compiled class files are placed. */
    public static final String OUTPUT_DIR = "build/classes/java";

    /**
     * The original logical name of this executable (e.g. {@code "HelloWorld"}).
     * This is the fully-qualified class name used by {@link RunStep} to invoke the program.
     */
    private final String executableName;

    /** Path to the Java source file to compile (e.g. {@code "src/HelloWorld.java"}). */
    private final String sourceFile;

    /**
     * Creates an executable step.
     *
     * @param name       the fully-qualified class name of the program (e.g. {@code "com.example.App"})
     * @param sourceFile path to the Java source file relative to the working directory
     */
    public Executable(String name, String sourceFile) {
        super("compile:" + name);
        this.executableName = name;
        this.sourceFile = sourceFile;
    }

    /**
     * Returns the logical name (class name) of this executable.
     * Used by {@link RunStep} to construct the {@code java} command.
     *
     * @return the class name, e.g. {@code "HelloWorld"}
     */
    public String getExecutableName() {
        return executableName;
    }

    /**
     * Invokes {@code javac} via {@link ProcessBuilder} to compile {@link #sourceFile}.
     * Output is placed in {@value #OUTPUT_DIR}.
     *
     * @throws RuntimeException if compilation fails or the process is interrupted
     */
    @Override
    protected void execute() {
        System.out.println("[buildente] Compiling " + sourceFile + " ...");

        // Ensure the output directory exists before invoking javac
        new File(OUTPUT_DIR).mkdirs();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "javac",
                "-d", OUTPUT_DIR,
                sourceFile
            );
            pb.inheritIO();

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException(
                    "[buildente] javac exited with code " + exitCode
                    + " for source: " + sourceFile
                );
            }

            System.out.println("[buildente] Compiled -> " + OUTPUT_DIR);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[buildente] Compilation interrupted: " + sourceFile, e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("[buildente] Failed to compile: " + sourceFile, e);
        }
    }
}
