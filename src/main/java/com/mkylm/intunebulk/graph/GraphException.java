package com.mkylm.intunebulk.graph;

import java.time.Duration;
import java.util.Optional;

/**
 * Graph transport exception enriched with HTTP metadata and retry semantics.
 */
public final class GraphException extends RuntimeException {
  private final int httpStatus;
  private final String requestId;
  private final String code;
  private final boolean permanent;
  private final Duration retryAfter;

  private GraphException(
      int httpStatus,
      String requestId,
      String code,
      String message,
      boolean permanent,
      Duration retryAfter) {
    super(message);
    this.httpStatus = httpStatus;
    this.requestId = requestId;
    this.code = code;
    this.permanent = permanent;
    this.retryAfter = retryAfter;
  }

  public static GraphException transientFailure(int httpStatus, String requestId, String code, String message) {
    // Transient failures are eligible for RetryPolicy backoff/retry.
    return new GraphException(httpStatus, requestId, code, message, false, null);
  }

  public static GraphException permanentFailure(int httpStatus, String requestId, String code, String message) {
    // Permanent failures should fail fast without retry.
    return new GraphException(httpStatus, requestId, code, message, true, null);
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String requestId() {
    return requestId;
  }

  public String code() {
    return code;
  }

  public boolean isPermanent() {
    return permanent;
  }

  public Optional<Duration> retryAfter() {
    return Optional.ofNullable(retryAfter);
  }
}

