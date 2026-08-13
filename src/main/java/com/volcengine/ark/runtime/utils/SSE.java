// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A single Server-Sent Event frame delivered by {@link ResponseBodyCallback}.
 *
 * <p>{@code data} is the raw payload string (SSE {@code data:} line, joined
 * across multi-line frames). {@code type} is the resolved event type: it
 * prefers the SSE {@code event:} line when the server emits one, otherwise
 * peeks the JSON payload's top-level {@code type} field so callers of
 * endpoints like {@code /sessions/{id}/events/stream} — which emit
 * data-only frames — can dispatch on {@link #getType()} without re-parsing
 * {@link #getData()} themselves. Empty string when neither source yields a
 * type (non-JSON payloads such as {@code [DONE]}, or JSON without a
 * {@code type} field).
 */
public class SSE {
    private static final String DONE_DATA = "[DONE]";
    private static final ObjectMapper TYPE_PEEK_MAPPER = new ObjectMapper();

    private final String data;
    private final String type;

    public SSE(String data) {
        this(data, resolveType("", data));
    }

    public SSE(String data, String type) {
        this.data = data;
        this.type = type == null ? "" : type;
    }

    public String getData() {
        return this.data;
    }

    /**
     * Effective event type. Never null; empty string when neither the SSE
     * {@code event:} line nor the JSON payload's {@code type} field is
     * available.
     */
    public String getType() {
        return this.type;
    }

    public byte[] toBytes() {
        return String.format("data: %s\n\n", this.data).getBytes();
    }

    public boolean isDone() {
        return DONE_DATA.equalsIgnoreCase(this.data);
    }

    /**
     * Resolve the effective event type for a SSE frame. Explicit
     * {@code event:} lines win; otherwise peek {@code type} out of the
     * JSON payload. Non-JSON payloads (e.g. {@code [DONE]}) or JSON
     * without a {@code type} field return an empty string.
     */
    public static String resolveType(String eventLine, String data) {
        if (eventLine != null && !eventLine.isEmpty()) {
            return eventLine;
        }
        if (data == null || data.isEmpty() || data.charAt(0) != '{') {
            return "";
        }
        try {
            JsonNode node = TYPE_PEEK_MAPPER.readTree(data);
            if (node == null || !node.isObject()) {
                return "";
            }
            JsonNode typeNode = node.get("type");
            if (typeNode == null || !typeNode.isTextual()) {
                return "";
            }
            return typeNode.asText("");
        } catch (Exception ignored) {
            return "";
        }
    }
}
