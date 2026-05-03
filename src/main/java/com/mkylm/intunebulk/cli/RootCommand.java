package com.mkylm.intunebulk.cli;

import picocli.CommandLine.Command;

/** Top-level command router for the entire CLI application. */
@Command(
    name = "intune-bulk",
    mixinStandardHelpOptions = true,
    description = "Bulk actions on Intune devices via Microsoft Graph.",
    subcommands = {BulkCommand.class, ShellCommand.class, GuiCommand.class})
public final class RootCommand implements Runnable {
  @Override
  public void run() {
    // Default behavior: show help via Picocli (mixinStandardHelpOptions).
  }
}

