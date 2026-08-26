// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public class SkillContent implements Closeable {
    private final InputStream body;
    private final long contentLength;
    private final String fileName;
    private final String contentType;

    public SkillContent(InputStream body, long contentLength, String fileName, String contentType) {
        this.body = body;
        this.contentLength = contentLength;
        this.fileName = fileName == null ? "" : fileName;
        this.contentType = contentType == null ? "" : contentType;
    }

    public InputStream getBody() {
        return body;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    @Override
    public void close() throws IOException {
        if (body != null) {
            body.close();
        }
    }
}
