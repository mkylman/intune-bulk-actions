package com.mkylm.intunebulk.core;

/** Normalized execution state shown to operators in command output. */
public enum ActionState {
  PENDING,
  RUNNING,
  SUCCEEDED,
  FAILED,
  SKIPPED
}

