// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolSet {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolSet add(Tool tool) {
        if (tool == null || tool.name() == null || tool.name().isEmpty()) {
            throw new IllegalArgumentException("tool name must not be empty");
        }
        tools.put(tool.name(), tool);
        return this;
    }

    public boolean has(String name) {
        return tools.containsKey(name);
    }

    public ToolResult execute(String name, Object input, ToolContext context) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.error("tool " + name + " is not registered");
        }
        try {
            return tool.execute(input, context);
        } catch (Exception e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
