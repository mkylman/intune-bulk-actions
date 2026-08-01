package com.mkylm.intunebulk.graph;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Small helper for strongly-typed environment variable parsing. */
final class Env {
  private static final String CFG_TEMPLATE =
      String.join(
              System.lineSeparator(),
              "# intune-bulk-tools config template",
              "# Environment variables override values from ibt.cfg.",
              "",
              "INTUNE_AUTH_MODE=interactive",
              "INTUNE_TENANT_ID=",
              "INTUNE_CLIENT_ID=",
              "INTUNE_REDIRECT_URI=http://localhost")
          + System.lineSeparator();
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
    Path created = maybeCreateDefaultConfig(candidates);
    if (created != null && Files.isRegularFile(created)) {
      Map<String, String> createdValues = parseConfigFile(created);
      return ensurePromptedConfigValues(created, createdValues);
    }

    for (Path cfgPath : candidates) {
      if (cfgPath == null || !Files.isRegularFile(cfgPath)) {
        continue;
      }
      Map<String, String> values = parseConfigFile(cfgPath);
      values = ensurePromptedConfigValues(cfgPath, values);
      if (!values.isEmpty()) {
        return values;
      }
    }
    return Collections.emptyMap();
  }

  private static Path maybeCreateDefaultConfig(List<Path> candidates) {
    for (Path cfgPath : candidates) {
      if (cfgPath == null || Files.exists(cfgPath)) {
        continue;
      }
      Path parent = cfgPath.toAbsolutePath().normalize().getParent();
      try {
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Files.writeString(cfgPath, CFG_TEMPLATE);
        return cfgPath;
      } catch (IOException ignored) {
        // Try the next candidate path.
      }
    }
    return null;
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

  private static Map<String, String> ensurePromptedConfigValues(
      Path cfgPath, Map<String, String> parsedValues) {
    if (hasValue(parsedValues, "INTUNE_TENANT_ID") && hasValue(parsedValues, "INTUNE_CLIENT_ID")) {
      return parsedValues;
    }
    if (Boolean.parseBoolean(System.getProperty("intune.gui.suppressConfigPrompt", "false"))) {
      return parsedValues;
    }

    ConfigPair valuesToSave =
        promptForTenantAndClient(
            parsedValues.getOrDefault("INTUNE_TENANT_ID", ""),
            parsedValues.getOrDefault("INTUNE_CLIENT_ID", ""));
    if (valuesToSave == null
        || valuesToSave.tenantId() == null
        || valuesToSave.clientId() == null
        || valuesToSave.tenantId().isBlank()
        || valuesToSave.clientId().isBlank()) {
      return parsedValues;
    }

    try {
      writeConfigValues(cfgPath, valuesToSave.tenantId().trim(), valuesToSave.clientId().trim());
      return parseConfigFile(cfgPath);
    } catch (IOException e) {
      throw new IllegalStateException("Failed writing config file: " + cfgPath, e);
    }
  }

  private static boolean hasValue(Map<String, String> values, String key) {
    String v = values.get(key);
    return v != null && !v.isBlank();
  }

  private static ConfigPair promptForTenantAndClient(String tenantIdDefault, String clientIdDefault) {
    if (!GraphicsEnvironment.isHeadless()) {
      return promptForTenantAndClientDialog(tenantIdDefault, clientIdDefault);
    }
    java.io.Console console = System.console();
    if (console != null) {
      String tenantId = console.readLine("%s ", "Enter value for INTUNE_TENANT_ID:");
      String clientId = console.readLine("%s ", "Enter value for INTUNE_CLIENT_ID:");
      return new ConfigPair(tenantId, clientId);
    }
    throw new IllegalStateException(
        "Missing values for INTUNE_TENANT_ID/INTUNE_CLIENT_ID in ibt.cfg and no interactive prompt is available.");
  }

  private static ConfigPair promptForTenantAndClientDialog(
      String tenantIdDefault, String clientIdDefault) {
    AtomicReference<ConfigPair> resultRef = new AtomicReference<>();
    Runnable dialogWork =
        () -> {
          JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 0, 6));
          panel.add(new JLabel("INTUNE_TENANT_ID"));
          JTextField tenantField = new JTextField(tenantIdDefault == null ? "" : tenantIdDefault, 42);
          panel.add(tenantField);
          panel.add(new JLabel("INTUNE_CLIENT_ID"));
          JTextField clientField = new JTextField(clientIdDefault == null ? "" : clientIdDefault, 42);
          panel.add(clientField);

          JOptionPane optionPane =
              new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
          JDialog dialog = optionPane.createDialog(null, "ibt.cfg Setup");
          dialog.setAlwaysOnTop(true);
          dialog.toFront();
          dialog.requestFocus();
          dialog.setVisible(true);

          Object selected = optionPane.getValue();
          if (selected instanceof Integer choice && choice == JOptionPane.OK_OPTION) {
            resultRef.set(new ConfigPair(tenantField.getText(), clientField.getText()));
          } else {
            resultRef.set(null);
          }
          dialog.dispose();
        };

    try {
      if (SwingUtilities.isEventDispatchThread()) {
        dialogWork.run();
      } else {
        SwingUtilities.invokeAndWait(dialogWork);
      }
      return resultRef.get();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to show ibt.cfg setup prompt.", e);
    }
  }

  private static void writeConfigValues(Path cfgPath, String tenantId, String clientId) throws IOException {
    List<String> lines = Files.readAllLines(cfgPath);
    boolean tenantWritten = false;
    boolean clientWritten = false;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line == null) {
        continue;
      }
      int eq = line.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      String key = line.substring(0, eq).trim();
      if ("INTUNE_TENANT_ID".equals(key)) {
        lines.set(i, "INTUNE_TENANT_ID=" + tenantId);
        tenantWritten = true;
      } else if ("INTUNE_CLIENT_ID".equals(key)) {
        lines.set(i, "INTUNE_CLIENT_ID=" + clientId);
        clientWritten = true;
      }
    }
    if (!tenantWritten) {
      lines.add("INTUNE_TENANT_ID=" + tenantId);
    }
    if (!clientWritten) {
      lines.add("INTUNE_CLIENT_ID=" + clientId);
    }
    Files.write(cfgPath, lines);
  }

  private static List<Path> resolveConfigPaths() {
    List<Path> candidates = new ArrayList<>();

    String explicitProperty = System.getProperty("intune.config.file");
    if (explicitProperty != null && !explicitProperty.isBlank()) {
      candidates.add(Path.of(explicitProperty.trim()));
      return candidates;
    }

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

  private record ConfigPair(String tenantId, String clientId) {}

  private Env() {}
}

