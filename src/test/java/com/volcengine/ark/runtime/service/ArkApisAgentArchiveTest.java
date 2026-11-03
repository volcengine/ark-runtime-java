// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.service;

import static org.junit.Assert.*;

import com.volcengine.ark.runtime.models.agent.Agent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class ArkApisAgentArchiveTest {
    @Test
    public void archiveAndListUseRealRetrofitTransport() throws Exception {
        List<Request> requests = new ArrayList<>();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            requests.add(request);
            String json = request.method().equals("POST")
                ? "{\"id\": \"resource/1\", \"type\": \"agent\", \"archived_at\": \"2026-10-29T00:00:00Z\", \"created_at\": \"2026-10-29T00:00:00Z\", \"updated_at\": \"2026-10-29T00:00:00Z\", \"name\": \"archive test\", \"version\": 1}"
                : "{\"data\": [], \"total\": 0}";
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), json)).build();
        }).build();
        ArkApi api = new Retrofit.Builder().baseUrl("https://example.test/").client(client)
            .addConverterFactory(JacksonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create()).build().create(ArkApi.class);
        ArkService service = new ArkService(api);
        Agent result = service.archiveAgent("resource/1");
        assertEquals("resource/1", result.getId());
        assertEquals("2026-10-29T00:00:00Z", result.getArchivedAt());
        Request request = requests.get(0);
        assertEquals("POST", request.method());
        assertEquals("/api/v3/agents/resource%2F1/archive", request.url().encodedPath());
        Buffer body = new Buffer();
        if (request.body() != null) request.body().writeTo(body);
        assertEquals(0, body.size());
        service.listAgents(null, null, null, null);
        assertNull(requests.get(requests.size() - 1).url().queryParameter("include_archived"));
        for (Boolean include : new Boolean[] { null, false, true }) {
            service.listAgents(null, null, null, null, include);
            request = requests.get(requests.size() - 1);
            assertEquals("GET", request.method());
            assertEquals(include == null ? null : include.toString(), request.url().queryParameter("include_archived"));
        }
        // Both old interface/service entry points remain source-compatible.
        ArkApi.class.getMethod("listAgents", Integer.class, String.class, String.class, String.class, Map.class);
        ArkService.class.getMethod("listAgents", Integer.class, String.class, String.class, String.class);
        for (String id : new String[] { null, "" }) {
            try { service.archiveAgent(id); fail("missing ID accepted"); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("agent_id")); }
        }
    }
}
