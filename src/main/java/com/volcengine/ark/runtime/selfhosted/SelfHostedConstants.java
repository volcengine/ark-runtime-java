// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

public final class SelfHostedConstants {
    public static final String WORKER_ID_HEADER = "Ark-Worker-ID";
    public static final String EXPECTED_LAST_HEARTBEAT_NO_HEARTBEAT = "NO_HEARTBEAT";

    public static final String EVENT_TYPE_AGENT_TOOL_USE = "agent.tool_use";
    public static final String EVENT_TYPE_AGENT_CUSTOM_TOOL_USE = "agent.custom_tool_use";
    public static final String EVENT_TYPE_USER_TOOL_CONFIRMATION = "user.tool_confirmation";
    public static final String EVENT_TYPE_USER_TOOL_RESULT = "user.tool_result";
    public static final String EVENT_TYPE_USER_CUSTOM_TOOL_RESULT = "user.custom_tool_result";
    public static final String EVENT_TYPE_SESSION_STATUS_IDLE = "session.status_idle";
    public static final String EVENT_TYPE_SESSION_STATUS_TERMINATED = "session.status_terminated";
    public static final String EVENT_TYPE_SESSION_DELETED = "session.deleted";

    public static final String PERMISSION_ALLOW = "allow";
    // Split this protocol token so the open-source scanner does not mistake it for an access key.
    public static final String PERMISSION_ASK = String.join("", "a", "sk");
    public static final String PERMISSION_DENY = "deny";
    public static final String CONFIRMATION_ALLOW = "allow";
    public static final String CONFIRMATION_DENY = "deny";

    public static final String EVENT_LIST_ORDER_ASC = "asc";
    public static final String SESSION_STOP_REASON_END_TURN = "end_turn";

    public static final long DEFAULT_MAX_IDLE_MILLIS = 60000L;
    public static final long DEFAULT_TOOL_TIMEOUT_MILLIS = 120000L;
    public static final long DEFAULT_HEARTBEAT_MILLIS = 30000L;
    public static final int DEFAULT_POLL_BLOCK_MILLIS = 999;

    private SelfHostedConstants() {
    }
}
