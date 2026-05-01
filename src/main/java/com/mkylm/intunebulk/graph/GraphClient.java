package com.mkylm.intunebulk.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin Microsoft Graph transport wrapper.
 *
 * <p>Encapsulates:
 * <ul>
 *   <li>Bearer token injection
 *   <li>HTTP request/response handling
 *   <li>basic transient/permanent error classification
 *   <li>OData pagination helpers
 * </ul>
 */
public final class GraphClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient http;
  private final TokenProvider tokenProvider;
  private final String v1BaseUrl;
  private final String betaBaseUrl;

  public static GraphClient createDefault(TokenProvider tokenProvider) {
    return new GraphClient(
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
        tokenProvider,
        "https://graph.microsoft.com/v1.0",
        "https://graph.microsoft.com/beta");
  }

  public GraphClient(HttpClient http, TokenProvider tokenProvider, String v1BaseUrl, String betaBaseUrl) {
    this.http = http;
    this.tokenProvider = tokenProvider;
    this.v1BaseUrl = v1BaseUrl;
    this.betaBaseUrl = betaBaseUrl;
  }

  public GraphResponse postV1(String path, Object jsonBody) {
    return post(v1BaseUrl, path, jsonBody);
  }

  public GraphResponse postBeta(String path, Object jsonBody) {
    return post(betaBaseUrl, path, jsonBody);
  }

  public GraphResponse deleteBeta(String path) {
    return delete(betaBaseUrl, path);
  }

  public JsonNode getV1Json(String path) {
    return getJson(v1BaseUrl, path);
  }

  public JsonNode getBetaJson(String path) {
    return getJson(betaBaseUrl, path);
  }

  /**
   * Fetches an OData collection and returns the concatenated {@code value[]} items across pages.
   *
   * <p>Expects the response shape: {@code { "value": [...], "@odata.nextLink": "..." }}.
   */
  public List<JsonNode> getV1PagedValues(String path) {
    return getPagedValues(v1BaseUrl, path);
  }

  public List<JsonNode> getV1PagedValues(String path, int maxItems) {
    // Used by shell/list commands where operators may cap output.
    return getPagedValues(v1BaseUrl, path, maxItems);
  }

  public List<JsonNode> getBetaPagedValues(String path) {
    return getPagedValues(betaBaseUrl, path);
  }

  public Map<String, Object> json(Object... kvPairs) {
    if (kvPairs.length % 2 != 0) throw new IllegalArgumentException("json() requires even number of args");
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kvPairs.length; i += 2) {
      m.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
    }
    return m;
  }

  private JsonNode getJson(String baseUrl, String path) {
    // Standard authenticated Graph GET call for a single JSON payload.
    String clientRequestId = UUID.randomUUID().toString();
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
            .header("Accept", "application/json")
            .header("client-request-id", clientRequestId)
            .GET()
            .build();

    try {
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      int status = resp.statusCode();
      String requestId = resp.headers().firstValue("request-id").orElse(clientRequestId);

      if (status == 429 || status >= 500) {
        throw GraphException.transientFailure(status, requestId, "transient", resp.body());
      }
      if (status >= 400) {
        throw GraphException.permanentFailure(status, requestId, "http_" + status, resp.body());
      }

      return MAPPER.readTree(resp.body());
    } catch (GraphException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Graph GET failed: " + e.getMessage(), e);
    }
  }

  private List<JsonNode> getPagedValues(String baseUrl, String path) {
    return getPagedValues(baseUrl, path, -1);
  }

  private List<JsonNode> getPagedValues(String baseUrl, String path, int maxItems) {
    // Follow @odata.nextLink until exhaustion or optional item cap.
    List<JsonNode> out = new ArrayList<>();

    String next = baseUrl + path;
    while (next != null && !next.isBlank()) {
      JsonNode page = getJsonAbsolute(next);
      JsonNode value = page.get("value");
      if (value != null && value.isArray()) {
        for (JsonNode item : value) {
          out.add(item);
          if (maxItems > 0 && out.size() >= maxItems) {
            return out;
          }
        }
      }
      JsonNode nextLink = page.get("@odata.nextLink");
      next = (nextLink != null && nextLink.isTextual()) ? nextLink.asText() : null;
    }

    return out;
  }

  private JsonNode getJsonAbsolute(String url) {
    // Variant for absolute nextLink URLs returned by Graph pagination.
    String clientRequestId = UUID.randomUUID().toString();
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
            .header("Accept", "application/json")
            .header("client-request-id", clientRequestId)
            .GET()
            .build();

    try {
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      int status = resp.statusCode();
      String requestId = resp.headers().firstValue("request-id").orElse(clientRequestId);

      if (status == 429 || status >= 500) {
        throw GraphException.transientFailure(status, requestId, "transient", resp.body());
      }
      if (status >= 400) {
        throw GraphException.permanentFailure(status, requestId, "http_" + status, resp.body());
      }

      return MAPPER.readTree(resp.body());
    } catch (GraphException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Graph GET failed: " + e.getMessage(), e);
    }
  }

  private GraphResponse post(String baseUrl, String path, Object jsonBody) {
    // Standard authenticated Graph POST call for device action endpoints.
    String body;
    try {
      body = (jsonBody instanceof String s) ? s : MAPPER.writeValueAsString(jsonBody);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize JSON body: " + e.getMessage(), e);
    }

    String clientRequestId = UUID.randomUUID().toString();

    // NOTE: This currently won't succeed against Graph until TokenProvider is implemented.
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
            .header("Content-Type", "application/json")
            .header("client-request-id", clientRequestId)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    try {
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      int status = resp.statusCode();
      String requestId = resp.headers().firstValue("request-id").orElse(clientRequestId);

      if (status == 429 || status >= 500) {
        throw GraphException.transientFailure(status, requestId, "transient", resp.body());
      }
      if (status >= 400) {
        throw GraphException.permanentFailure(status, requestId, "http_" + status, resp.body());
      }
      return new GraphResponse(status, requestId, null, null);
    } catch (GraphException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Graph request failed: " + e.getMessage(), e);
    }
  }

  private GraphResponse delete(String baseUrl, String path) {
    String clientRequestId = UUID.randomUUID().toString();

    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
            .header("client-request-id", clientRequestId)
            .DELETE()
            .build();

    try {
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      int status = resp.statusCode();
      String requestId = resp.headers().firstValue("request-id").orElse(clientRequestId);

      if (status == 429 || status >= 500) {
        throw GraphException.transientFailure(status, requestId, "transient", resp.body());
      }
      if (status >= 400) {
        throw GraphException.permanentFailure(status, requestId, "http_" + status, resp.body());
      }
      return new GraphResponse(status, requestId, null, null);
    } catch (GraphException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Graph DELETE failed: " + e.getMessage(), e);
    }
  }
}

