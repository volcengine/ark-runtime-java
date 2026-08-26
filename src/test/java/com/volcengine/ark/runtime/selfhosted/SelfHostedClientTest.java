// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.volcengine.ark.runtime.interceptor.RetryInterceptor;
import com.volcengine.ark.runtime.models.environment.WorkItem;
import com.volcengine.ark.runtime.models.environment.WorkState;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;

public class SelfHostedClientTest {
    @Test
    public void usesProductionBaseURLByDefault() {
        assertPollURL(null, "ark.cn-beijing.volces.com");
    }

    @Test
    public void acceptsBaseURLOverride() {
        assertPollURL("https://example.com/api/v3", "example.com");
    }

    @Test
    public void preservesNestedSessionWorkData() {
        String body = "{"
                + "\"id\":\"work-1\","
                + "\"environment_id\":\"env-1\","
                + "\"data\":{\"id\":\"session-1\",\"type\":\"session\"}"
                + "}";
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> response(chain.request(), new AtomicReference<>(), body))
                .build();
        SelfHostedClient client = new SelfHostedClient.Builder()
                .apiKey("test-api-key")
                .httpClient(httpClient)
                .build();

        WorkItem item = client.pollWork("env-1", "worker-1", 999, 0);

        assertNotNull(item);
        assertEquals("work-1", item.getId());
        assertEquals("env-1", item.getEnvironmentId());
        assertEquals("session-1", item.getData().getId());
        assertEquals("session-1", WorkItems.sessionId(item));
    }

    @Test
    public void opensSkillHubFromMetadataAndVersionedDownload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/skills/download/volcengine/ark/demo", exchange -> {
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("version=1.0.0", exchange.getRequestURI().getQuery());
            writeResponse(exchange, "application/zip", "skill-hub-zip");
        });
        server.createContext("/v1/skills", exchange -> {
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("skillIds=skill-1", exchange.getRequestURI().getQuery());
            writeResponse(
                    exchange,
                    "application/json",
                    "{\"Skills\":[{\"Id\":\"other-skill\",\"Slug\":\"wrong/slug\"},"
                            + "{\"Id\":\"skill-1\",\"Slug\":\"volcengine/ark/demo\"}],\"Total\":2}");
        });
        server.start();
        try {
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .header("Authorization", "Bearer test-api-key")
                            .build()))
                    .build();
            SelfHostedClient client = new SelfHostedClient.Builder()
                    .apiKey("test-api-key")
                    .httpClient(httpClient)
                    .skillHubBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/skills")
                    .build();
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("type", "skill_hub");
            raw.put("skill_id", "skill-1");
            raw.put("display_name", "demo");
            raw.put("version", "1.0.0");
            SkillRef skill = SkillRef.fromMap(raw);

            try (SkillContent content = client.openSkill("session-1", skill)) {
                byte[] data = readAll(content);
                assertEquals("skill-hub-zip", new String(data, StandardCharsets.UTF_8));
                assertEquals("demo", skill.nameValue());
                assertEquals("skill_hub", skill.getType());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void resolvesSkillFromControlPlaneMetadata() {
        AtomicReference<HttpUrl> requestedURL = new AtomicReference<>();
        String body = "{"
                + "\"id\":\"skill-1\","
                + "\"object\":\"skill\","
                + "\"created_at\":1786506774,"
                + "\"name\":\"canonical-skill-name\","
                + "\"latest_version\":\"1.0.0\""
                + "}";
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> response(chain.request(), requestedURL, body))
                .build();
        SelfHostedClient client = new SelfHostedClient.Builder()
                .apiKey("test-api-key")
                .baseUrl("https://ark.example.com/api/v3")
                .httpClient(httpClient)
                .build();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("skill_id", "skill-1");
        raw.put("type", "skill_hub");

        SkillRef resolved = client.resolveSkill(SkillRef.fromMap(raw));

        assertEquals("/api/v3/skills/skill-1", requestedURL.get().encodedPath());
        assertEquals("canonical-skill-name", resolved.getName());
        assertEquals("1.0.0", resolved.getVersion());
        assertEquals("skill_hub", resolved.getType());
    }

    @Test
    public void gracefulStopSendsEmptyJSONBody() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Buffer buffer = new Buffer();
                    chain.request().body().writeTo(buffer);
                    body.set(buffer.readUtf8());
                    return response(chain.request(), new AtomicReference<>());
                })
                .build();
        SelfHostedClient client = new SelfHostedClient.Builder()
                .apiKey("test-api-key")
                .httpClient(httpClient)
                .build();

        client.stopWork("env-1", "work-1", false);

        assertEquals("{}", body.get());
    }

    @Test
    public void heartbeatUsesOneLeaseBoundedAttempt() {
        AtomicInteger calls = new AtomicInteger();
        AtomicLong timeoutSeconds = new AtomicLong();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(3))
                .addInterceptor(chain -> {
                    calls.incrementAndGet();
                    timeoutSeconds.set(
                            TimeUnit.NANOSECONDS.toSeconds(chain.call().timeout().timeoutNanos()));
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(500)
                            .message("error")
                            .body(ResponseBody.create(MediaType.parse("application/json"), "temporary"))
                            .build();
                })
                .build();
        SelfHostedClient client = new SelfHostedClient.Builder()
                .apiKey("test-api-key")
                .httpClient(httpClient)
                .build();

        try {
            client.heartbeatWork("env-1", "work-1", "NO_HEARTBEAT", 30);
        } catch (WorkerAPIException expected) {
            assertEquals(500, expected.getStatusCode());
        }

        assertEquals(1, calls.get());
        assertEquals(15L, timeoutSeconds.get());
    }

    @Test
    public void atomicEnvironmentWorkAPIMatchesOpenAPIContract() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String work = "{"
                + "\"id\":\"work-1\","
                + "\"created_at\":\"2026-08-24T10:00:00Z\","
                + "\"data\":{\"id\":\"session-1\",\"type\":\"session\"},"
                + "\"environment_id\":\"env-1\","
                + "\"state\":\"active\","
                + "\"type\":\"work\""
                + "}";
        String heartbeat = "{"
                + "\"last_heartbeat\":\"2026-08-24T10:00:01Z\","
                + "\"lease_extended\":true,"
                + "\"state\":\"active\","
                + "\"ttl_seconds\":30,"
                + "\"type\":\"work_heartbeat\""
                + "}";
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    int call = calls.getAndIncrement();
                    if (call == 0) {
                        assertEquals("GET", request.method());
                        assertEquals("/api/v3/environments/env-1/work/poll", request.url().encodedPath());
                        assertEquals("999", request.url().queryParameter("block_ms"));
                        assertEquals("5000", request.url().queryParameter("reclaim_older_than_ms"));
                        assertEquals("worker-1", request.header("Ark-Worker-ID"));
                        return response(request, new AtomicReference<>(), work);
                    }
                    if (call == 1) {
                        assertEquals("POST", request.method());
                        assertEquals("/api/v3/environments/env-1/work/work-1/ack", request.url().encodedPath());
                        assertEquals("worker-1", request.header("Ark-Worker-ID"));
                        return response(request, new AtomicReference<>(), work);
                    }
                    if (call == 2) {
                        assertEquals("POST", request.method());
                        assertEquals(
                                "/api/v3/environments/env-1/work/work-1/heartbeat",
                                request.url().encodedPath());
                        assertEquals("NO_HEARTBEAT", request.url().queryParameter("expected_last_heartbeat"));
                        assertEquals("30", request.url().queryParameter("desired_ttl_seconds"));
                        return response(request, new AtomicReference<>(), heartbeat);
                    }
                    assertEquals("POST", request.method());
                    assertEquals("/api/v3/environments/env-1/work/work-1/stop", request.url().encodedPath());
                    Buffer buffer = new Buffer();
                    request.body().writeTo(buffer);
                    assertEquals("{}", buffer.readUtf8());
                    return response(request, new AtomicReference<>(), work);
                })
                .build();
        SelfHostedClient client = new SelfHostedClient.Builder()
                .apiKey("test-api-key")
                .baseUrl("https://ark.example.com/api/v3")
                .httpClient(httpClient)
                .build();

        WorkItem item = client.pollWork("env-1", "worker-1", 999, 5000);
        assertEquals("work-1", item.getId());
        assertEquals("2026-08-24T10:00:00Z", item.getCreatedAt());
        assertEquals(WorkState.ACTIVE, item.getState());
        assertEquals(WorkItem.TypeEnum.WORK, item.getType());
        client.ackWork("env-1", "work-1", "worker-1");
        assertEquals(
                "2026-08-24T10:00:01Z",
                client.heartbeatWork("env-1", "work-1", "NO_HEARTBEAT", 30).getLastHeartbeat());
        client.stopWork("env-1", "work-1", false);

        assertEquals(4, calls.get());
    }

    private static void assertPollURL(String baseUrl, String expectedHost) {
        AtomicReference<HttpUrl> requestedURL = new AtomicReference<>();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> response(chain.request(), requestedURL))
                .build();
        SelfHostedClient.Builder builder = new SelfHostedClient.Builder()
                .apiKey("test-api-key")
                .httpClient(httpClient);
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }

        builder.build().pollWork("env-1", "worker-1", 999, 0);

        assertEquals(expectedHost, requestedURL.get().host());
        assertEquals("/api/v3/environments/env-1/work/poll", requestedURL.get().encodedPath());
    }

    private static Response response(Request request, AtomicReference<HttpUrl> requestedURL) throws IOException {
        return response(request, requestedURL, "{}");
    }

    private static Response response(
            Request request,
            AtomicReference<HttpUrl> requestedURL,
            String body) throws IOException {
        requestedURL.set(request.url());
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), body))
                .build();
    }

    private static void writeResponse(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static byte[] readAll(SkillContent content) throws IOException {
        byte[] buffer = new byte[1024];
        int size = content.getBody().read(buffer);
        byte[] result = new byte[size];
        System.arraycopy(buffer, 0, result, 0, size);
        return result;
    }
}
