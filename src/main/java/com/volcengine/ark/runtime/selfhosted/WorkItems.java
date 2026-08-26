// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import com.volcengine.ark.runtime.models.environment.WorkData;
import com.volcengine.ark.runtime.models.environment.WorkItem;

final class WorkItems {
    private WorkItems() {
    }

    static String sessionId(WorkItem item) {
        if (item == null) {
            return "";
        }
        WorkData data = item.getData();
        if (data == null || data.getId() == null || data.getId().isEmpty()) {
            return "";
        }
        String type = data.getType();
        return type == null || type.isEmpty() || "session".equals(type) ? data.getId() : "";
    }

    static String latestHeartbeat(WorkItem item) {
        if (item == null || item.getLatestHeartbeatAt() == null) {
            return "";
        }
        return item.getLatestHeartbeatAt();
    }
}
