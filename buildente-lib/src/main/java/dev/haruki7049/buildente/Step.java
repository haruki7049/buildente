package dev.haruki7049.buildente;

import java.util.ArrayList;
import java.util.List;

public abstract class Step {
    public String name;
    protected List<Step> dependencies = new ArrayList<>();
    private boolean isExecuted = false;

    public Step(String name) {
        this.name = name;
    }

    // Add a dependency to this step
    public void dependOn(Step step) {
        this.dependencies.add(step);
    }

    // Evaluate the graph and execute
    public void make() {
        if (this.isExecuted) {
            // Prevent duplicate execution
            return; 
        }
        
        // Resolve dependencies first (Post-order traversal)
        for (Step dep : this.dependencies) {
            dep.make();
        }
        
        // Execute the actual task of this step
        this.execute();
        this.isExecuted = true;
    }

    // Subclasses must implement actual logic here
    protected abstract void execute();
}
