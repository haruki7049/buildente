package dev.haruki7049.buildente;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class Build {
    private Map<String, Step> steps = new HashMap<>();
    private Step defaultStep;
    private List<String> args; // Command line args
    
    public Build(List<String> args) {
        this.args = args;
        this.defaultStep = new NamedStep("install");
        this.steps.put("install", this.defaultStep);
    }

    // API corresponding to b.addExecutable
    public Executable addExecutable(String name, String source) {
        return new Executable(name, source);
    }

    // API corresponding to b.addRunArtifact
    public RunStep addRunArtifact(Executable exe) {
        return new RunStep(exe);
    }

    // API corresponding to b.step
    public Step step(String name, String description) {
        return this.steps.computeIfAbsent(name, k -> new NamedStep(name));
    }

    public Step getInstallStep() {
        return this.defaultStep;
    }

    public List<String> getArgs() {
        return this.args;
    }

    // Internal step implementation for conceptual grouping (like "run" or "test")
    private class NamedStep extends Step {
        public NamedStep(String name) {
            super(name);
        }
        @Override
        protected void execute() {
            // Do nothing, just a conceptual node in the graph
        }
    }

    // Called by engine to start execution
    public void executeStep(String stepName) {
        Step target = steps.get(stepName);
        if (target != null) {
            target.make();
        } else {
            System.err.println("Step not found: " + stepName);
        }
    }
}
