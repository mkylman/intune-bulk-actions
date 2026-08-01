package com.mkylm.intunebulk.core;

import com.mkylm.intunebulk.graph.GraphClient;
import com.mkylm.intunebulk.graph.GraphException;
import com.mkylm.intunebulk.graph.GraphResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core bulk-action engine.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>fan out actions across devices with configured concurrency
 *   <li>apply retry policy for transient Graph failures
 *   <li>normalize each outcome into ActionResult
 * </ul>
 */
public final class DeviceActionService {
  @FunctionalInterface
  public interface ProgressListener {
    void onProgress(ProgressSnapshot progress);
  }

  public record ProgressSnapshot(
      int total, int completed, int succeeded, int failed, int skipped, ActionResult latestResult) {}

  // Graph client for interacting with Intune Graph API.
  private final GraphClient graph;

  public DeviceActionService(GraphClient graph) {
    this.graph = graph;
  }

  // Executes the action request and returns the results.
  public List<ActionResult> execute(ActionRequest req) {
    return execute(req, null);
  }

  // Executes the action request and returns the results with progress updates.
  public List<ActionResult> execute(ActionRequest req, ProgressListener progressListener) {
    List<ActionResult> out = new ArrayList<>();
    ActionOptions opt = req.options();
    int total = req.targets().size();

    // Parallelize device operations to reduce total runtime for large groups.
    ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, opt.maxConcurrency()));

    try {
      CompletionService<ActionResult> completion = new ExecutorCompletionService<>(pool);
      for (DeviceRef d : req.targets()) {
        completion.submit(() -> safeRunOne(req.action(), d, opt));
      }

      int completed = 0;
      int succeeded = 0;
      int failed = 0;
      int skipped = 0;

      for (int i = 0; i < total; i++) {
        ActionResult r = completion.take().get();
        out.add(r);
        completed++;
        switch (r.state()) {
          case SUCCEEDED -> succeeded++;
          case FAILED -> failed++;
          case SKIPPED -> skipped++;
          default -> {}
        }

        if (progressListener != null) {
          progressListener.onProgress(
              new ProgressSnapshot(total, completed, succeeded, failed, skipped, r));
        }
      }
      return out;
    } catch (Exception e) {
      throw new RuntimeException("Bulk execution failed: " + e.getMessage(), e);
    } finally {
      pool.shutdownNow();
    }
  }

  private ActionResult safeRunOne(ActionType action, DeviceRef device, ActionOptions opt) {
    try {
      return runOne(action, device, opt);
    } catch (Exception e) {
      return ActionResult.failed(device, action, "execution_error", e.getMessage());
    }
  }

  private ActionResult runOne(ActionType action, DeviceRef device, ActionOptions opt) {
    // Skip unresolved targets and dry-run operations up front.
    if (device.skipped()) return ActionResult.skippedNotEnrolled(device, action);
    if (opt.dryRun()) return ActionResult.skippedDryRun(device, action);

    // Wrap each device operation in throttling/transient-error retry behavior.
    RetryPolicy retry = new RetryPolicy(opt.maxRetries(), opt.baseBackoff());

    return retry.execute(
        new Callable<>() {
          @Override
          public ActionResult call() {
            Instant start = Instant.now();
            GraphResponse resp = invokeAction(action, device, opt);
            Instant end = Instant.now();

            // Convert transport/API response into user-facing state.
            ActionState state = (resp.errorCode() == null && resp.status() < 400) ? ActionState.SUCCEEDED : ActionState.FAILED;
            return new ActionResult(
                device,
                action,
                state,
                resp.status(),
                resp.requestId(),
                resp.errorCode(),
                resp.errorMessage(),
                start,
                end);
          }
        });
  }

  private GraphResponse invokeAction(ActionType action, DeviceRef d, ActionOptions opt) {
    try {
      // Map high-level action enum to the corresponding Intune Graph action endpoint.
      return switch (action) {
        case SYNC ->
            graph.postV1("/deviceManagement/managedDevices/" + d.managedDeviceId() + "/syncDevice", "{}");
        case REBOOT ->
            graph.postV1("/deviceManagement/managedDevices/" + d.managedDeviceId() + "/rebootNow", "{}");
        case REMOVE_PRIMARY_USER ->
            graph.deleteBeta("/deviceManagement/managedDevices/" + d.managedDeviceId() + "/users/$ref");
        case WIPE ->
            graph.postV1(
                "/deviceManagement/managedDevices/" + d.managedDeviceId() + "/wipe",
                graph.json(
                    "keepEnrollmentData",
                    opt.keepEnrollmentData(),
                    "keepUserData",
                    opt.keepUserData(),
                    "useProtectedWipe",
                    opt.useProtectedWipe()));
        case AUTOPILOT_RESET -> {
          String path = "/deviceManagement/managedDevices/" + d.managedDeviceId() + "/autopilotReset";
          yield opt.useBeta() ? graph.postBeta(path, "{}") : graph.postV1(path, "{}");
        }
      };
    } catch (GraphException e) {
      return new GraphResponse(e.httpStatus(), e.requestId(), e.code(), e.getMessage());
    }
  }
}

