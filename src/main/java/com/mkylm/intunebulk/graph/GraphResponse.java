package com.mkylm.intunebulk.graph;

/** Minimal transport-level response returned by GraphClient action calls. */
public record GraphResponse(int status, String requestId, String errorCode, String errorMessage) {}

