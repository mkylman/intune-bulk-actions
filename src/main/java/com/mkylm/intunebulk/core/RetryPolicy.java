package com.mkylm.intunebulk.core;

import com.mkylm.intunebulk.graph.GraphException;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Callable;

/**
 * Retry helper for Graph operations.
 *
 * <p>Retries transient GraphException failures with exponential backoff and jitter.
 */
final class RetryPolicy {
  private final int maxRetries;
  private final Duration baseBackoff;
  private final Random jitter = new Random();

  RetryPolicy(int maxRetries, Duration baseBackoff) {
    this.maxRetries = Math.max(0, maxRetries);
    this.baseBackoff = baseBackoff == null ? Duration.ofSeconds(2) : baseBackoff;
  }

  <T> T execute(Callable<T> work) {
    int attempt = 0;
    while (true) {
      try {
        return work.call();
      } catch (GraphException ex) {
        // Retry only transient, within configured attempt budget.
        attempt++;
        if (attempt > maxRetries || ex.isPermanent()) throw ex;
        Duration wait = ex.retryAfter().orElse(backoff(attempt));
        sleep(wait);
      } catch (RuntimeException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  private Duration backoff(int attempt) {
    long baseMillis = baseBackoff.toMillis();
    long exp = baseMillis * (1L << Math.min(10, attempt)); // cap growth
    long withJitter = (long) (exp * (0.7 + (jitter.nextDouble() * 0.6)));
    return Duration.ofMillis(Math.min(withJitter, 120_000));
  }

  private void sleep(Duration d) {
    try {
      Thread.sleep(Math.max(0, d.toMillis()));
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted during backoff", ie);
    }
  }
}

