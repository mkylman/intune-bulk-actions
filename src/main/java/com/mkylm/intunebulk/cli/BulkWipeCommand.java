package com.mkylm.intunebulk.cli;

import com.mkylm.intunebulk.core.ActionOptions;
import com.mkylm.intunebulk.core.ActionType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "wipe", description = "Bulk wipe (factory reset) Intune managed devices.")
public final class BulkWipeCommand extends BaseBulkActionCommand {
  @Option(names = {"--keepEnrollmentData"}, defaultValue = "false")
  boolean keepEnrollmentData;

  @Option(names = {"--keepUserData"}, defaultValue = "false")
  boolean keepUserData;

  @Option(names = {"--useProtectedWipe"}, defaultValue = "false")
  boolean useProtectedWipe;

  @Override
  protected ActionType actionType() {
    return ActionType.WIPE;
  }

  @Override
  protected ActionOptions buildOptions() {
    return super.buildOptions().toBuilder()
        .keepEnrollmentData(keepEnrollmentData)
        .keepUserData(keepUserData)
        .useProtectedWipe(useProtectedWipe)
        .build();
  }
}

