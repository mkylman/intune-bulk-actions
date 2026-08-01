package com.mkylm.intunebulk.graph;

import com.microsoft.aad.msal4j.PublicClientApplication;
import java.net.MalformedURLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scaffold: reads env vars and returns a TokenProvider.
 *
 * <p>Planned env vars:
 * <ul>
 *   <li>INTUNE_TENANT_ID
 *   <li>INTUNE_CLIENT_ID
 *   <li>INTUNE_AUTH_MODE = interactive | device_code | raw_token
 *   <li>INTUNE_REDIRECT_URI (interactive only; must be registered on the app)
 *   <li>INTUNE_ACCESS_TOKEN (when auth mode is raw_token)
 *   <li>INTUNE_SCOPES (comma-separated delegated scopes; default: common read scopes)
 *   <li>(future) INTUNE_CLIENT_SECRET or INTUNE_CERT_PATH
 * </ul>
 */
public final class TokenProviderFactory {
  private static final SecureTokenStore FALLBACK_TOKEN_STORE =
      new SecureTokenStore() {
        @Override
        public java.util.Optional<String> loadSerializedCache() {
          return java.util.Optional.empty();
        }

        @Override
        public void saveSerializedCache(String serializedCache) {
          // no-op fallback
        }

        @Override
        public void clear() {
          // no-op fallback
        }
      };

  public static TokenProvider fromEnvironment() {
    // Entry point for auth selection used by all commands.
    // Default is interactive browser flow.
    String mode = Env.optional("INTUNE_AUTH_MODE", "interactive").toLowerCase(Locale.ROOT);
    return switch (mode) {
      case "raw_token" -> new RawAccessTokenProvider(Env.required("INTUNE_ACCESS_TOKEN"));
      case "interactive" -> interactive();
      case "device_code" -> deviceCode();
      default ->
          throw new IllegalArgumentException(
              "Unsupported INTUNE_AUTH_MODE: " + mode + " (expected interactive|device_code|raw_token)");
    };
  }

  private static TokenProvider interactive() {
    // Browser redirect-based delegated login flow.
    String tenantId = Env.optional("INTUNE_TENANT_ID", "organizations");
    String clientId = Env.optional("INTUNE_CLIENT_ID", null);
    if (clientId == null) {
      throw new IllegalStateException(
          "INTUNE_AUTH_MODE=interactive requires INTUNE_CLIENT_ID (app registration). "
              + "Interactive auth needs a registered redirect URI.");
    }
    String redirect = Env.optional("INTUNE_REDIRECT_URI", "http://localhost");

    Set<String> scopes = defaultScopes();
    String authority = "https://login.microsoftonline.com/" + tenantId + "/";

    try {
      PublicClientApplication app = createPublicClientApplication(clientId, authority);
      return new InteractiveBrowserTokenProvider(app, scopes, java.net.URI.create(redirect));
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Invalid authority URL: " + authority, e);
    }
  }

  private static TokenProvider deviceCode() {
    // Device code flow for terminal-first UX where redirect URIs are inconvenient.
    // Uses a well-known public client ID by default unless overridden.
    String tenantId = Env.optional("INTUNE_TENANT_ID", "organizations");
    String clientId = Env.optional("INTUNE_CLIENT_ID", "04b07795-8ddb-461a-bbee-02f9e1bf7b46");

    Set<String> scopes = defaultScopes();

    String authority = "https://login.microsoftonline.com/" + tenantId + "/";

    try {
      PublicClientApplication app = createPublicClientApplication(clientId, authority);
      return new DeviceCodeTokenProvider(
          app,
          scopes,
          code -> {
            System.out.println(code.message());
          });
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Invalid authority URL: " + authority, e);
    }
  }

  private static Set<String> defaultScopes() {
    // Baseline delegated scopes needed for the current MVP behavior.
    List<String> defaults =
        List.of(
            "Group.Read.All",
            "DeviceManagementManagedDevices.Read.All",
            "DeviceManagementManagedDevices.ReadWrite.All",
            "DeviceManagementManagedDevices.PrivilegedOperations.All");
    return new LinkedHashSet<>(Env.csvList("INTUNE_SCOPES", defaults));
  }

  private static PublicClientApplication createPublicClientApplication(String clientId, String authority)
      throws MalformedURLException {
    SecureTokenStore tokenStore = createSecureTokenStore();
    MsalTokenCacheAccessAspect aspect = new MsalTokenCacheAccessAspect(tokenStore);
    return PublicClientApplication.builder(clientId)
        .authority(authority)
        .setTokenCacheAccessAspect(aspect)
        .build();
  }

  private static SecureTokenStore createSecureTokenStore() {
    try {
      return new WindowsSecureTokenStore();
    } catch (Exception ex) {
      return FALLBACK_TOKEN_STORE;
    }
  }

  private TokenProviderFactory() {}
}

