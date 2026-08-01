package com.mkylm.intunebulk.gui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Loads and holds GUI report definitions from {@code reports.json}, falling back to built-in
 * defaults when the file is missing or invalid.
 */
final class ReportRegistry {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final TypeReference<List<ReportDefinition>> REPORT_LIST =
      new TypeReference<>() {};

  static final String DEFAULT_REPORTS_JSON =
      """
      [
        {
          "id": "all-users",
          "label": "All Users",
          "endpoint": "/users?$select=id,displayName,userPrincipalName",
          "columns": ["Display Name", "UPN", "User ID"],
          "fields": ["displayName", "userPrincipalName", "id"],
          "filter": null,
          "sortByField": "displayName",
          "sortDirection": "asc",
          "maxItems": null,
          "cacheable": true
        },
        {
          "id": "all-devices",
          "label": "All Devices",
          "endpoint": "/deviceManagement/managedDevices?$select=id,deviceName,serialNumber",
          "columns": ["Device Name", "Serial", "Managed Device ID"],
          "fields": ["deviceName", "serialNumber", "id"],
          "filter": null,
          "sortByField": "deviceName",
          "sortDirection": "asc",
          "maxItems": null,
          "cacheable": true
        },
        {
          "id": "expired-passwords",
          "label": "Expired Passwords",
          "endpoint": "/users?$select=id,displayName,userPrincipalName,passwordProfile",
          "columns": ["Display Name", "UPN", "User ID"],
          "fields": ["displayName", "userPrincipalName", "id"],
          "filter": {
            "fieldPath": "passwordProfile.forceChangePasswordNextSignIn",
            "op": "eq",
            "value": "true"
          },
          "sortByField": "displayName",
          "sortDirection": "asc",
          "maxItems": null,
          "cacheable": true
        }
      ]
      """;

  private final List<ReportDefinition> reports;
  private final boolean usingDefaults;
  private final String loadMessage;

  private ReportRegistry(List<ReportDefinition> reports, boolean usingDefaults, String loadMessage) {
    this.reports = List.copyOf(reports);
    this.usingDefaults = usingDefaults;
    this.loadMessage = loadMessage;
  }

  /** Built-in report definitions used when the file is missing or invalid. */
  static ReportRegistry defaults() {
    try {
      List<ReportDefinition> reports = parseAndValidate(DEFAULT_REPORTS_JSON);
      return new ReportRegistry(reports, true, "Using built-in default reports.");
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Built-in default reports failed validation.", e);
    }
  }

  /**
   * Loads reports from {@code reportsPath}. Falls back to {@link #defaults()} when the file is
   * missing, unreadable, or fails validation.
   */
  static ReportRegistry loadOrDefaults(Path reportsPath) {
    if (reportsPath == null || !Files.isRegularFile(reportsPath)) {
      return defaults();
    }

    try {
      String json = Files.readString(reportsPath);
      List<ReportDefinition> reports = parseAndValidate(json);
      return new ReportRegistry(
          reports, false, "Loaded " + reports.size() + " report(s) from " + reportsPath);
    } catch (IOException | IllegalArgumentException e) {
      System.err.println(
          "[GUI] Invalid or unreadable reports.json ("
              + e.getMessage()
              + "); falling back to built-in defaults.");
      ReportRegistry fallback = defaults();
      return new ReportRegistry(
          fallback.all(),
          true,
          "Failed to load "
              + reportsPath
              + " ("
              + e.getMessage()
              + "); using built-in defaults.");
    }
  }

  List<ReportDefinition> all() {
    return reports;
  }

  Optional<ReportDefinition> findById(String id) {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }
    return reports.stream().filter(r -> id.equals(r.id())).findFirst();
  }

  Optional<ReportDefinition> findByLabel(String label) {
    if (label == null || label.isBlank()) {
      return Optional.empty();
    }
    return reports.stream().filter(r -> label.equals(r.label())).findFirst();
  }

  List<String> labels() {
    List<String> labels = new ArrayList<>(reports.size());
    for (ReportDefinition report : reports) {
      labels.add(report.label());
    }
    return List.copyOf(labels);
  }

  boolean usingDefaults() {
    return usingDefaults;
  }

  String loadMessage() {
    return loadMessage;
  }

  private static List<ReportDefinition> parseAndValidate(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("Reports JSON is empty.");
    }

    List<ReportDefinition> parsed;
    try {
      parsed = MAPPER.readValue(json, REPORT_LIST);
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not parse reports JSON: " + e.getMessage(), e);
    }

    if (parsed == null || parsed.isEmpty()) {
      throw new IllegalArgumentException("Reports JSON must contain at least one report.");
    }

    Set<String> ids = new HashSet<>();
    Set<String> labels = new HashSet<>();
    List<ReportDefinition> validated = new ArrayList<>(parsed.size());
    for (int i = 0; i < parsed.size(); i++) {
      ReportDefinition report = parsed.get(i);
      String prefix = "Report[" + i + "]";
      if (report == null) {
        throw new IllegalArgumentException(prefix + " is null.");
      }
      validateReport(report, prefix);
      if (!ids.add(report.id())) {
        throw new IllegalArgumentException(prefix + " has duplicate id: " + report.id());
      }
      if (!labels.add(report.label())) {
        throw new IllegalArgumentException(prefix + " has duplicate label: " + report.label());
      }
      validated.add(report);
    }
    return validated;
  }

  private static void validateReport(ReportDefinition report, String prefix) {
    requireNonBlank(report.id(), prefix + ".id");
    requireNonBlank(report.label(), prefix + ".label");
    requireNonBlank(report.endpoint(), prefix + ".endpoint");
    if (!report.endpoint().startsWith("/")) {
      throw new IllegalArgumentException(prefix + ".endpoint must start with '/'.");
    }

    if (report.columns() == null || report.columns().isEmpty()) {
      throw new IllegalArgumentException(prefix + ".columns must be a non-empty array.");
    }
    if (report.fields() == null || report.fields().isEmpty()) {
      throw new IllegalArgumentException(prefix + ".fields must be a non-empty array.");
    }
    if (report.columns().size() != report.fields().size()) {
      throw new IllegalArgumentException(
          prefix
              + ".columns and .fields must have the same length (columns="
              + report.columns().size()
              + ", fields="
              + report.fields().size()
              + ").");
    }
    for (int i = 0; i < report.columns().size(); i++) {
      requireNonBlank(report.columns().get(i), prefix + ".columns[" + i + "]");
      requireNonBlank(report.fields().get(i), prefix + ".fields[" + i + "]");
    }

    if (report.filter() != null) {
      validateFilter(report.filter(), prefix + ".filter");
    }

    if (report.sortByField() != null && report.sortByField().isBlank()) {
      throw new IllegalArgumentException(prefix + ".sortByField must not be blank when set.");
    }
    if (report.sortDirection() != null && !report.sortDirection().isBlank()) {
      String direction = report.sortDirection().trim().toLowerCase(Locale.ROOT);
      if (!"asc".equals(direction) && !"desc".equals(direction)) {
        throw new IllegalArgumentException(
            prefix + ".sortDirection must be 'asc' or 'desc' (got: " + report.sortDirection() + ").");
      }
    }
    if (report.maxItems() != null && report.maxItems() <= 0) {
      throw new IllegalArgumentException(prefix + ".maxItems must be null or a positive integer.");
    }
  }

  private static void validateFilter(ReportFilter filter, String prefix) {
    if (filter.hasCompoundConditions()) {
      if (filter.logic() != null && !filter.logic().isBlank()) {
        String logic = filter.logic().trim().toLowerCase(Locale.ROOT);
        if (!"and".equals(logic) && !"or".equals(logic)) {
          throw new IllegalArgumentException(
              prefix + ".logic must be 'and' or 'or' (got: " + filter.logic() + ").");
        }
      }
      for (int i = 0; i < filter.conditions().size(); i++) {
        ReportCondition condition = filter.conditions().get(i);
        if (condition == null) {
          throw new IllegalArgumentException(prefix + ".conditions[" + i + "] is null.");
        }
        validateCondition(condition, prefix + ".conditions[" + i + "]");
      }
      return;
    }

    boolean anySimpleField =
        (filter.fieldPath() != null && !filter.fieldPath().isBlank())
            || (filter.op() != null && !filter.op().isBlank())
            || (filter.value() != null && !filter.value().isBlank());
    if (!anySimpleField) {
      throw new IllegalArgumentException(
          prefix
              + " must be a simple condition (fieldPath/op/value) or a non-empty conditions array.");
    }
    validateCondition(
        new ReportCondition(filter.fieldPath(), filter.op(), filter.value()), prefix);
  }

  private static void validateCondition(ReportCondition condition, String prefix) {
    requireNonBlank(condition.fieldPath(), prefix + ".fieldPath");
    requireNonBlank(condition.op(), prefix + ".op");
    requireNonBlank(condition.value(), prefix + ".value");
    String op = condition.op().trim().toLowerCase(Locale.ROOT);
    if (!"eq".equals(op) && !"contains".equals(op) && !"doesnotcontain".equals(op)) {
      throw new IllegalArgumentException(
          prefix
              + ".op must be 'eq', 'contains', or 'doesnotcontain' (got: "
              + condition.op()
              + ").");
    }
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required.");
    }
  }
}
