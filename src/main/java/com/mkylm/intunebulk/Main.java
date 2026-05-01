package com.mkylm.intunebulk;

import com.mkylm.intunebulk.cli.RootCommand;
import picocli.CommandLine;

/** Program entry point: hands argument parsing/execution to Picocli. */
public final class Main {
  public static void main(String[] args) {
    // RootCommand owns all subcommands (bulk + interactive shell).
    // Quality-of-life default for packaged .exe: opening it directly starts the REPL shell.
    String[] effectiveArgs = (args == null || args.length == 0) ? new String[] {"shell"} : args;
    int exitCode = new CommandLine(new RootCommand()).execute(effectiveArgs);
    System.exit(exitCode);
  }
}

