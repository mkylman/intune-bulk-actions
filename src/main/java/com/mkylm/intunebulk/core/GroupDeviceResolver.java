package com.mkylm.intunebulk.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.mkylm.intunebulk.graph.GraphClient;
import com.mkylm.intunebulk.graph.GraphException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves devices from an Azure AD group to Intune managedDevice IDs.
 *
 * Design:
 *  - Fetch AAD devices: /groups/{id}/transitiveMembers/microsoft.graph.device
 *  - Map to managedDevice: /deviceManagement/managedDevices?$filter=azureADDeviceId eq '{deviceId}'
 */
public final class GroupDeviceResolver {
  @FunctionalInterface
  public interface ProgressListener {
    void onProgress(ProgressSnapshot progress);
  }

  public record ProgressSnapshot(
      int totalMembers, int processedMembers, int mappedMembers, int skippedMembers, String currentName) {}

  private final GraphClient graph;

  public GroupDeviceResolver(GraphClient graph) {
    this.graph = graph;
  }

  public List<DeviceRef> resolveFromAadGroup(String groupId) {
    return resolveFromAadGroup(groupId, null);
  }

  public List<DeviceRef> resolveFromAadGroup(String groupId, ProgressListener progressListener) {
    try {
      List<DeviceRef> out = new ArrayList<>();

      // 1) Read Entra group membership (transitive) as directory device objects.
      // GET /groups/{groupId}/transitiveMembers/microsoft.graph.device?$select=id,deviceId,displayName
      String membersPath =
          "/groups/"
              + groupId
              + "/transitiveMembers/microsoft.graph.device?$select=id,deviceId,displayName";
      List<JsonNode> aadDevices = graph.getV1PagedValues(membersPath);
      int totalMembers = aadDevices.size();
      int processed = 0;
      int mapped = 0;
      int skipped = 0;

      if (progressListener != null && totalMembers == 0) {
        progressListener.onProgress(new ProgressSnapshot(0, 0, 0, 0, null));
      }

      // 2) Bridge each Entra device object to Intune managedDevice via azureADDeviceId.
      for (JsonNode aad : aadDevices) {
        String azureAdDeviceId = text(aad, "deviceId"); // GUID string
        String displayName = text(aad, "displayName");

        if (azureAdDeviceId == null || azureAdDeviceId.isBlank()) {
          // Membership entry exists, but lacks the deviceId needed for Intune lookup.
          out.add(DeviceRef.skipped(null, displayName, "AAD deviceId missing"));
          skipped++;
          processed++;
          emitProgress(progressListener, totalMembers, processed, mapped, skipped, displayName);
          continue;
        }

        // Query Intune managedDevices for the corresponding Entra device.
        // NOTE: Not paged because match cardinality should be tiny (normally 0 or 1).
        String mdPath =
            "/deviceManagement/managedDevices"
                + "?$filter="
                + urlEncode("azureADDeviceId eq '" + azureAdDeviceId + "'")
                + "&$select="
                + urlEncode("id,deviceName,serialNumber,userPrincipalName,azureADDeviceId");
        JsonNode mdPage = graph.getV1Json(mdPath);
        JsonNode mdValues = mdPage.get("value");

        if (mdValues == null || !mdValues.isArray() || mdValues.isEmpty()) {
          // Device is not currently represented as an enrolled Intune managedDevice.
          out.add(DeviceRef.skipped(azureAdDeviceId, displayName, "Not enrolled in Intune (no managedDevice match)"));
          skipped++;
          processed++;
          emitProgress(progressListener, totalMembers, processed, mapped, skipped, displayName);
          continue;
        }

        // If multiple, pick the first for now. (We can refine selection rules later.)
        JsonNode md = mdValues.get(0);
        String managedDeviceId = text(md, "id");
        String deviceName = firstNonBlank(text(md, "deviceName"), displayName);
        String serial = text(md, "serialNumber");
        String primaryUser = text(md, "userPrincipalName");

        if (managedDeviceId == null || managedDeviceId.isBlank()) {
          // Defensive check for malformed responses.
          out.add(DeviceRef.skipped(azureAdDeviceId, displayName, "managedDevice id missing"));
          skipped++;
          processed++;
          emitProgress(progressListener, totalMembers, processed, mapped, skipped, displayName);
          continue;
        }

        out.add(
            DeviceRef.ofManagedDevice(
                managedDeviceId,
                azureAdDeviceId,
                deviceName,
                serial,
                primaryUser));
        mapped++;
        processed++;
        emitProgress(progressListener, totalMembers, processed, mapped, skipped, deviceName);
      }

      return out;
    } catch (GraphException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to resolve group devices: " + e.getMessage(), e);
    }
  }

  private static void emitProgress(
      ProgressListener progressListener,
      int totalMembers,
      int processedMembers,
      int mappedMembers,
      int skippedMembers,
      String currentName) {
    if (progressListener == null) {
      return;
    }
    progressListener.onProgress(
        new ProgressSnapshot(
            totalMembers, processedMembers, mappedMembers, skippedMembers, currentName));
  }

  private static String text(JsonNode node, String field) {
    if (node == null) return null;
    JsonNode v = node.get(field);
    return (v != null && v.isTextual()) ? v.asText() : null;
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b;
  }

  private static String urlEncode(String s) {
    try {
      return java.net.URLEncoder.encode(Objects.requireNonNull(s), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to URL-encode value", e);
    }
  }

}

