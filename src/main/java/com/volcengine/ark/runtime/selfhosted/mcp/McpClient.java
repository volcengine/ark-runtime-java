// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted.mcp;

import com.volcengine.ark.runtime.selfhosted.ToolContext;
import java.util.Map;

/** Minimal MCP client contract required by a self-hosted worker. */
public interface McpClient {
    McpCallToolResult callTool(
            String name,
            Map<String, Object> arguments,
            ToolContext context) throws Exception;
}
