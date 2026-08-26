// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

public interface Tool {
    String name();

    ToolResult execute(Object input, ToolContext context);
}
