package dev.haruki7049.buildente;

import java.util.List;
import java.util.ArrayList;

// Step for running the compiled artifact
public class RunStep extends Step {
    private Executable executable;
    private List<String> args = new ArrayList<>();

    public RunStep(Executable executable) {
        super("Run: " + executable.name);
        this.executable = executable;
        // Automatically depend on compilation
        this.dependOn(executable); 
    }

    public void addArgs(List<String> newArgs) {
        this.args.addAll(newArgs);
    }

    @Override
    protected void execute() {
        System.out.println("Running " + this.executable.name + "...");
        // Use ProcessBuilder to run `java` command here
    }
}
