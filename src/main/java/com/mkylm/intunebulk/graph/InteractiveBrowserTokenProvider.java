package com.mkylm.intunebulk.graph;

import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.InteractiveRequestParameters;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.Prompt;
import com.microsoft.aad.msal4j.SilentParameters;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Opens the system browser to authenticate (Microsoft login page).
 *
 * <p>This is the closest "az login"-like experience without requiring Azure CLI.
 * Reuses tokens silently when possible to avoid repeated browser prompts.
 */
final class InteractiveBrowserTokenProvider implements TokenProvider {
  private static final long TOKEN_REFRESH_SKEW_SECONDS = 120;
  private final PublicClientApplication app;
  private final Set<String> scopes;
  private final URI redirectUri;
  private IAuthenticationResult cachedResult;

  InteractiveBrowserTokenProvider(PublicClientApplication app, Set<String> scopes, URI redirectUri) {
    this.app = app;
    this.scopes = scopes;
    this.redirectUri = redirectUri;
  }

  @Override
  public synchronized String getAccessToken() {
    try {
      // Fast path: in-memory token still valid.
      if (isUsable(cachedResult)) {
        return cachedResult.accessToken();
      }

      // Try silent acquisition before prompting the browser again.
      IAuthenticationResult silentResult = tryAcquireSilently();
      if (isUsable(silentResult)) {
        cachedResult = silentResult;
        return silentResult.accessToken();
      }

      // Interactive fallback opens browser and waits for auth redirect completion.
      InteractiveRequestParameters p =
          InteractiveRequestParameters.builder(redirectUri)
              .scopes(scopes)
              .prompt(Prompt.SELECT_ACCOUNT)
              .build();
      IAuthenticationResult result = app.acquireToken(p).get();
      cachedResult = result;
      return result.accessToken();
    } catch (ExecutionException e) {
      throw new RuntimeException("MSAL interactive auth failed: " + e.getCause().getMessage(), e);
    } catch (Exception e) {
      throw new RuntimeException("MSAL interactive auth failed: " + e.getMessage(), e);
    }
  }

  private IAuthenticationResult tryAcquireSilently() {
    try {
      Collection<IAccount> accounts = app.getAccounts().get();
      if (accounts == null || accounts.isEmpty()) {
        return null;
      }

      IAccount account = accounts.iterator().next();
      SilentParameters silent = SilentParameters.builder(scopes, account).build();
      return app.acquireTokenSilently(silent).get();
    } catch (Exception e) {
      return null;
    }
  }

  private boolean isUsable(IAuthenticationResult result) {
    if (result == null || result.accessToken() == null || result.accessToken().isBlank()) {
      return false;
    }
    if (result.expiresOnDate() == null) {
      return true;
    }
    return result.expiresOnDate().toInstant().isAfter(Instant.now().plusSeconds(TOKEN_REFRESH_SKEW_SECONDS));
  }
}

