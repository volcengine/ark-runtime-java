// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.util.Map;

public class WorkItem {
    private String id = "";
    private String environmentId = "";
    private WorkData data = new WorkData();
    private String latestHeartbeatAt = "";
    private String sessionId = "";
    private String state = "";
    private String lastHeartbeat = "";

    @SuppressWarnings("unchecked")
    public static WorkItem fromMap(Map<String, Object> raw) {
        WorkItem item = new WorkItem();
        if (raw == null) {
            return item;
        }
        item.id = stringValue(raw.get("id"));
        item.environmentId = stringValue(raw.get("environment_id"));
        item.latestHeartbeatAt = stringValue(raw.get("latest_heartbeat_at"));
        item.sessionId = stringValue(raw.get("session_id"));
        item.state = stringValue(raw.get("state"));
        item.lastHeartbeat = stringValue(raw.get("last_heartbeat"));
        if (raw.get("data") instanceof Map) {
            item.data = WorkData.fromMap((Map<String, Object>) raw.get("data"));
        }
        return item;
    }

    public String sessionIdValue() {
        if (sessionId != null && !sessionId.isEmpty()) {
            return sessionId;
        }
        if (data.getSessionId() != null && !data.getSessionId().isEmpty()) {
            return data.getSessionId();
        }
        if (data.getId() != null && !data.getId().isEmpty()
                && (data.getType() == null || data.getType().isEmpty() || "session".equals(data.getType()))) {
            return data.getId();
        }
        return "";
    }

    public String latestHeartbeatValue() {
        return latestHeartbeatAt != null && !latestHeartbeatAt.isEmpty() ? latestHeartbeatAt : lastHeartbeat;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }

    public WorkData getData() {
        return data;
    }

    public void setData(WorkData data) {
        this.data = data;
    }

    public String getLatestHeartbeatAt() {
        return latestHeartbeatAt;
    }

    public void setLatestHeartbeatAt(String latestHeartbeatAt) {
        this.latestHeartbeatAt = latestHeartbeatAt;
    }

    public String getState() {
        return state;
    }
}
