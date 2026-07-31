package com.mkylm.intunebulk.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mkylm.intunebulk.core.DeviceActionService;
import com.mkylm.intunebulk.core.DeviceRef;
import com.mkylm.intunebulk.core.GroupDeviceResolver;
import com.mkylm.intunebulk.graph.GraphClient;
import com.mkylm.intunebulk.graph.TokenProvider;
import com.mkylm.intunebulk.graph.TokenProviderFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

final class GuiRuntime {
  private static final Duration GROUP_DEVICE_CACHE_TTL = Duration.ofMinutes(5);

  private GraphClient graph;
  private GroupDeviceResolver groupResolver;
  private DeviceActionService actionService;
  JTable resultsTable;
  DefaultTableModel resultsModel;
  TableRowSorter<DefaultTableModel> resultsSorter;
  JTextField resultsFilterField;
  private JLabel elapsedLabel;
  private long progressStartedAtMs;
  private final Map<String, CachedGroupDevices> groupDevicesCache = new HashMap<>();
  private List<String[]> usersRowsCache;
  private List<String[]> devicesRowsCache;

  GraphClient graph() {
    if (graph == null) {
      TokenProvider tokenProvider = TokenProviderFactory.fromEnvironment();
      graph = GraphClient.createDefault(tokenProvider);
    }
    return graph;
  }

  DeviceActionService actionService() {
    if (actionService == null) {
      actionService = new DeviceActionService(graph());
    }
    return actionService;
  }

  List<DeviceRef> resolveGroupDevices(String groupId) {
    Instant now = Instant.now();
    CachedGroupDevices cached = groupDevicesCache.get(groupId);
    if (cached != null && now.isBefore(cached.expiresAt())) {
      return cached.devices();
    }

    List<DeviceRef> resolved = List.copyOf(groupResolver().resolveFromAadGroup(groupId));
    groupDevicesCache.put(groupId, new CachedGroupDevices(resolved, now.plus(GROUP_DEVICE_CACHE_TTL)));
    return resolved;
  }

  synchronized List<String[]> loadUsersRows() {
    if (usersRowsCache != null) {
      return copyRows(usersRowsCache);
    }

    String path =
        "/users?$select=id,displayName,userPrincipalName";
    List<JsonNode> rows = graph().getV1PagedValues(path);
    List<String[]> tableRows = new ArrayList<>();
    for (JsonNode row : rows) {
      tableRows.add(
          new String[] {
            text(row, "displayName"), text(row, "userPrincipalName"), text(row, "id")
          });
    }
    sortRowsByColumn(tableRows, 0);
    usersRowsCache = copyRows(tableRows);
    return copyRows(usersRowsCache);
  }

  synchronized List<String[]> loadDevicesRows() {
    if (devicesRowsCache != null) {
      return copyRows(devicesRowsCache);
    }

    String path =
        "/deviceManagement/managedDevices?$select="
            + java.net.URLEncoder.encode("id,deviceName,serialNumber", java.nio.charset.StandardCharsets.UTF_8);
    List<JsonNode> rows = graph().getV1PagedValues(path);
    List<String[]> tableRows = new ArrayList<>();
    for (JsonNode row : rows) {
      tableRows.add(new String[] {text(row, "deviceName"), text(row, "serialNumber"), text(row, "id")});
    }
    sortRowsByColumn(tableRows, 0);
    devicesRowsCache = copyRows(tableRows);
    return copyRows(devicesRowsCache);
  }

  void applyResultsFilter() {
    if (resultsSorter == null) {
      return;
    }
    String query = resultsFilterField == null ? "" : resultsFilterField.getText();
    if (query == null || query.isBlank()) {
      resultsSorter.setRowFilter(null);
      return;
    }
    resultsSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(query.trim())));
  }

  void attachElapsedLabel(JLabel elapsedLabel) {
    this.elapsedLabel = elapsedLabel;
    if (this.elapsedLabel != null) {
      this.elapsedLabel.setText("Elapsed: -- ms");
    }
  }

  void startProgressTimer() {
    progressStartedAtMs = System.currentTimeMillis();
    if (elapsedLabel != null) {
      elapsedLabel.setText("Elapsed: ...");
    }
  }

  long stopProgressTimer() {
    long elapsedMs = Math.max(0, System.currentTimeMillis() - progressStartedAtMs);
    if (elapsedLabel != null) {
      elapsedLabel.setText("Elapsed: " + elapsedMs + " ms");
    }
    return elapsedMs;
  }

  private GroupDeviceResolver groupResolver() {
    if (groupResolver == null) {
      groupResolver = new GroupDeviceResolver(graph());
    }
    return groupResolver;
  }

  private static List<String[]> copyRows(List<String[]> rows) {
    List<String[]> copy = new ArrayList<>(rows.size());
    for (String[] row : rows) {
      copy.add(row == null ? null : row.clone());
    }
    return copy;
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

  private static String text(JsonNode node, String field) {
    if (node == null) {
      return "";
    }
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) {
      return "";
    }
    return value.asText("");
  }

  private record CachedGroupDevices(List<DeviceRef> devices, Instant expiresAt) {}
}
