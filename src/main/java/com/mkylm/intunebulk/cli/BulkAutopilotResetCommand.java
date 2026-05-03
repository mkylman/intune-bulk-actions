package com.mkylm.intunebulk.cli;

import com.mkylm.intunebulk.core.ActionType;
import picocli.CommandLine.Command;

@Command(name = "autopilot-reset", description = "Bulk Autopilot Reset Intune managed devices.")
public final class BulkAutopilotResetCommand extends BaseBulkActionCommand {
  @Override
  protected ActionType actionType() {
    return ActionType.AUTOPILOT_RESET;
  }
}

