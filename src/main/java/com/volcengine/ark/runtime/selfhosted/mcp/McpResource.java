// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted.mcp;

/** Protocol-independent MCP embedded resource. */
public class McpResource {
    private final String uri;
    private final String mimeType;
    private final String text;
    private final String blob;

    public McpResource(String uri, String mimeType, String text, String blob) {
        this.uri = uri;
        this.mimeType = mimeType;
        this.text = text;
        this.blob = blob;
    }

    public String getUri() {
        return uri;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getText() {
        return text;
    }

    public String getBlob() {
        return blob;
    }
}
