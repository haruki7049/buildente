package dev.haruki7049.buildente;

import java.util.List;
import java.util.ArrayList;

// Step for compiling Java source code
public class Executable extends Step {
    private String sourceFile;

    public Executable(String name, String sourceFile) {
        super("Compile: " + name);
        this.sourceFile = sourceFile;
    }

    @Override
    protected void execute() {
        System.out.println("Compiling " + this.sourceFile + "...");
        // Use javax.tools.JavaCompiler or ProcessBuilder to run `javac` here
    }
}
