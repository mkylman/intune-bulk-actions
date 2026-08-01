package com.mkylm.intunebulk.graph;

import com.microsoft.aad.msal4j.ITokenCacheAccessAspect;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;
import java.util.Optional;

/** Bridges MSAL token-cache callbacks to {@link SecureTokenStore}. */
final class MsalTokenCacheAccessAspect implements ITokenCacheAccessAspect {
  private final SecureTokenStore secureTokenStore;
  private final Object initLock = new Object();
  private volatile boolean cacheInitialized;

  MsalTokenCacheAccessAspect(SecureTokenStore secureTokenStore) {
    this.secureTokenStore = secureTokenStore;
  }

  @Override
  public void beforeCacheAccess(ITokenCacheAccessContext context) {
    if (cacheInitialized) {
      return;
    }
    synchronized (initLock) {
      if (cacheInitialized) {
        return;
      }
      try {
        Optional<String> serialized = secureTokenStore.loadSerializedCache();
        if (serialized.isPresent() && !serialized.get().isBlank()) {
          context.tokenCache().deserialize(serialized.get());
        }
      } catch (Exception ex) {
        // Fail open: token-store issues must not block authentication flow.
        System.err.println("[Auth] Warning: secure token cache load skipped: " + ex.getMessage());
      } finally {
        cacheInitialized = true;
      }
    }
  }

  @Override
  public void afterCacheAccess(ITokenCacheAccessContext context) {
    try {
      if (context.hasCacheChanged()) {
        String serialized = context.tokenCache().serialize();
        Thread persistThread =
            new Thread(
                () -> {
                  try {
                    secureTokenStore.saveSerializedCache(serialized);
                  } catch (Exception ex) {
                    // Fail open: token-store issues must not block authentication flow.
                    System.err.println(
                        "[Auth] Warning: secure token cache save skipped: " + ex.getMessage());
                  }
                },
                "msal-token-cache-save");
        persistThread.setDaemon(true);
        persistThread.start();
      }
    } catch (Exception ex) {
      // Fail open: token-store issues must not block authentication flow.
      System.err.println("[Auth] Warning: secure token cache save skipped: " + ex.getMessage());
    }
  }
}
