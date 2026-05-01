package com.mkylm.intunebulk.graph;

/** Token provider that returns a pre-supplied token (INTUNE_ACCESS_TOKEN). */
final class RawAccessTokenProvider implements TokenProvider {
  private final String token;

  RawAccessTokenProvider(String token) {
    this.token = token;
  }

  @Override
  public String getAccessToken() {
    return token;
  }
}

