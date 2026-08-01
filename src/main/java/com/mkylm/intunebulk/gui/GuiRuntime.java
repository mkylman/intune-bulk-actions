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
import java.util.Locale;
import java.util.Map;
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
  private ResultsEntityType resultsEntityType = ResultsEntityType.NONE;
  private int resultsEntityIdColumn = -1;
  private int resultsEntityNameColumn = 0;
  private long progressStartedAtMs;
  private final Map<String, CachedGroupDevices> groupDevicesCache = new HashMap<>();
  private final Map<String, List<String[]>> reportRowsCache = new HashMap<>();
  private final Map<String, List<String[]>> userGroupMembersCache = new HashMap<>();

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

  synchronized List<String[]> loadUserGroupMemberRows(String groupId) {
    if (groupId == null || groupId.isBlank()) {
      throw new IllegalArgumentException("Group id is required.");
    }
    List<String[]> cached = userGroupMembersCache.get(groupId);
    if (cached != null) {
      return copyRows(cached);
    }

    String path =
        "/groups/"
            + groupId
            + "/transitiveMembers/microsoft.graph.user?$select="
            + java.net.URLEncoder.encode(
                "id,displayName,userPrincipalName", java.nio.charset.StandardCharsets.UTF_8);
    List<JsonNode> rows = graph().getV1PagedValues(path);
    List<String[]> tableRows = new ArrayList<>();
    for (JsonNode row : rows) {
      tableRows.add(
          new String[] {
            textAtPath(row, "displayName"),
            textAtPath(row, "userPrincipalName"),
            textAtPath(row, "id")
          });
    }
    tableRows.sort(
        Comparator.comparing(
            row -> row == null || row.length == 0 || row[0] == null ? "" : row[0],
            String.CASE_INSENSITIVE_ORDER));
    userGroupMembersCache.put(groupId, copyRows(tableRows));
    return copyRows(tableRows);
  }

  synchronized List<String[]> loadReportRows(ReportDefinition report) {
    if (report == null) {
      throw new IllegalArgumentException("Report definition is required.");
    }
    if (report.cacheable()) {
      List<String[]> cached = reportRowsCache.get(report.id());
      if (cached != null) {
        return copyRows(cached);
      }
    }

    List<JsonNode> graphRows;
    if (report.maxItems() != null) {
      graphRows = graph().getV1PagedValues(report.endpoint(), report.maxItems());
    } else {
      graphRows = graph().getV1PagedValues(report.endpoint());
    }

    List<String> fields = report.fields();
    List<String[]> tableRows = new ArrayList<>();
    for (JsonNode row : graphRows) {
      if (!matchesFilter(row, report.filter())) {
        continue;
      }
      String[] mapped = new String[fields.size()];
      for (int i = 0; i < fields.size(); i++) {
        mapped[i] = textAtPath(row, fields.get(i));
      }
      tableRows.add(mapped);
    }

    sortRows(tableRows, report);

    if (report.cacheable()) {
      reportRowsCache.put(report.id(), copyRows(tableRows));
    }
    return copyRows(tableRows);
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

  void updateResultsEntityMetadata(String[] columns) {
    if (columns == null) {
      clearResultsEntityMetadata();
      return;
    }
    int managedDeviceIdCol = findColumnIndex(columns, "Managed Device ID");
    if (managedDeviceIdCol >= 0) {
      resultsEntityType = ResultsEntityType.DEVICE;
      resultsEntityIdColumn = managedDeviceIdCol;
      resultsEntityNameColumn = firstPresentColumn(columns, 0, "Device Name", "Device", "Display Name");
      return;
    }
    int userIdCol = findColumnIndex(columns, "User ID");
    if (userIdCol >= 0) {
      resultsEntityType = ResultsEntityType.USER;
      resultsEntityIdColumn = userIdCol;
      resultsEntityNameColumn = firstPresentColumn(columns, 0, "Display Name", "Name");
      return;
    }
    clearResultsEntityMetadata();
  }

  void clearResultsEntityMetadata() {
    resultsEntityType = ResultsEntityType.NONE;
    resultsEntityIdColumn = -1;
    resultsEntityNameColumn = 0;
  }

  ResultsEntityType resultsEntityType() {
    return resultsEntityType;
  }

  String resultsEntityIdAtViewRow(int viewRow) {
    return cellAtViewRow(viewRow, resultsEntityIdColumn);
  }

  String resultsEntityNameAtViewRow(int viewRow) {
    return cellAtViewRow(viewRow, resultsEntityNameColumn);
  }

  private String cellAtViewRow(int viewRow, int column) {
    if (resultsTable == null || resultsModel == null || column < 0) {
      return "";
    }
    if (viewRow < 0 || viewRow >= resultsTable.getRowCount()) {
      return "";
    }
    int modelRow = resultsTable.convertRowIndexToModel(viewRow);
    if (modelRow < 0 || modelRow >= resultsModel.getRowCount()) {
      return "";
    }
    if (column >= resultsModel.getColumnCount()) {
      return "";
    }
    Object value = resultsModel.getValueAt(modelRow, column);
    return value == null ? "" : String.valueOf(value);
  }

  private static int findColumnIndex(String[] columns, String name) {
    for (int i = 0; i < columns.length; i++) {
      if (name.equals(columns[i])) {
        return i;
      }
    }
    return -1;
  }

  private static int firstPresentColumn(String[] columns, int fallback, String... names) {
    for (String name : names) {
      int idx = findColumnIndex(columns, name);
      if (idx >= 0) {
        return idx;
      }
    }
    return fallback;
  }

  void startProgressTimer() {
    progressStartedAtMs = System.currentTimeMillis();
  }

  long stopProgressTimer() {
    return Math.max(0, System.currentTimeMillis() - progressStartedAtMs);
  }

  private GroupDeviceResolver groupResolver() {
    if (groupResolver == null) {
      groupResolver = new GroupDeviceResolver(graph());
    }
    return groupResolver;
  }

  private static boolean matchesFilter(JsonNode row, ReportFilter filter) {
    if (filter == null) {
      return true;
    }

    List<ReportCondition> conditions = filter.resolvedConditions();
    if (conditions.isEmpty()) {
      return true;
    }

    boolean requireAll = !"or".equals(filter.resolvedLogic());
    if (requireAll) {
      for (ReportCondition condition : conditions) {
        if (!matchesCondition(row, condition)) {
          return false;
        }
      }
      return true;
    }

    for (ReportCondition condition : conditions) {
      if (matchesCondition(row, condition)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchesCondition(JsonNode row, ReportCondition condition) {
    if (condition == null) {
      return true;
    }
    String op = condition.op() == null ? "" : condition.op().trim().toLowerCase(Locale.ROOT);
    JsonNode actual = valueAtPath(row, condition.fieldPath());
    String expected = condition.value() == null ? "" : condition.value().trim();
    String actualText = actual == null || actual.isNull() ? "" : actual.asText("");

    return switch (op) {
      case "eq" -> {
        if (actual == null || actual.isNull()) {
          yield expected.isEmpty() || "null".equalsIgnoreCase(expected);
        }
        yield expected.equalsIgnoreCase(actualText);
      }
      case "contains" ->
          !expected.isEmpty()
              && actualText.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
      case "doesnotcontain" ->
          expected.isEmpty()
              || !actualText.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
      default -> true;
    };
  }

  private static void sortRows(List<String[]> rows, ReportDefinition report) {
    int columnIndex = 0;
    if (report.sortByField() != null && !report.sortByField().isBlank()) {
      int idx = report.fields().indexOf(report.sortByField());
      if (idx >= 0) {
        columnIndex = idx;
      }
    }

    final int sortColumn = columnIndex;
    rows.sort(
        Comparator.comparing(
            row -> {
              if (row == null || sortColumn >= row.length || row[sortColumn] == null) {
                return "";
              }
              return row[sortColumn];
            },
            String.CASE_INSENSITIVE_ORDER));

    if (report.sortDirection() != null
        && "desc".equalsIgnoreCase(report.sortDirection().trim())) {
      java.util.Collections.reverse(rows);
    }
  }

  private static List<String[]> copyRows(List<String[]> rows) {
    List<String[]> copy = new ArrayList<>(rows.size());
    for (String[] row : rows) {
      copy.add(row == null ? null : row.clone());
    }
    return copy;
  }

  private static JsonNode valueAtPath(JsonNode node, String fieldPath) {
    if (node == null || fieldPath == null || fieldPath.isBlank()) {
      return null;
    }
    JsonNode current = node;
    for (String part : fieldPath.split("\\.")) {
      if (current == null || current.isNull() || !current.isObject()) {
        return null;
      }
      current = current.get(part);
    }
    return current;
  }

  private static String textAtPath(JsonNode node, String fieldPath) {
    JsonNode value = valueAtPath(node, fieldPath);
    if (value == null || value.isNull()) {
      return "";
    }
    return value.asText("");
  }

  private record CachedGroupDevices(List<DeviceRef> devices, Instant expiresAt) {}
}
