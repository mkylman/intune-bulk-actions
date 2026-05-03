package com.mkylm.intunebulk.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Uses the current Azure CLI login session to fetch a Microsoft Graph access token.
 *
 * <p>Prereqs:
 * <ul>
 *   <li>{@code az} installed
 *   <li>{@code az login} already completed (browser-based)
 * </ul>
 */
final class AzureCliTokenProvider implements TokenProvider {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String getAccessToken() {
    try {
      // Azure CLI returns JSON: { "accessToken": "...", "expiresOn": "...", ... }
      Process p =
          new ProcessBuilder(
                  "az",
                  "account",
                  "get-access-token",
                  "--resource-type",
                  "ms-graph",
                  "--output",
                  "json")
              .redirectErrorStream(true)
              .start();

      String output;
      try (BufferedReader r =
          new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
        output = r.lines().collect(Collectors.joining("\n"));
      }
      int code = p.waitFor();
      if (code != 0) {
        throw new RuntimeException(
            "Azure CLI token acquisition failed (exit " + code + "). Output:\n" + output);
      }

      JsonNode json = MAPPER.readTree(output);
      JsonNode token = json.get("accessToken");
      if (token == null || !token.isTextual() || token.asText().isBlank()) {
        throw new RuntimeException("Azure CLI returned no accessToken. Output:\n" + output);
      }
      return token.asText();
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to get Graph token from Azure CLI. Ensure `az` is installed and run `az login`.\n"
              + "Underlying error: "
              + e.getMessage(),
          e);
    }
  }
}

