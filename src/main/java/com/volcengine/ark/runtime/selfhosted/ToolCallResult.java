// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

public class ToolCallResult {
    private final String toolUseId;
    private final String name;
    private final boolean custom;
    private final String confirmation;
    private final boolean posted;
    private final Event event;
    private final Event result;

    public ToolCallResult(String toolUseId, String name, boolean custom, String confirmation, boolean posted, Event event, Event result) {
        this.toolUseId = toolUseId;
        this.name = name;
        this.custom = custom;
        this.confirmation = confirmation == null ? "" : confirmation;
        this.posted = posted;
        this.event = event;
        this.result = result;
    }

    public String getToolUseId() {
        return toolUseId;
    }

    public String getName() {
        return name;
    }

    public boolean isCustom() {
        return custom;
    }

    public String getConfirmation() {
        return confirmation;
    }

    public boolean isPosted() {
        return posted;
    }

    public Event getEvent() {
        return event;
    }

    public Event getResult() {
        return result;
    }
}
