package dev.haruki7049.buildente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Step}.
 *
 * <p>Verifies the idempotent execution contract and the depth-first dependency traversal ordering.
 */
class StepTest {

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /**
   * A concrete {@link Step} implementation that records every invocation of {@link #execute()} into
   * a shared list so tests can inspect execution order.
   */
  private static class RecordingStep extends Step {

    private final List<String> log;

    RecordingStep(String name, List<String> log) {
      super(name, "test step: " + name);
      this.log = log;
    }

    @Override
    protected void execute() {
      log.add(name);
    }
  }

  // -------------------------------------------------------------------------
  // Idempotency
  // -------------------------------------------------------------------------

  /** Calling {@link Step#make()} more than once must not re-execute the step. */
  @Test
  void make_calledMultipleTimes_executesOnlyOnce() {
    List<String> log = new ArrayList<>();
    RecordingStep step = new RecordingStep("A", log);

    step.make();
    step.make();
    step.make();

    assertEquals(1, log.size(), "step should execute exactly once regardless of make() calls");
  }

  // -------------------------------------------------------------------------
  // Dependency ordering
  // -------------------------------------------------------------------------

  /** A dependency declared via {@link Step#dependOn(Step)} must run before the dependent step. */
  @Test
  void make_singleDependency_dependencyRunsFirst() {
    List<String> log = new ArrayList<>();
    RecordingStep dep = new RecordingStep("dep", log);
    RecordingStep main = new RecordingStep("main", log);
    main.dependOn(dep);

    main.make();

    assertEquals(List.of("dep", "main"), log);
  }

  /** A linear chain A → B → C must execute in the order C, B, A (post-order). */
  @Test
  void make_chainedDependencies_postOrderExecution() {
    List<String> log = new ArrayList<>();
    RecordingStep c = new RecordingStep("C", log);
    RecordingStep b = new RecordingStep("B", log);
    RecordingStep a = new RecordingStep("A", log);

    b.dependOn(c);
    a.dependOn(b);

    a.make();

    assertEquals(List.of("C", "B", "A"), log);
  }

  /** When a step is a shared dependency of two steps, it must still execute only once. */
  @Test
  void make_sharedDependency_executedOnlyOnce() {
    List<String> log = new ArrayList<>();
    RecordingStep shared = new RecordingStep("shared", log);
    RecordingStep left = new RecordingStep("left", log);
    RecordingStep right = new RecordingStep("right", log);
    RecordingStep top = new RecordingStep("top", log);

    left.dependOn(shared);
    right.dependOn(shared);
    top.dependOn(left);
    top.dependOn(right);

    top.make();

    long sharedCount = log.stream().filter("shared"::equals).count();
    assertEquals(1, sharedCount, "shared dependency must execute exactly once");
  }

  // -------------------------------------------------------------------------
  // dependOn()
  // -------------------------------------------------------------------------

  /** A step with no dependencies must execute without error. */
  @Test
  void make_noDependencies_executesImmediately() {
    List<String> log = new ArrayList<>();
    RecordingStep step = new RecordingStep("standalone", log);

    step.make(); // must not throw

    assertFalse(log.isEmpty());
    assertEquals("standalone", log.get(0));
  }

  /** Multiple dependencies registered on a single step must all run before it. */
  @Test
  void dependOn_multipleDependencies_allRunBeforeDependent() {
    List<String> log = new ArrayList<>();
    RecordingStep d1 = new RecordingStep("d1", log);
    RecordingStep d2 = new RecordingStep("d2", log);
    RecordingStep main = new RecordingStep("main", log);

    main.dependOn(d1);
    main.dependOn(d2);

    main.make();

    // Both deps must appear before "main"
    int mainIdx = log.indexOf("main");
    int d1Idx = log.indexOf("d1");
    int d2Idx = log.indexOf("d2");

    assertEquals(3, log.size());
    assertFalse(d1Idx > mainIdx, "d1 must run before main");
    assertFalse(d2Idx > mainIdx, "d2 must run before main");
  }
}
