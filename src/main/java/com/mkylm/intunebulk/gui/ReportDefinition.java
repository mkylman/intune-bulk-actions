package com.mkylm.intunebulk.gui;

import java.util.List;

/**
 * Config-driven report definition used by the GUI Reports dropdown.
 *
 * <p>{@code columns} and {@code fields} must be the same length and order.
 * {@code filter} may be null when no post-query filter is needed.
 */
record ReportDefinition(
    String id,
    String label,
    String endpoint,
    List<String> columns,
    List<String> fields,
    ReportFilter filter,
    String sortByField,
    String sortDirection,
    Integer maxItems,
    boolean cacheable) {}
