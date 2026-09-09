// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted.mcp;

import java.util.Map;

/** Protocol-independent MCP tool definition. */
public class McpToolDefinition {
    private final String name;
    private final String description;
    private final Map<String, Object> inputSchema;

    public McpToolDefinition(
            String name,
            String description,
            Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }
}
