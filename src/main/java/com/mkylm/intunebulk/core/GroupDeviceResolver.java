package com.mkylm.intunebulk.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.mkylm.intunebulk.graph.GraphClient;
import com.mkylm.intunebulk.graph.GraphException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resolves devices from an Azure AD group to Intune managedDevice IDs.
 *
 * <p>Design:
 * <ul>
 *   <li>Fetch AAD devices: {@code /groups/{id}/transitiveMembers/microsoft.graph.device}
 *   <li>Map via a session-wide managed-device index keyed by {@code azureADDeviceId}
 *   <li>Fall back to per-device Intune {@code $filter} lookups if the index cannot be built
 * </ul>
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
  private volatile Map<String, DeviceRef> managedDeviceIndex;
  private volatile boolean managedDeviceIndexLoadFailed;

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

      // 2) Bridge each Entra device to Intune via local index (or per-device lookup fallback).
      Map<String, DeviceRef> index = ensureManagedDeviceIndex();
      List<DeviceRef> resolved =
          index != null
              ? resolveManagedDevicesFromIndex(aadDevices, index)
              : resolveManagedDevicesInParallel(aadDevices);

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

  private Map<String, DeviceRef> ensureManagedDeviceIndex() {
    Map<String, DeviceRef> existing = managedDeviceIndex;
    if (existing != null) {
      return existing;
    }
    if (managedDeviceIndexLoadFailed) {
      return null;
    }

    synchronized (this) {
      if (managedDeviceIndex != null) {
        return managedDeviceIndex;
      }
      if (managedDeviceIndexLoadFailed) {
        return null;
      }
      try {
        managedDeviceIndex = buildManagedDeviceIndex();
        return managedDeviceIndex;
      } catch (Exception e) {
        managedDeviceIndexLoadFailed = true;
        System.err.println(
            "[GroupDeviceResolver] Failed to build managed-device index ("
                + e.getMessage()
                + "); falling back to per-device lookups.");
        return null;
      }
    }
  }

  private Map<String, DeviceRef> buildManagedDeviceIndex() {
    String path =
        "/deviceManagement/managedDevices?$select="
            + urlEncode("id,deviceName,serialNumber,userPrincipalName,azureADDeviceId");
    List<JsonNode> managedDevices = graph.getV1PagedValues(path);
    Map<String, DeviceRef> index = new HashMap<>();
    for (JsonNode md : managedDevices) {
      String azureAdDeviceId = text(md, "azureADDeviceId");
      if (azureAdDeviceId == null || azureAdDeviceId.isBlank()) {
        continue;
      }
      if (index.containsKey(azureAdDeviceId)) {
        continue; // Keep first match for duplicates.
      }
      String managedDeviceId = text(md, "id");
      if (managedDeviceId == null || managedDeviceId.isBlank()) {
        continue;
      }
      index.put(
          azureAdDeviceId,
          DeviceRef.ofManagedDevice(
              managedDeviceId,
              azureAdDeviceId,
              text(md, "deviceName"),
              text(md, "serialNumber"),
              text(md, "userPrincipalName")));
    }
    return Map.copyOf(index);
  }

  private List<DeviceRef> resolveManagedDevicesFromIndex(
      List<JsonNode> aadDevices, Map<String, DeviceRef> index) {
    if (aadDevices == null || aadDevices.isEmpty()) {
      return List.of();
    }

    List<DeviceRef> result = new ArrayList<>(aadDevices.size());
    for (JsonNode aad : aadDevices) {
      String azureAdDeviceId = text(aad, "deviceId");
      String displayName = text(aad, "displayName");

      if (azureAdDeviceId == null || azureAdDeviceId.isBlank()) {
        result.add(DeviceRef.skipped(null, displayName, "AAD deviceId missing"));
        continue;
      }

      DeviceRef indexed = index.get(azureAdDeviceId);
      if (indexed == null) {
        result.add(
            DeviceRef.skipped(
                azureAdDeviceId, displayName, "Not enrolled in Intune (no managedDevice match)"));
        continue;
      }

      // Prefer AAD display name when Intune deviceName is blank.
      String deviceName = firstNonBlank(indexed.displayName(), displayName);
      if (Objects.equals(deviceName, indexed.displayName())) {
        result.add(indexed);
      } else {
        result.add(
            DeviceRef.ofManagedDevice(
                indexed.managedDeviceId(),
                indexed.azureAdDeviceId(),
                deviceName,
                indexed.serialNumber(),
                indexed.primaryUser()));
      }
    }
    return result;
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
    String azureAdDeviceId = text(aad, "deviceId");
    String displayName = text(aad, "displayName");

    if (azureAdDeviceId == null || azureAdDeviceId.isBlank()) {
      return DeviceRef.skipped(null, displayName, "AAD deviceId missing");
    }

    String mdPath =
        "/deviceManagement/managedDevices"
            + "?$filter="
            + urlEncode("azureADDeviceId eq '" + azureAdDeviceId + "'")
            + "&$select="
            + urlEncode("id,deviceName,serialNumber,userPrincipalName,azureADDeviceId");
    JsonNode mdPage = graph.getV1Json(mdPath);
    JsonNode mdValues = mdPage.get("value");

    if (mdValues == null || !mdValues.isArray() || mdValues.isEmpty()) {
      return DeviceRef.skipped(
          azureAdDeviceId, displayName, "Not enrolled in Intune (no managedDevice match)");
    }

    JsonNode md = mdValues.get(0);
    String managedDeviceId = text(md, "id");
    String deviceName = firstNonBlank(text(md, "deviceName"), displayName);
    String serial = text(md, "serialNumber");
    String primaryUser = text(md, "userPrincipalName");

    if (managedDeviceId == null || managedDeviceId.isBlank()) {
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
