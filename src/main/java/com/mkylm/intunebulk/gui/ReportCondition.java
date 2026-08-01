package com.mkylm.intunebulk.gui;

/** Single post-query filter condition. */
record ReportCondition(
    String fieldPath, // e.g. passwordProfile.forceChangePasswordNextSignIn
    String op, // eq | contains | doesnotcontain
    String value) {}
