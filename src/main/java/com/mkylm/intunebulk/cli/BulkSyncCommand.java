package com.mkylm.intunebulk.cli;

import com.mkylm.intunebulk.core.ActionType;
import picocli.CommandLine.Command;

@Command(name = "sync", description = "Bulk sync Intune managed devices.")
public final class BulkSyncCommand extends BaseBulkActionCommand {
  @Override
  protected ActionType actionType() {
    return ActionType.SYNC;
  }
}

