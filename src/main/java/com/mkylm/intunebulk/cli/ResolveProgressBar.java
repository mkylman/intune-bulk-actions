package com.mkylm.intunebulk.cli;

import com.mkylm.intunebulk.core.GroupDeviceResolver.ProgressSnapshot;

/** Single-line progress indicator for group-device resolution phase. */
final class ResolveProgressBar {
  private static final int BAR_WIDTH = 24;

  private final String label;
  private int lastRenderWidth = 0;

  ResolveProgressBar(String label) {
    this.label = label;
  }

  void onProgress(ProgressSnapshot p) {
    if (p.totalMembers() <= 0) {
      String line = String.format("\r%s [------------------------]   0%% (0/0) mapped:0 skipped:0", label);
      render(line);
      System.out.println();
      return;
    }

    int pct = (int) Math.round((p.processedMembers() * 100.0) / p.totalMembers());
    int filled = (int) Math.round((p.processedMembers() * BAR_WIDTH) / (double) p.totalMembers());
    filled = Math.max(0, Math.min(BAR_WIDTH, filled));

    String bar = "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled);
    String line =
        String.format(
            "\r%s [%s] %3d%% (%d/%d) mapped:%d skipped:%d",
            label,
            bar,
            pct,
            p.processedMembers(),
            p.totalMembers(),
            p.mappedMembers(),
            p.skippedMembers());

    render(line);
    if (p.processedMembers() >= p.totalMembers()) {
      System.out.println();
    }
  }

  private void render(String line) {
    int pad = Math.max(0, lastRenderWidth - printableWidth(line));
    String out = line + (pad > 0 ? " ".repeat(pad) : "");
    lastRenderWidth = printableWidth(out);
    System.out.print(out);
    System.out.flush();
  }

  private static int printableWidth(String s) {
    if (s == null) return 0;
    return s.startsWith("\r") ? s.length() - 1 : s.length();
  }
}

