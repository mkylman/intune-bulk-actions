package com.mkylm.intunebulk.cli;

import com.mkylm.intunebulk.gui.IntuneBulkGuiApp;
import picocli.CommandLine.Command;

/** Launches the desktop GUI shell while preserving existing CLI behavior. */
@Command(name = "gui", description = "Open the desktop GUI shell.")
public final class GuiCommand implements Runnable {
  @Override
  public void run() {
    IntuneBulkGuiApp.launch();
  }
}
