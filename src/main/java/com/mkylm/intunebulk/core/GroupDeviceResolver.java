package com.mkylm.intunebulk.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.mkylm.intunebulk.graph.GraphClient;
import com.mkylm.intunebulk.graph.GraphException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resolves devices from an Azure AD group to Intune managedDevice IDs.
 *
 * Design:
 *  - Fetch AAD devices: /groups/{id}/transitiveMembers/microsoft.graph.device
 *  - Map to managedDevice: /deviceManagement/managedDevices?$filter=azureADDeviceId eq '{deviceId}'
 */
public final class GroupDeviceResolver {
  private static final int DEFAULT_LOOKUP_CONCURRENCY = 8;

  @FunctionalInterface
  public interface ProgressListener {
    void onProgress(ProgressSnapshot progress);
  }

  public record ProgressSnapshot(
      int totalMembers, int processedMembers, int mappedMembers, int skippedMembers, String currentName) {}

  private final GraphClient graph;
  private final int lookupConcurrency;

  public GroupDeviceResolver(GraphClient graph) {
    this(graph, DEFAULT_LOOKUP_CONCURRENCY);
  }

  public GroupDeviceResolver(GraphClient graph, int lookupConcurrency) {
    this.graph = graph;
    this.lookupConcurrency = Math.max(1, lookupConcurrency);
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
      // We resolve these lookups in parallel with a bounded pool to reduce end-to-end latency.
      List<DeviceRef> resolved = resolveManagedDevicesInParallel(aadDevices);
      for (DeviceRef deviceRef : resolved) {
        out.add(deviceRef);
        if (deviceRef.skipped()) {
          skipped++;
        } else {
          mapped++;
        }
        processed++;
        emitProgress(progressListener, totalMembers, processed, mapped, skipped, deviceRef.displayNameOrId());
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

  private List<DeviceRef> resolveManagedDevicesInParallel(List<JsonNode> aadDevices)
      throws InterruptedException, ExecutionException {
    if (aadDevices == null || aadDevices.isEmpty()) {
      return List.of();
    }

    int workerCount = Math.max(1, Math.min(lookupConcurrency, aadDevices.size()));
    ExecutorService pool = Executors.newFixedThreadPool(workerCount);
    CompletionService<IndexedDeviceRef> completionService = new ExecutorCompletionService<>(pool);
    try {
      for (int i = 0; i < aadDevices.size(); i++) {
        final int index = i;
        final JsonNode aadDevice = aadDevices.get(i);
        completionService.submit(() -> new IndexedDeviceRef(index, resolveManagedDeviceRef(aadDevice)));
      }

      DeviceRef[] ordered = new DeviceRef[aadDevices.size()];
      for (int i = 0; i < aadDevices.size(); i++) {
        IndexedDeviceRef completed = completionService.take().get();
        ordered[completed.index()] = completed.deviceRef();
      }

      List<DeviceRef> result = new ArrayList<>(ordered.length);
      for (DeviceRef deviceRef : ordered) {
        result.add(deviceRef);
      }
      return result;
    } finally {
      pool.shutdownNow();
    }
  }

  private DeviceRef resolveManagedDeviceRef(JsonNode aad) {
    String azureAdDeviceId = text(aad, "deviceId"); // GUID string
    String displayName = text(aad, "displayName");

    if (azureAdDeviceId == null || azureAdDeviceId.isBlank()) {
      // Membership entry exists, but lacks the deviceId needed for Intune lookup.
      return DeviceRef.skipped(null, displayName, "AAD deviceId missing");
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
      return DeviceRef.skipped(
          azureAdDeviceId, displayName, "Not enrolled in Intune (no managedDevice match)");
    }

    // If multiple, pick the first for now. (We can refine selection rules later.)
    JsonNode md = mdValues.get(0);
    String managedDeviceId = text(md, "id");
    String deviceName = firstNonBlank(text(md, "deviceName"), displayName);
    String serial = text(md, "serialNumber");
    String primaryUser = text(md, "userPrincipalName");

    if (managedDeviceId == null || managedDeviceId.isBlank()) {
      // Defensive check for malformed responses.
      return DeviceRef.skipped(azureAdDeviceId, displayName, "managedDevice id missing");
    }

    return DeviceRef.ofManagedDevice(managedDeviceId, azureAdDeviceId, deviceName, serial, primaryUser);
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

  private record IndexedDeviceRef(int index, DeviceRef deviceRef) {}

}

