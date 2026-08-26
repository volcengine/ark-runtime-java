// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.util.Map;

public class HeartbeatResponse {
    private String lastHeartbeat = "";
    private Boolean leaseExtended;
    private String state = "";
    private int ttlSeconds;
    private String type = "";

    public static HeartbeatResponse fromMap(Map<String, Object> raw) {
        HeartbeatResponse response = new HeartbeatResponse();
        if (raw == null) {
            return response;
        }
        response.lastHeartbeat = stringValue(raw.get("last_heartbeat"));
        if (raw.get("lease_extended") instanceof Boolean) {
            response.leaseExtended = (Boolean) raw.get("lease_extended");
        }
        response.state = stringValue(raw.get("state"));
        response.ttlSeconds = intValue(raw.get("ttl_seconds"));
        response.type = stringValue(raw.get("type"));
        return response;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    public String getLastHeartbeat() {
        return lastHeartbeat;
    }

    public Boolean getLeaseExtended() {
        return leaseExtended;
    }

    public String getState() {
        return state;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public String getType() {
        return type;
    }
}
