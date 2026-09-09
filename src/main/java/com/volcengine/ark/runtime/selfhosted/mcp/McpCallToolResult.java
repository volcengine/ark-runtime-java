// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted.mcp;

import java.util.ArrayList;
import java.util.List;

/** Protocol-independent MCP tool result. */
public class McpCallToolResult {
    private final List<McpContent> content;
    private final Object structuredContent;
    private final boolean error;

    public McpCallToolResult(
            List<McpContent> content,
            Object structuredContent,
            boolean error) {
        this.content = content == null ? new ArrayList<McpContent>() : content;
        this.structuredContent = structuredContent;
        this.error = error;
    }

    public List<McpContent> getContent() {
        return content;
    }

    public Object getStructuredContent() {
        return structuredContent;
    }

    public boolean isError() {
        return error;
    }
}
