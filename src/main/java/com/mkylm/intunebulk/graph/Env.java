package com.mkylm.intunebulk.graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/** Small helper for strongly-typed environment variable parsing. */
final class Env {
  private static final Map<String, String> CONFIG_VALUES = loadConfigValues();

  static String required(String name) {
    String v = value(name);
    if (v == null || v.isBlank()) {
      throw new IllegalStateException(
          "Missing required setting: " + name + " (env var or ibt.cfg key)");
    }
    return v.trim();
  }

  static String optional(String name, String defaultValue) {
    String v = value(name);
    return (v == null || v.isBlank()) ? defaultValue : v.trim();
  }

  static List<String> csvList(String name, List<String> defaultValue) {
    // Parses comma-separated env var values into trimmed string list.
    String v = value(name);
    if (v == null || v.isBlank()) return defaultValue;
    String[] parts = v.split(",");
    List<String> out = new ArrayList<>();
    for (String p : parts) {
      String s = p.trim();
      if (!s.isEmpty()) out.add(s);
    }
    return out.isEmpty() ? defaultValue : out;
  }

  private static String value(String name) {
    String env = System.getenv(name);
    if (env != null && !env.isBlank()) {
      return env.trim();
    }
    String fromCfg = CONFIG_VALUES.get(name);
    if (fromCfg != null && !fromCfg.isBlank()) {
      return fromCfg.trim();
    }
    return null;
  }

  private static Map<String, String> loadConfigValues() {
    List<Path> candidates = resolveConfigPaths();
    for (Path cfgPath : candidates) {
      if (cfgPath == null || !Files.isRegularFile(cfgPath)) {
        continue;
      }
      Map<String, String> values = parseConfigFile(cfgPath);
      if (!values.isEmpty()) {
        return values;
      }
    }
    return Collections.emptyMap();
  }

  private static Map<String, String> parseConfigFile(Path cfgPath) {
    Map<String, String> values = new HashMap<>();
    try {
      List<String> lines = Files.readAllLines(cfgPath);
      for (String rawLine : lines) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
          continue;
        }
        int eq = line.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        String key = line.substring(0, eq).trim();
        String val = line.substring(eq + 1).trim();
        if (key.isEmpty()) {
          continue;
        }
        values.put(key, val);
      }
      return values;
    } catch (IOException e) {
      throw new IllegalStateException("Failed reading config file: " + cfgPath, e);
    }
  }

  private static List<Path> resolveConfigPaths() {
    List<Path> candidates = new ArrayList<>();

    String explicit = System.getenv("INTUNE_CONFIG_FILE");
    if (explicit != null && !explicit.isBlank()) {
      candidates.add(Path.of(explicit.trim()));
      return candidates;
    }

    // 1) Current working directory.
    candidates.add(Path.of("ibt.cfg"));

    // 2) Alongside the first classpath entry (jar folder), if available.
    Path classpathDir = firstClasspathEntryDirectory();
    if (classpathDir != null) {
      candidates.add(classpathDir.resolve("ibt.cfg"));
      // jpackage app-image layout keeps jars in "app"; allow config one level up.
      Path parent = classpathDir.getParent();
      if (parent != null) {
        candidates.add(parent.resolve("ibt.cfg"));
      }
    }

    return candidates;
  }

  private static Path firstClasspathEntryDirectory() {
    String classPath = System.getProperty("java.class.path");
    if (classPath == null || classPath.isBlank()) {
      return null;
    }

    StringTokenizer tok = new StringTokenizer(classPath, System.getProperty("path.separator", ";"));
    while (tok.hasMoreTokens()) {
      String entry = tok.nextToken();
      if (entry == null || entry.isBlank()) {
        continue;
      }
      Path p = Path.of(entry);
      if (Files.isDirectory(p)) {
        return p.toAbsolutePath().normalize();
      }
      Path parent = p.toAbsolutePath().normalize().getParent();
      if (parent != null) {
        return parent;
      }
    }
    return null;
  }

  private Env() {}
}

