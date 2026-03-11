package dev.haruki7049.buildente;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for all build steps.
 *
 * <p>Steps form a directed acyclic graph (DAG). When {@link #make()} is called, all dependencies
 * are executed first (post-order traversal), guaranteeing that each step runs exactly once per
 * build invocation.
 *
 * <p>This design is inspired by the Zig build system's {@code std.Build.Step}.
 */
public abstract class Step {

  /** Human-readable name used in logs and dependency resolution. */
  public final String name;

  /** Optional description shown in help output. */
  public final String description;

  /** Ordered list of steps that must complete before this one. */
  protected final List<Step> dependencies = new ArrayList<>();

  /** Guards against duplicate execution within a single build run. */
  private boolean executed = false;

  /**
   * Creates a step with a name but no description.
   *
   * @param name human-readable identifier for this step
   */
  public Step(String name) {
    this(name, "");
  }

  /**
   * Creates a step with both a name and a description.
   *
   * @param name human-readable identifier for this step
   * @param description short description shown in help output
   */
  public Step(String name, String description) {
    this.name = name;
    this.description = description;
  }

  /**
   * Declares that this step depends on another step. The dependency will be executed before this
   * step.
   *
   * @param step the step that must complete first
   */
  public void dependOn(Step step) {
    this.dependencies.add(step);
  }

  /**
   * Executes this step and all of its transitive dependencies.
   *
   * <p>Dependencies are resolved depth-first. This method is idempotent: calling it multiple times
   * on the same step has no additional effect.
   */
  public final void make() {
    if (this.executed) {
      return;
    }

    // Resolve dependencies first (post-order / bottom-up traversal)
    for (Step dep : this.dependencies) {
      dep.make();
    }

    this.execute();
    this.executed = true;
  }

  /** Contains the actual work performed by this step. Subclasses must implement this method. */
  protected abstract void execute();
}
