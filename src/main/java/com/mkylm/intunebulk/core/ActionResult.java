package com.mkylm.intunebulk.core;

import java.time.Instant;

/** Immutable per-device outcome record for a bulk action execution. */
public record ActionResult(
    DeviceRef device,
    ActionType action,
    ActionState state,
    int httpStatus,
    String requestId,
    String errorCode,
    String errorMessage,
    Instant startedAt,
    Instant endedAt) {

  public static ActionResult skippedDryRun(DeviceRef d, ActionType a) {
    // Used when user requests planning/preview only.
    return new ActionResult(
        d, a, ActionState.SKIPPED, 0, null, null, "dry-run", Instant.now(), Instant.now());
  }

  public static ActionResult skippedNotEnrolled(DeviceRef d, ActionType a) {
    // Used when a target cannot be mapped to an actionable managedDevice.
    return new ActionResult(
        d,
        a,
        ActionState.SKIPPED,
        0,
        null,
        null,
        d.skipReason() != null ? d.skipReason() : "skipped",
        Instant.now(),
        Instant.now());
  }

  public static ActionResult failed(DeviceRef d, ActionType a, String code, String message) {
    // Utility factory for non-HTTP execution failures.
    return new ActionResult(d, a, ActionState.FAILED, 0, null, code, message, Instant.now(), Instant.now());
  }

  public String errorMessageOrDash() {
    return errorMessage == null || errorMessage.isBlank() ? "-" : errorMessage;
  }
}

