package com.mkylm.intunebulk.cli;

import com.mkylm.intunebulk.core.ActionRequest;
import com.mkylm.intunebulk.core.ActionResult;
import com.mkylm.intunebulk.core.ActionState;
import java.util.List;

/** Shared renderer for non-interactive bulk command result summaries. */
final class BulkOutput {
  static void printSummary(ActionRequest req, List<ActionResult> results) {
    // Aggregate states for a top-level operator summary line.
    long ok = results.stream().filter(r -> r.state() == ActionState.SUCCEEDED).count();
    long failed = results.stream().filter(r -> r.state() == ActionState.FAILED).count();
    long skipped = results.stream().filter(r -> r.state() == ActionState.SKIPPED).count();

    System.out.println();
    System.out.println("Action: " + req.action());
    System.out.println("Targets: " + req.targets().size());
    System.out.println("Dry-run: " + req.options().dryRun());
    System.out.println("Succeeded: " + ok + "  Failed: " + failed + "  Skipped: " + skipped);
    System.out.println();

    for (ActionResult r : results) {
      // One row per device to make troubleshooting failed/skipped targets easy.
      System.out.printf(
          "%s\t%s\t%s\t%s%n",
          r.state(), r.device().displayNameOrId(), r.device().managedDeviceId(), r.errorMessageOrDash());
    }
  }

  private BulkOutput() {}
}

