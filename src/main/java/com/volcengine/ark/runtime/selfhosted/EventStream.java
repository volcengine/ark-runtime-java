// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.Const;
import com.volcengine.ark.runtime.service.ArkService;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class EventStream implements Closeable {
    private static final ObjectMapper MAPPER = ArkService.defaultObjectMapper();

    private final Call<ResponseBody> call;
    private final ResponseBody body;
    private final BufferedReader reader;

    EventStream(Call<ResponseBody> call, Response<ResponseBody> response) throws IOException {
        this.call = call;
        if (!response.isSuccessful() || response.body() == null) {
            String message = response.errorBody() == null ? response.message() : response.errorBody().string();
            throw new WorkerAPIException(response.code(), message, header(response, Const.SERVER_REQUEST_HEADER));
        }
        this.body = response.body();
        this.reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8));
    }

    public Event next() throws IOException {
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (data.length() == 0) {
                    continue;
                }
                String payload = data.toString();
                if ("[DONE]".equals(payload)) {
                    return null;
                }
                Map<String, Object> raw = MAPPER.readValue(payload, new TypeReference<Map<String, Object>>() {
                });
                return Event.fromMap(raw);
            }
            if (line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring(5).trim());
            }
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        call.cancel();
        reader.close();
        body.close();
    }

    private static String header(Response<?> response, String name) {
        String value = response.headers().get(name);
        return value == null ? "" : value;
    }
}
