package com.mkylm.intunebulk.cli;

import com.mkylm.intunebulk.core.DeviceActionService.ProgressSnapshot;

/** Simple single-line console progress indicator for bulk actions. */
final class ConsoleProgressBar {
  private static final int BAR_WIDTH = 24;

  private final String label;
  private int lastRenderWidth = 0;

  ConsoleProgressBar(String label) {
    this.label = label;
  }

  void onProgress(ProgressSnapshot p) {
    if (p.total() <= 0) {
      return;
    }

    int pct = (int) Math.round((p.completed() * 100.0) / p.total());
    int filled = (int) Math.round((p.completed() * BAR_WIDTH) / (double) p.total());
    filled = Math.max(0, Math.min(BAR_WIDTH, filled));

    String bar = "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled);
    String line =
        String.format(
            "\r%s [%s] %3d%% (%d/%d) ok:%d fail:%d skip:%d",
            label, bar, pct, p.completed(), p.total(), p.succeeded(), p.failed(), p.skipped());

    int pad = Math.max(0, lastRenderWidth - printableWidth(line));
    if (pad > 0) {
      line = line + " ".repeat(pad);
    }
    lastRenderWidth = printableWidth(line);
    System.out.print(line);
    System.out.flush();

    if (p.completed() >= p.total()) {
      System.out.println();
    }
  }

  private static int printableWidth(String s) {
    if (s == null) return 0;
    return s.startsWith("\r") ? s.length() - 1 : s.length();
  }
}

