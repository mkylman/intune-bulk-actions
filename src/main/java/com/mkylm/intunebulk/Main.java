package com.mkylm.intunebulk;

import com.mkylm.intunebulk.cli.RootCommand;
import picocli.CommandLine;

/** Program entry point: hands argument parsing/execution to Picocli. */
public final class Main {
  public static void main(String[] args) {
    // RootCommand owns all subcommands (bulk + interactive shell).
    // Quality-of-life default for packaged .exe: opening it directly starts the GUI.
    String[] effectiveArgs;
    if (args == null || args.length == 0) {
      effectiveArgs = new String[] {"gui"};
    } else if (args.length == 1 && "--gui".equalsIgnoreCase(args[0])) {
      effectiveArgs = new String[] {"gui"};
    } else {
      effectiveArgs = args;
    }
    int exitCode = new CommandLine(new RootCommand()).execute(effectiveArgs);
    if (!isGuiMode(effectiveArgs)) {
      System.exit(exitCode);
    }
  }

  private static boolean isGuiMode(String[] effectiveArgs) {
    return effectiveArgs != null
        && effectiveArgs.length > 0
        && ("gui".equalsIgnoreCase(effectiveArgs[0]) || "--gui".equalsIgnoreCase(effectiveArgs[0]));
  }
}

