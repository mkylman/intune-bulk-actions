package com.mkylm.intunebulk.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.mkylm.intunebulk.core.ActionOptions;
import com.mkylm.intunebulk.core.ActionRequest;
import com.mkylm.intunebulk.core.ActionResult;
import com.mkylm.intunebulk.core.ActionState;
import com.mkylm.intunebulk.core.ActionType;
import com.mkylm.intunebulk.core.DeviceRef;
import com.mkylm.intunebulk.core.DeviceActionService;
import com.mkylm.intunebulk.core.GroupDeviceResolver;
import com.mkylm.intunebulk.graph.GraphClient;
import com.mkylm.intunebulk.graph.TokenProvider;
import com.mkylm.intunebulk.graph.TokenProviderFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import picocli.CommandLine.Command;

@Command(
    name = "shell",
    description = "Interactive terminal shell (browse groups/users/devices, resolve group members).")
public final class ShellCommand implements Runnable {
  @Override
  public void run() {
    // Core runtime services shared by all shell commands.
    TokenProvider tokenProvider = TokenProviderFactory.fromEnvironment();
    GraphClient graph = GraphClient.createDefault(tokenProvider);
    GroupDeviceResolver resolver = new GroupDeviceResolver(graph);
    DeviceActionService actionService = new DeviceActionService(graph);

    System.out.println("intune-bulk shell");
    System.out.println("Type 'help' for commands, 'exit' to quit.");
    System.out.println();
    printHelp();

    try (BufferedReader r =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      while (true) {
        System.out.print("> ");
        System.out.flush();
        String line = r.readLine();
        if (line == null) return;
        line = line.trim();
        if (line.isEmpty()) continue;

        List<String> args = splitArgs(line);
        String cmd = args.get(0).toLowerCase(Locale.ROOT);

        try {
          // Command dispatcher for the interactive shell command set.
          switch (cmd) {
            case "exit", "quit" -> {
              return;
            }
            case "help" -> printHelp();
            case "login" -> handleLogin(tokenProvider);
            case "groups" -> handleGroups(graph, args);
            case "users" -> handleUsers(graph, args);
            case "devices" -> handleDevices(graph, args);
            case "group-devices" -> handleGroupDevices(graph, resolver, args);
            case "sync-group" -> handleSyncGroup(graph, resolver, actionService, args);
            case "reboot-group" -> handleRebootGroup(graph, resolver, actionService, args);
            case "remove-primary-user-group" -> handleRemovePrimaryUserGroup(graph, resolver, actionService, args);
            default -> {
              System.out.println("Unknown command: " + args.get(0));
              System.out.println("Type 'help' for a list of commands.");
            }
          }
        } catch (Exception e) {
          System.out.println("ERROR: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Shell failed: " + e.getMessage(), e);
    }
  }

  private static void printHelp() {
    System.out.println("Commands");
    List<String[]> commandRows = new ArrayList<>();
    commandRows.add(new String[] {"help", "Show help output"});
    commandRows.add(new String[] {"login", "Open browser/device login now (forces token acquisition)"});
    commandRows.add(new String[] {"exit", "Exit the interactive shell"});
    commandRows.add(new String[] {"groups [--top N] [--prefix TEXT]", "List Azure AD groups"});
    commandRows.add(new String[] {"users [--top N] [--prefix TEXT]", "List users"});
    commandRows.add(new String[] {"devices [--top N] [--prefix TEXT]", "List Intune managed devices"});
    commandRows.add(
        new String[] {
          "group-devices <groupId|groupName>", "Resolve AAD group -> Intune managedDevice ids"
        });
    commandRows.add(
        new String[] {
          "sync-group <groupId|groupName> [--dryRun]", "Run sync on all resolvable devices in group"
        });
    commandRows.add(
        new String[] {
          "reboot-group <groupId|groupName> [--dryRun]",
          "Reboot all resolvable devices in group"
        });
    commandRows.add(
        new String[] {
          "remove-primary-user-group <groupId|groupName> [--dryRun]",
          "Remove primary user from all resolvable devices in group"
        });
    printTable(new String[] {"Command", "Description"}, commandRows);
    System.out.println();

    System.out.println("Auth");
    List<String[]> authRows = new ArrayList<>();
    authRows.add(
        new String[] {
          "Default",
          "INTUNE_AUTH_MODE=interactive (opens browser sign-in when token is needed)"
        });
    authRows.add(
        new String[] {
          "Optional",
          "Set INTUNE_TENANT_ID / INTUNE_CLIENT_ID if needed"
        });
    authRows.add(
        new String[] {
          "Interactive",
          "INTUNE_AUTH_MODE=interactive + INTUNE_CLIENT_ID (+ INTUNE_REDIRECT_URI if needed)"
        });
    printTable(new String[] {"Setting", "Value"}, authRows);
    System.out.println();
  }

  private static void handleLogin(TokenProvider tokenProvider) {
    // Forces auth (interactive mode opens browser). We don't print the token.
    tokenProvider.getAccessToken();
    System.out.println("Login OK.");
  }

  private static void handleGroups(GraphClient graph, List<String> args) {
    int top = intFlag(args, "--top", -1);
    String prefix = stringFlag(args, "--prefix", null);

    String path =
        "/groups?$select="
            + urlEncode("id,displayName")
            + (top > 0 ? "&$top=" + top : "")
            + (prefix == null
                ? ""
                : "&$filter=" + urlEncode("startswith(displayName,'" + escapeOData(prefix) + "')"));

    List<JsonNode> rows = graph.getV1PagedValues(path, top);
    List<String[]> tableRows = new ArrayList<>();
    for (JsonNode g : rows) {
      tableRows.add(new String[] {text(g, "displayName"), text(g, "id")});
    }
    sortRowsByColumn(tableRows, 0);
    printTable(new String[] {"Display Name", "Group ID"}, tableRows);
    System.out.println("(" + rows.size() + " groups)");
  }

  private static void handleUsers(GraphClient graph, List<String> args) {
    int top = intFlag(args, "--top", -1);
    String prefix = stringFlag(args, "--prefix", null);

    String path =
        "/users?$select="
            + urlEncode("id,displayName,userPrincipalName")
            + (top > 0 ? "&$top=" + top : "")
            + (prefix == null
                ? ""
                : "&$filter=" + urlEncode("startswith(displayName,'" + escapeOData(prefix) + "')"));

    List<JsonNode> rows = graph.getV1PagedValues(path, top);
    List<String[]> tableRows = new ArrayList<>();
    for (JsonNode u : rows) {
      tableRows.add(new String[] {text(u, "displayName"), text(u, "userPrincipalName"), text(u, "id")});
    }
    sortRowsByColumn(tableRows, 0);
    printTable(new String[] {"Display Name", "UPN", "User ID"}, tableRows);
    System.out.println("(" + rows.size() + " users)");
  }

  private static void handleDevices(GraphClient graph, List<String> args) {
    int top = intFlag(args, "--top", -1);
    String prefix = stringFlag(args, "--prefix", null);

    String path =
        "/deviceManagement/managedDevices?$select="
            + urlEncode("id,deviceName,serialNumber,azureADDeviceId")
            + (top > 0 ? "&$top=" + top : "")
            + (prefix == null
                ? ""
                : "&$filter=" + urlEncode("startswith(deviceName,'" + escapeOData(prefix) + "')"));

    List<JsonNode> rows = graph.getV1PagedValues(path, top);
    List<String[]> tableRows = new ArrayList<>();
    for (JsonNode d : rows) {
      tableRows.add(new String[] {text(d, "deviceName"), text(d, "serialNumber"), text(d, "id")});
    }
    sortRowsByColumn(tableRows, 0);
    printTable(new String[] {"Device Name", "Serial", "Managed Device ID"}, tableRows);
    System.out.println("(" + rows.size() + " devices)");
  }

  private static void handleGroupDevices(GraphClient graph, GroupDeviceResolver resolver, List<String> args) {
    if (args.size() < 2) {
      System.out.println("Usage: group-devices <groupId|groupName>");
      return;
    }
    // Resolve provided identifier/name first, then materialize devices for that group.
    String groupId = resolveGroupIdOrName(graph, args.get(1));
    List<DeviceRef> devices = resolveGroupDevicesWithProgress(resolver, groupId);
    List<String[]> rows = new ArrayList<>();
    for (DeviceRef d : devices) {
      if (d.skipped()) {
        rows.add(new String[] {d.displayNameOrId(), "-", "-"});
      } else {
        rows.add(new String[] {d.displayNameOrId(), d.serialNumber(), d.primaryUser()});
      }
    }
    sortRowsByColumn(rows, 0);
    printTable(new String[] {"Device Name", "Serial Number", "Device Primary User"}, rows);
    System.out.println("(" + devices.size() + " resolved)");
  }

  private static void handleSyncGroup(
      GraphClient graph, GroupDeviceResolver resolver, DeviceActionService actionService, List<String> args) {
    if (args.size() < 2) {
      System.out.println("Usage: sync-group <groupId|groupName> [--dryRun]");
      return;
    }

    // Translate user input into a concrete group ID and execute SYNC across members.
    String groupId = resolveGroupIdOrName(graph, args.get(1));
    boolean dryRun = hasFlag(args, "--dryrun");
    int maxConcurrency = intFlag(args, "--maxConcurrency", 6);
    int maxRetries = intFlag(args, "--maxRetries", 6);
    int backoffSeconds = intFlag(args, "--baseBackoffSeconds", 2);

    List<DeviceRef> targets = resolveGroupDevicesWithProgress(resolver, groupId);
    ActionOptions options =
        ActionOptions.builder()
            .dryRun(dryRun)
            .maxConcurrency(maxConcurrency)
            .maxRetries(maxRetries)
            .baseBackoff(Duration.ofSeconds(Math.max(1, backoffSeconds)))
            .continueOnError(true)
            .build();

    ActionRequest req = new ActionRequest(ActionType.SYNC, targets, options);
    List<ActionResult> results = executeWithProgress(actionService, req, "SYNC");

    printActionSummary("SYNC", groupId, req.options().dryRun(), results);
  }

  private static void handleRebootGroup(
      GraphClient graph, GroupDeviceResolver resolver, DeviceActionService actionService, List<String> args) {
    if (args.size() < 2) {
      System.out.println("Usage: reboot-group <groupId|groupName> [--dryRun]");
      return;
    }

    // Same execution pipeline as sync-group, but targeting REBOOT action.
    String groupId = resolveGroupIdOrName(graph, args.get(1));
    boolean dryRun = hasFlag(args, "--dryrun");
    int maxConcurrency = intFlag(args, "--maxConcurrency", 6);
    int maxRetries = intFlag(args, "--maxRetries", 6);
    int backoffSeconds = intFlag(args, "--baseBackoffSeconds", 2);

    List<DeviceRef> targets = resolveGroupDevicesWithProgress(resolver, groupId);
    ActionOptions options =
        ActionOptions.builder()
            .dryRun(dryRun)
            .maxConcurrency(maxConcurrency)
            .maxRetries(maxRetries)
            .baseBackoff(Duration.ofSeconds(Math.max(1, backoffSeconds)))
            .continueOnError(true)
            .build();

    ActionRequest req = new ActionRequest(ActionType.REBOOT, targets, options);
    List<ActionResult> results = executeWithProgress(actionService, req, "REBOOT");

    printActionSummary("REBOOT", groupId, req.options().dryRun(), results);
  }

  private static void handleRemovePrimaryUserGroup(
      GraphClient graph, GroupDeviceResolver resolver, DeviceActionService actionService, List<String> args) {
    if (args.size() < 2) {
      System.out.println("Usage: remove-primary-user-group <groupId|groupName> [--dryRun]");
      return;
    }

    String groupId = resolveGroupIdOrName(graph, args.get(1));
    boolean dryRun = hasFlag(args, "--dryrun");
    int maxConcurrency = intFlag(args, "--maxConcurrency", 6);
    int maxRetries = intFlag(args, "--maxRetries", 6);
    int backoffSeconds = intFlag(args, "--baseBackoffSeconds", 2);

    List<DeviceRef> targets = resolveGroupDevicesWithProgress(resolver, groupId);
    ActionOptions options =
        ActionOptions.builder()
            .dryRun(dryRun)
            .maxConcurrency(maxConcurrency)
            .maxRetries(maxRetries)
            .baseBackoff(Duration.ofSeconds(Math.max(1, backoffSeconds)))
            .continueOnError(true)
            .build();

    ActionRequest req = new ActionRequest(ActionType.REMOVE_PRIMARY_USER, targets, options);
    List<ActionResult> results = executeWithProgress(actionService, req, "REMOVE_PRIMARY_USER");

    printActionSummary("REMOVE_PRIMARY_USER", groupId, req.options().dryRun(), results);
  }

  private static List<ActionResult> executeWithProgress(
      DeviceActionService actionService, ActionRequest req, String label) {
    ConsoleProgressBar progressBar = new ConsoleProgressBar(label);
    return actionService.execute(req, progressBar::onProgress);
  }

  private static List<DeviceRef> resolveGroupDevicesWithProgress(
      GroupDeviceResolver resolver, String groupId) {
    ResolveProgressBar progressBar = new ResolveProgressBar("RESOLVE");
    return resolver.resolveFromAadGroup(groupId, progressBar::onProgress);
  }

  private static String resolveGroupIdOrName(GraphClient graph, String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Group identifier is required");
    }
    // Accept direct GUIDs; otherwise resolve by exact displayName for operator convenience.
    if (looksLikeGuid(value)) {
      return value;
    }

    String path =
        "/groups?$select="
            + urlEncode("id,displayName")
            + "&$filter="
            + urlEncode("displayName eq '" + escapeOData(value) + "'");
    List<JsonNode> exact = graph.getV1PagedValues(path, 5);

    if (exact.isEmpty()) {
      String prefixPath =
          "/groups?$select="
              + urlEncode("id,displayName")
              + "&$filter="
              + urlEncode("startswith(displayName,'" + escapeOData(value) + "')")
              + "&$top=5";
      List<JsonNode> candidates = graph.getV1PagedValues(prefixPath, 5);
      if (candidates.isEmpty()) {
        throw new IllegalArgumentException(
            "No group found with name '" + value + "'. Use groups --prefix to discover names.");
      }
      StringBuilder msg =
          new StringBuilder("No exact group name match for '")
              .append(value)
              .append("'. Possible matches:\n");
      for (JsonNode g : candidates) {
        msg.append("  - ").append(text(g, "displayName")).append("  (").append(text(g, "id")).append(")\n");
      }
      msg.append("Use the exact name in quotes or pass the groupId.");
      throw new IllegalArgumentException(msg.toString());
    }

    if (exact.size() > 1) {
      StringBuilder msg = new StringBuilder("Multiple groups found named '").append(value).append("':\n");
      for (JsonNode g : exact) {
        msg.append("  - ").append(text(g, "displayName")).append("  (").append(text(g, "id")).append(")\n");
      }
      msg.append("Please pass the groupId.");
      throw new IllegalArgumentException(msg.toString());
    }

    JsonNode group = exact.get(0);
    String groupId = text(group, "id");
    String displayName = text(group, "displayName");
    System.out.println("Resolved group '" + displayName + "' -> " + groupId);
    return groupId;
  }

  private static boolean looksLikeGuid(String value) {
    return value.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
  }

  private static int intFlag(List<String> args, String name, int defaultValue) {
    for (int i = 0; i < args.size() - 1; i++) {
      if (args.get(i).equalsIgnoreCase(name)) {
        try {
          return Integer.parseInt(args.get(i + 1));
        } catch (Exception e) {
          throw new IllegalArgumentException("Invalid " + name + " value: " + args.get(i + 1));
        }
      }
    }
    return defaultValue;
  }

  private static String stringFlag(List<String> args, String name, String defaultValue) {
    for (int i = 0; i < args.size() - 1; i++) {
      if (args.get(i).equalsIgnoreCase(name)) {
        return args.get(i + 1);
      }
    }
    return defaultValue;
  }

  private static boolean hasFlag(List<String> args, String flag) {
    for (String arg : args) {
      if (arg.equalsIgnoreCase(flag)) return true;
    }
    return false;
  }

  private static List<String> splitArgs(String line) {
    // Minimal splitter: supports double-quoted segments.
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes;
        continue;
      }
      if (!inQuotes && Character.isWhitespace(c)) {
        if (!cur.isEmpty()) {
          out.add(cur.toString());
          cur.setLength(0);
        }
        continue;
      }
      cur.append(c);
    }
    if (!cur.isEmpty()) out.add(cur.toString());
    return out;
  }

  private static String text(JsonNode node, String field) {
    if (node == null) return "";
    JsonNode v = node.get(field);
    return (v != null && v.isTextual()) ? v.asText() : "";
  }

  private static String escapeOData(String s) {
    // OData strings escape single quotes by doubling them.
    return Objects.requireNonNull(s).replace("'", "''");
  }

  private static String urlEncode(String s) {
    try {
      return java.net.URLEncoder.encode(Objects.requireNonNull(s), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to URL-encode value", e);
    }
  }

  private static void printTable(String[] headers, List<String[]> rows) {
    if (headers == null || headers.length == 0) {
      return;
    }

    int cols = headers.length;
    int[] widths = new int[cols];
    for (int i = 0; i < cols; i++) {
      widths[i] = headers[i].length();
    }

    for (String[] row : rows) {
      for (int i = 0; i < cols; i++) {
        String cell = safeCell(row, i);
        widths[i] = Math.max(widths[i], cell.length());
      }
    }

    System.out.println(formatRow(headers, widths));
    System.out.println(formatSeparator(widths));
    for (String[] row : rows) {
      String[] rendered = new String[cols];
      for (int i = 0; i < cols; i++) {
        rendered[i] = safeCell(row, i);
      }
      System.out.println(formatRow(rendered, widths));
    }
  }

  private static String safeCell(String[] row, int idx) {
    if (row == null || idx >= row.length || row[idx] == null || row[idx].isBlank()) {
      return "-";
    }
    return row[idx];
  }

  private static String formatRow(String[] values, int[] widths) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < widths.length; i++) {
      if (i > 0) b.append("  ");
      b.append(padRight(values[i], widths[i]));
    }
    return b.toString();
  }

  private static String formatSeparator(int[] widths) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < widths.length; i++) {
      if (i > 0) b.append("  ");
      b.append("-".repeat(Math.max(3, widths[i])));
    }
    return b.toString();
  }

  private static String padRight(String value, int width) {
    if (value.length() >= width) return value;
    return value + " ".repeat(width - value.length());
  }

  private static void sortRowsByColumn(List<String[]> rows, int columnIndex) {
    rows.sort(
        Comparator.comparing(
            row -> {
              if (row == null || columnIndex >= row.length || row[columnIndex] == null) {
                return "";
              }
              return row[columnIndex];
            },
            String.CASE_INSENSITIVE_ORDER));
  }

  private static void printActionSummary(
      String actionName, String groupId, boolean dryRun, List<ActionResult> results) {
    long ok = results.stream().filter(r -> r.state() == ActionState.SUCCEEDED).count();
    long failed = results.stream().filter(r -> r.state() == ActionState.FAILED).count();
    long skipped = results.stream().filter(r -> r.state() == ActionState.SKIPPED).count();

    System.out.println("Action: " + actionName + "  GroupId: " + groupId + "  DryRun: " + dryRun);
    System.out.println("Succeeded: " + ok + "  Failed: " + failed + "  Skipped: " + skipped);

    List<String[]> rows = new ArrayList<>();
    for (ActionResult r : results) {
      rows.add(
          new String[] {
            r.state().name(),
            r.device().displayNameOrId(),
            r.device().managedDeviceId(),
            r.errorMessageOrDash()
          });
    }
    sortRowsByColumn(rows, 1);
    printTable(new String[] {"State", "Device", "ManagedDeviceId", "Message"}, rows);
    System.out.println("(" + results.size() + " total)");
  }
}

