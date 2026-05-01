package com.mkylm.intunebulk.cli;

import com.mkylm.intunebulk.core.ActionType;
import picocli.CommandLine.Command;

@Command(name = "reboot", description = "Bulk reboot Intune managed devices.")
public final class BulkRebootCommand extends BaseBulkActionCommand {
  @Override
  protected ActionType actionType() {
    return ActionType.REBOOT;
  }
}

