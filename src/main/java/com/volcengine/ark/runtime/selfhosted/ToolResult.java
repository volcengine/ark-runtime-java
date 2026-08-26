// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ToolResult {
    private final List<ContentBlock> content;
    private final boolean error;

    public ToolResult(List<ContentBlock> content, boolean error) {
        this.content = content == null ? new ArrayList<ContentBlock>() : content;
        this.error = error;
    }

    public static ToolResult text(String text) {
        return new ToolResult(Collections.singletonList(new ContentBlock("text", text)), false);
    }

    public static ToolResult error(String text) {
        return new ToolResult(Collections.singletonList(new ContentBlock("text", text)), true);
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    public boolean isError() {
        return error;
    }
}
