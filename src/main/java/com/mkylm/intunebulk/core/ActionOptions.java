package com.mkylm.intunebulk.core;

import java.time.Duration;

/**
 * Execution tuning/options shared across bulk actions.
 *
 * <p>Includes throughput/retry controls plus action-specific toggles (e.g. wipe flags).
 */
public record ActionOptions(
    int maxConcurrency,
    int batchSize,
    int maxRetries,
    Duration baseBackoff,
    boolean dryRun,
    boolean continueOnError,
    boolean useBeta,
    // wipe options
    boolean keepEnrollmentData,
    boolean keepUserData,
    boolean useProtectedWipe) {

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static final class Builder {
    // Sensible defaults for interactive operations.
    private int maxConcurrency = 6;
    private int batchSize = 25;
    private int maxRetries = 6;
    private Duration baseBackoff = Duration.ofSeconds(2);
    private boolean dryRun = false;
    private boolean continueOnError = true;
    private boolean useBeta = true;
    private boolean keepEnrollmentData = false;
    private boolean keepUserData = false;
    private boolean useProtectedWipe = false;

    private Builder() {}

    private Builder(ActionOptions o) {
      this.maxConcurrency = o.maxConcurrency;
      this.batchSize = o.batchSize;
      this.maxRetries = o.maxRetries;
      this.baseBackoff = o.baseBackoff;
      this.dryRun = o.dryRun;
      this.continueOnError = o.continueOnError;
      this.useBeta = o.useBeta;
      this.keepEnrollmentData = o.keepEnrollmentData;
      this.keepUserData = o.keepUserData;
      this.useProtectedWipe = o.useProtectedWipe;
    }

    public Builder maxConcurrency(int v) {
      this.maxConcurrency = v;
      return this;
    }

    public Builder batchSize(int v) {
      this.batchSize = v;
      return this;
    }

    public Builder maxRetries(int v) {
      this.maxRetries = v;
      return this;
    }

    public Builder baseBackoff(Duration v) {
      this.baseBackoff = v;
      return this;
    }

    public Builder dryRun(boolean v) {
      this.dryRun = v;
      return this;
    }

    public Builder continueOnError(boolean v) {
      this.continueOnError = v;
      return this;
    }

    public Builder useBeta(boolean v) {
      this.useBeta = v;
      return this;
    }

    public Builder keepEnrollmentData(boolean v) {
      this.keepEnrollmentData = v;
      return this;
    }

    public Builder keepUserData(boolean v) {
      this.keepUserData = v;
      return this;
    }

    public Builder useProtectedWipe(boolean v) {
      this.useProtectedWipe = v;
      return this;
    }

    public ActionOptions build() {
      return new ActionOptions(
          maxConcurrency,
          batchSize,
          maxRetries,
          baseBackoff,
          dryRun,
          continueOnError,
          useBeta,
          keepEnrollmentData,
          keepUserData,
          useProtectedWipe);
    }
  }
}

