// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.util.LinkedHashMap;
import java.util.Map;

public class ContentBlock {
    private String type;
    private String text;
    private String mediaType;
    private Object data;
    private Object source;
    private String title;
    private String context;

    public ContentBlock() {
    }

    public ContentBlock(String type, String text) {
        this.type = type;
        this.text = text;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        if ("text".equals(type) || (text != null && !text.isEmpty())) {
            out.put("text", text == null ? "" : text);
        }
        if (mediaType != null && !mediaType.isEmpty()) {
            out.put("media_type", mediaType);
        }
        if (data != null) {
            out.put("data", data);
        }
        if (source != null) {
            out.put("source", source);
        }
        if (title != null && !title.isEmpty()) {
            out.put("title", title);
        }
        if (context != null && !context.isEmpty()) {
            out.put("context", context);
        }
        return out;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Object getSource() {
        return source;
    }

    public void setSource(Object source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
