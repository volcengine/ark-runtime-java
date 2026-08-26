// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.util.Map;

public class WorkData {
    private String type = "";
    private String id = "";
    private String sessionId = "";

    public static WorkData fromMap(Map<String, Object> raw) {
        WorkData data = new WorkData();
        if (raw == null) {
            return data;
        }
        data.type = stringValue(raw.get("type"));
        data.id = stringValue(raw.get("id"));
        data.sessionId = stringValue(raw.get("session_id"));
        return data;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
