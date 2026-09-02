// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

/**
 * Tool execution contract.
 *
 * <p>The runner enforces {@link ToolContext#getToolTimeoutMillis()}. Custom tools should also
 * poll {@link ToolContext#isCancelled()} so timed-out work releases resources promptly.
 */
public interface Tool {
    String name();

    ToolResult execute(Object input, ToolContext context);
}
