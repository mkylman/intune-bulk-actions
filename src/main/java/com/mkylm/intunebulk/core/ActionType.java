package com.mkylm.intunebulk.core;

/** Supported remote actions this tool can request through Intune/Graph. */
public enum ActionType {
  SYNC,
  REBOOT,
  REMOVE_PRIMARY_USER,
  WIPE,
  AUTOPILOT_RESET
}

