package com.mkylm.intunebulk.core;

/**
 * Canonical target device reference used throughout resolution and execution.
 *
 * <p>Holds both Entra and Intune identifiers plus display metadata for output.
 */
public record DeviceRef(
    String managedDeviceId,
    String azureAdDeviceId,
    String displayName,
    String serialNumber,
    String primaryUser,
    boolean skipped,
    String skipReason) {

  public static DeviceRef ofManagedDevice(
      String managedDeviceId,
      String azureAdDeviceId,
      String displayName,
      String serialNumber,
      String primaryUser) {
    // "Actionable" device reference (mapped to a managedDeviceId).
    return new DeviceRef(
        managedDeviceId, azureAdDeviceId, displayName, serialNumber, primaryUser, false, null);
  }

  public static DeviceRef skipped(String azureAdDeviceId, String displayName, String reason) {
    // Non-actionable reference with explanation preserved for reporting.
    return new DeviceRef(null, azureAdDeviceId, displayName, null, null, true, reason);
  }

  public String displayNameOrId() {
    if (displayName != null && !displayName.isBlank()) return displayName;
    if (managedDeviceId != null) return managedDeviceId;
    return azureAdDeviceId != null ? azureAdDeviceId : "<unknown>";
  }
}

