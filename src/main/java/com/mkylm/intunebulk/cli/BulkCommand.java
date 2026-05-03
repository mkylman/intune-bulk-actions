package com.mkylm.intunebulk.cli;

import picocli.CommandLine.Command;

/** Namespace command for one-shot bulk actions (non-REPL mode). */
@Command(
    name = "bulk",
    description = "Perform bulk actions on devices resolved from a target set.",
    subcommands = {
      BulkSyncCommand.class,
      BulkRebootCommand.class,
      BulkWipeCommand.class,
      BulkAutopilotResetCommand.class
    })
public final class BulkCommand implements Runnable {
  @Override
  public void run() {
    // Intentionally empty; Picocli will show help when invoked with -h/--help.
  }
}

