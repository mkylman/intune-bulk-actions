package com.mkylm.intunebulk.graph;

import java.util.Optional;

/**
 * Abstraction for secure persistence of serialized token cache data.
 *
 * Implementations should use platform-protected storage (for example DPAPI, Keychain, or
 * Secret Service) and avoid writing plaintext token material to disk.
 */
public interface SecureTokenStore {
  /**
   * Loads previously persisted serialized token cache payload.
   *
   * @return serialized cache payload when present; empty when no cache exists
   */
  Optional<String> loadSerializedCache();

  /**
   * Persists the serialized token cache payload securely.
   *
   * @param serializedCache serialized token cache payload from MSAL
   */
  void saveSerializedCache(String serializedCache);

  /** Removes any persisted token cache material. */
  void clear();
}
