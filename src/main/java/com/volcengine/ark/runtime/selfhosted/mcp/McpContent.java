// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted.mcp;

/** Protocol-independent MCP content block. */
public class McpContent {
    private final String type;
    private final String text;
    private final String mimeType;
    private final String data;
    private final McpResource resource;

    public McpContent(
            String type,
            String text,
            String mimeType,
            String data,
            McpResource resource) {
        this.type = type;
        this.text = text;
        this.mimeType = mimeType;
        this.data = data;
        this.resource = resource;
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getData() {
        return data;
    }

    public McpResource getResource() {
        return resource;
    }
}
