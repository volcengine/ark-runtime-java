// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.volcengine.ark.runtime.models.file.FileListResponse;
import com.volcengine.ark.runtime.models.session.ListSessionResourcesResponse;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

/** Adapts legacy null list pages before Jackson and RxJava see the response. */
final class ListResponseConverterFactory extends Converter.Factory {
    private final ObjectMapper mapper;

    ListResponseConverterFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        if (type != FileListResponse.class && type != ListSessionResourcesResponse.class) {
            return null;
        }
        Converter<ResponseBody, ?> delegate = retrofit.nextResponseBodyConverter(this, type, annotations);
        return body -> {
            try (ResponseBody original = body) {
                String normalized = normalize(original.string(), type == FileListResponse.class);
                return delegate.convert(ResponseBody.create(original.contentType(), normalized));
            }
        };
    }

    private String normalize(String raw, boolean files) throws IOException {
        try (JsonParser parser = mapper.getFactory().createParser(raw)) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.VALUE_NULL) {
                return parser.nextToken() == null ? emptyPage(files) : raw;
            }
            StringBuilder normalized = new StringBuilder(raw.length());
            int depth = 0;
            int copied = 0;
            while (token != null) {
                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    depth++;
                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    depth--;
                } else if (depth == 1 && token == JsonToken.VALUE_NULL && "data".equals(parser.currentName())) {
                    int start = Math.toIntExact(parser.getTokenLocation().getCharOffset());
                    normalized.append(raw, copied, start).append("[]");
                    copied = start + 4;
                }
                token = parser.nextToken();
            }
            return copied == 0 ? raw : normalized.append(raw, copied, raw.length()).toString();
        }
    }

    private String emptyPage(boolean files) throws IOException {
        ObjectNode page = mapper.createObjectNode();
        page.putArray("data");
        if (files) {
            page.put("object", "file");
            page.put("first_id", "");
            page.put("last_id", "");
            page.put("has_more", false);
        }
        return mapper.writeValueAsString(page);
    }
}
