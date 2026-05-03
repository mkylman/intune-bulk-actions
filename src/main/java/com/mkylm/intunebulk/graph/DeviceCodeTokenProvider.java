package com.mkylm.intunebulk.graph;

import com.microsoft.aad.msal4j.DeviceCode;
import com.microsoft.aad.msal4j.DeviceCodeFlowParameters;
import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Device-code token provider with in-process token reuse.
 *
 * <p>Flow:
 * <ol>
 *   <li>use in-memory cached token if still valid
 *   <li>try silent refresh from MSAL account cache
 *   <li>fall back to interactive device-code prompt
 * </ol>
 */
final class DeviceCodeTokenProvider implements TokenProvider {
  private static final long TOKEN_REFRESH_SKEW_SECONDS = 120;
  private final PublicClientApplication app;
  private final Set<String> scopes;
  private final Consumer<DeviceCode> deviceCodeConsumer;
  private IAuthenticationResult cachedResult;

  DeviceCodeTokenProvider(
      PublicClientApplication app, Set<String> scopes, Consumer<DeviceCode> deviceCodeConsumer) {
    this.app = app;
    this.scopes = scopes;
    this.deviceCodeConsumer = deviceCodeConsumer;
  }

  @Override
  public synchronized String getAccessToken() {
    try {
      // Fast path: valid token already in memory.
      if (isUsable(cachedResult)) {
        return cachedResult.accessToken();
      }

      // Next best: silent token refresh via MSAL cache/account context.
      IAuthenticationResult silentResult = tryAcquireSilently();
      if (isUsable(silentResult)) {
        cachedResult = silentResult;
        return silentResult.accessToken();
      }

      // Fallback: request user interaction through device-code flow.
      DeviceCodeFlowParameters p =
          DeviceCodeFlowParameters.builder(scopes, deviceCodeConsumer).build();
      IAuthenticationResult result = app.acquireToken(p).get();
      cachedResult = result;
      return result.accessToken();
    } catch (ExecutionException e) {
      throw new RuntimeException("MSAL device-code auth failed: " + e.getCause().getMessage(), e);
    } catch (Exception e) {
      throw new RuntimeException("MSAL device-code auth failed: " + e.getMessage(), e);
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

