package com.mkylm.intunebulk.graph;

/** Authentication abstraction used by GraphClient to obtain bearer tokens. */
public interface TokenProvider {
  String getAccessToken();
}

