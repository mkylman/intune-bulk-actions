package com.mkylm.intunebulk.gui;

import java.util.List;
import java.util.Locale;

/**
 * Post-query filter applied to Graph rows.
 *
 * <p>Supports either:
 * <ul>
 *   <li>Simple: {@code fieldPath}/{@code op}/{@code value}
 *   <li>Compound: {@code logic} ({@code and}|{@code or}) + {@code conditions}
 * </ul>
 */
record ReportFilter(
    String fieldPath,
    String op,
    String value,
    String logic,
    List<ReportCondition> conditions) {

  boolean hasCompoundConditions() {
    return conditions != null && !conditions.isEmpty();
  }

  List<ReportCondition> resolvedConditions() {
    if (hasCompoundConditions()) {
      return List.copyOf(conditions);
    }
    if (fieldPath != null && !fieldPath.isBlank()) {
      return List.of(new ReportCondition(fieldPath, op, value));
    }
    return List.of();
  }

  String resolvedLogic() {
    if (logic == null || logic.isBlank()) {
      return "and";
    }
    return logic.trim().toLowerCase(Locale.ROOT);
  }
}
