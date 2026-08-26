// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Event {
    private String id = "";
    private String type = "";
    private String name = "";
    private Object input;
    private String processedAt = "";
    private String evaluatedPermission = "";
    private String sessionThreadId = "";
    private String toolUseId = "";
    private String customToolUseId = "";
    private String result = "";
    private String denyMessage = "";
    private Object stopReason;
    private List<ContentBlock> content = new ArrayList<>();
    private Boolean isError;
    private Map<String, Object> extra = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public static Event fromMap(Map<String, Object> raw) {
        Event event = new Event();
        if (raw == null) {
            return event;
        }
        event.id = stringValue(raw.get("id"));
        event.type = stringValue(raw.get("type"));
        event.name = stringValue(raw.get("name"));
        event.input = raw.get("input");
        event.processedAt = stringValue(raw.get("processed_at"));
        event.evaluatedPermission = stringValue(raw.get("evaluated_permission"));
        event.sessionThreadId = stringValue(raw.get("session_thread_id"));
        event.toolUseId = stringValue(raw.get("tool_use_id"));
        event.customToolUseId = stringValue(raw.get("custom_tool_use_id"));
        event.result = stringValue(raw.get("result"));
        event.denyMessage = stringValue(raw.get("deny_message"));
        event.stopReason = raw.get("stop_reason");
        if (raw.get("is_error") instanceof Boolean) {
            event.isError = (Boolean) raw.get("is_error");
        }
        Object blocks = raw.get("content");
        if (blocks instanceof List) {
            for (Object block : (List<Object>) blocks) {
                if (block instanceof Map) {
                    Map<String, Object> blockMap = (Map<String, Object>) block;
                    ContentBlock contentBlock = new ContentBlock();
                    contentBlock.setType(stringValue(blockMap.get("type")));
                    contentBlock.setText(stringValue(blockMap.get("text")));
                    contentBlock.setMediaType(stringValue(blockMap.get("media_type")));
                    contentBlock.setData(blockMap.get("data"));
                    event.content.add(contentBlock);
                }
            }
        }
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!knownField(entry.getKey())) {
                event.extra.put(entry.getKey(), entry.getValue());
            }
        }
        return event;
    }

    public static Event newUserToolResultEvent(
            String toolUseId, List<ContentBlock> content, boolean isError, String sessionThreadId) {
        Event event = new Event();
        event.id = newEventId("evt");
        event.type = SelfHostedConstants.EVENT_TYPE_USER_TOOL_RESULT;
        event.toolUseId = toolUseId;
        event.content = content == null ? new ArrayList<ContentBlock>() : content;
        event.isError = isError;
        event.processedAt = Instant.now().toString();
        event.sessionThreadId = sessionThreadId == null ? "" : sessionThreadId;
        return event;
    }

    public static Event newUserCustomToolResultEvent(
            String customToolUseId, List<ContentBlock> content, boolean isError, String sessionThreadId) {
        Event event = new Event();
        event.id = newEventId("evt");
        event.type = SelfHostedConstants.EVENT_TYPE_USER_CUSTOM_TOOL_RESULT;
        event.customToolUseId = customToolUseId;
        event.content = content == null ? new ArrayList<ContentBlock>() : content;
        event.isError = isError;
        event.processedAt = Instant.now().toString();
        event.sessionThreadId = sessionThreadId == null ? "" : sessionThreadId;
        return event;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>(extra);
        put(out, "id", id);
        put(out, "type", type);
        put(out, "name", name);
        if (input != null) {
            out.put("input", input);
        }
        put(out, "processed_at", processedAt);
        put(out, "evaluated_permission", evaluatedPermission);
        put(out, "session_thread_id", sessionThreadId);
        put(out, "tool_use_id", toolUseId);
        put(out, "custom_tool_use_id", customToolUseId);
        put(out, "result", result);
        put(out, "deny_message", denyMessage);
        if (stopReason != null) {
            out.put("stop_reason", stopReason);
        }
        if (content != null && !content.isEmpty()) {
            List<Map<String, Object>> blocks = new ArrayList<>();
            for (ContentBlock block : content) {
                blocks.add(block.toMap());
            }
            out.put("content", blocks);
        }
        if (isError != null) {
            out.put("is_error", isError);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public String stopReasonType() {
        if (stopReason instanceof Map) {
            return stringValue(((Map<String, Object>) stopReason).get("type"));
        }
        return stopReason instanceof String ? (String) stopReason : "";
    }

    public String callId() {
        if (toolUseId != null && !toolUseId.isEmpty()) {
            return toolUseId;
        }
        if (customToolUseId != null && !customToolUseId.isEmpty()) {
            return customToolUseId;
        }
        return id == null ? "" : id;
    }

    public String resultCallId() {
        if (toolUseId != null && !toolUseId.isEmpty()) {
            return toolUseId;
        }
        return customToolUseId == null ? "" : customToolUseId;
    }

    public static String newEventId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static void put(Map<String, Object> out, String key, String value) {
        if (value != null && !value.isEmpty()) {
            out.put(key, value);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean knownField(String key) {
        return "id".equals(key)
                || "type".equals(key)
                || "name".equals(key)
                || "input".equals(key)
                || "processed_at".equals(key)
                || "evaluated_permission".equals(key)
                || "session_thread_id".equals(key)
                || "tool_use_id".equals(key)
                || "custom_tool_use_id".equals(key)
                || "result".equals(key)
                || "deny_message".equals(key)
                || "stop_reason".equals(key)
                || "content".equals(key)
                || "is_error".equals(key);
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Object getInput() {
        return input;
    }

    public String getEvaluatedPermission() {
        return evaluatedPermission;
    }

    public String getSessionThreadId() {
        return sessionThreadId;
    }

    public String getResult() {
        return result;
    }

    public List<ContentBlock> getContent() {
        return content;
    }
}
