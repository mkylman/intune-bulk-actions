package com.mkylm.intunebulk.core;

import java.util.List;

/** Immutable payload sent into DeviceActionService for one bulk operation run. */
public record ActionRequest(ActionType action, List<DeviceRef> targets, ActionOptions options) {}

