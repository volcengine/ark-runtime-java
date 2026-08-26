// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class SessionToolRunnerTest {
    @Test
    public void listFallbackRetriesFullHistoryAndConvertsCustomToolFailure() throws Exception {
        AtomicInteger lists = new AtomicInteger();
        CountDownLatch sent = new CountDownLatch(1);
        AtomicReference<String> createdAt = new AtomicReference<>();
        AtomicReference<String> limit = new AtomicReference<>();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            if (request.method().equals("GET") && request.url().encodedPath().endsWith("/events")) {
                createdAt.set(request.url().queryParameter("created_at[gt]"));
                limit.set(request.url().queryParameter("limit"));
                if (lists.getAndIncrement() == 0) {
                    return response(request, 500, "temporary");
                }
                return response(
                        request,
                        200,
                        "{\"data\":[{\"id\":\"event-1\",\"type\":\"agent.custom_tool_use\","
                                + "\"name\":\"custom\",\"custom_tool_use_id\":\"call-1\","
                                + "\"session_thread_id\":\"thread-1\",\"input\":{}}]}");
            }
            if (request.method().equals("POST") && request.url().encodedPath().endsWith("/events")) {
                sent.countDown();
            }
            return response(request, 200, "{}");
        }).build();
        SelfHostedClient client = new SelfHostedClient.Builder()
                .apiKey("test-key")
                .baseUrl("https://ark.example.com/api/v3")
                .httpClient(http)
                .build();
        Tool custom = new Tool() {
            @Override
            public String name() {
                return "custom";
            }

            @Override
            public ToolResult execute(Object input, ToolContext context) {
                throw new IllegalStateException("custom tool failed");
            }
        };
        SessionToolRunner runner = new SessionToolRunner(
                client,
                "session-1",
                new SessionToolRunner.Options()
                        .tools(new ToolSet())
                        .toolContext(new ToolContext(Files.createTempDirectory("ark-java-runner-").toString()))
                        .customTools(Collections.singletonMap("custom", custom))
                        .preferStream(false)
                        .eventLimit(5000)
                        .eventPollIntervalMillis(10L));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                runner.run();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        thread.start();

        assertTrue(sent.await(3, TimeUnit.SECONDS));
        runner.close();
        thread.join(2000L);

        assertFalse(thread.isAlive());
        assertNull(failure.get());
        assertNull(createdAt.get());
        assertEquals("1000", limit.get());
        assertTrue(lists.get() >= 2);
        assertEquals(1, runner.getResults().size());
        assertEquals(Boolean.TRUE, runner.getResults().get(0).getResult().toMap().get("is_error"));
        assertEquals("custom tool failed", runner.getResults().get(0).getResult().getContent().get(0).getText());
    }

    @Test
    public void constructorRequiresToolContext() {
        try {
            new SessionToolRunner(
                    new SelfHostedClient("test-key"),
                    "session-1",
                    new SessionToolRunner.Options().tools(new ToolSet()));
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("tool context"));
            return;
        }
        throw new AssertionError("expected missing tool context failure");
    }

    @Test
    public void duplicateStreamIdleEventDoesNotResetIdleDeadline() throws Exception {
        SessionToolRunner runner = idleRunner();
        Event event = idleEvent();
        Method handle = SessionToolRunner.class.getDeclaredMethod("handleStreamEvent", Event.class);
        handle.setAccessible(true);

        handle.invoke(runner, event);
        long armedAt = idleArmedAt(runner);
        Thread.sleep(2L);
        handle.invoke(runner, event);

        assertEquals(armedAt, idleArmedAt(runner));
    }

    @Test
    public void reconcileDoesNotResetIdleDeadlineForSeenHistory() throws Exception {
        SessionToolRunner runner = idleRunner();
        List<Event> events = new ArrayList<>();
        events.add(idleEvent());
        Method process = SessionToolRunner.class.getDeclaredMethod(
                "processListedEvents", List.class, boolean.class);
        process.setAccessible(true);

        process.invoke(runner, events, true);
        long armedAt = idleArmedAt(runner);
        Thread.sleep(2L);
        process.invoke(runner, events, true);

        assertEquals(armedAt, idleArmedAt(runner));
    }

    @Test
    public void successfulSendStaysAnsweredWhenMarkSentFails() throws Exception {
        SelfHostedClient client = new SelfHostedClient("test-key") {
            @Override
            public void sendEvent(String sessionId, Event event) {
            }
        };
        FileToolResultStore store = new FileToolResultStore(
                Files.createTempDirectory("ark-java-mark-sent-").toString()) {
            @Override
            public void markSent(String callId) throws IOException {
                throw new IOException("ledger unavailable");
            }
        };
        SessionToolRunner runner = new SessionToolRunner(
                client,
                "session-1",
                new SessionToolRunner.Options()
                        .tools(new ToolSet())
                        .toolContext(new ToolContext(
                                Files.createTempDirectory("ark-java-runner-").toString()))
                        .resultStore(store));
        Map<String, Object> sourceRaw = new LinkedHashMap<>();
        sourceRaw.put("id", "tool-1");
        sourceRaw.put("type", "agent.tool_use");
        sourceRaw.put("name", "bash");
        sourceRaw.put("tool_use_id", "call-1");
        Event source = Event.fromMap(sourceRaw);
        Event result = Event.newUserToolResultEvent(
                "call-1", Collections.singletonList(new ContentBlock("text", "ok")), false, "");
        Method send = SessionToolRunner.class.getDeclaredMethod(
                "sendResult", String.class, Event.class, boolean.class, String.class, Event.class);
        send.setAccessible(true);

        send.invoke(runner, "call-1", source, false, "", result);

        assertTrue(answered(runner).containsKey("call-1"));
        assertEquals(1, runner.getResults().size());
        assertTrue(runner.getResults().get(0).isPosted());
    }

    private static SessionToolRunner idleRunner() throws IOException {
        return new SessionToolRunner(
                new SelfHostedClient("test-key"),
                "session-1",
                new SessionToolRunner.Options()
                        .tools(new ToolSet())
                        .toolContext(new ToolContext(
                                Files.createTempDirectory("ark-java-idle-").toString())));
    }

    private static Event idleEvent() {
        Map<String, Object> stopReason = new LinkedHashMap<>();
        stopReason.put("type", "end_turn");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", "idle-1");
        raw.put("type", "session.status_idle");
        raw.put("stop_reason", stopReason);
        return Event.fromMap(raw);
    }

    private static long idleArmedAt(SessionToolRunner runner) throws Exception {
        Field stateField = SessionToolRunner.class.getDeclaredField("state");
        stateField.setAccessible(true);
        Object state = stateField.get(runner);
        Field idleField = state.getClass().getDeclaredField("idleArmedAt");
        idleField.setAccessible(true);
        return idleField.getLong(state);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Boolean> answered(SessionToolRunner runner) throws Exception {
        Field stateField = SessionToolRunner.class.getDeclaredField("state");
        stateField.setAccessible(true);
        Object state = stateField.get(runner);
        Field answeredField = state.getClass().getDeclaredField("answered");
        answeredField.setAccessible(true);
        return (Map<String, Boolean>) answeredField.get(state);
    }

    private static Response response(Request request, int code, String body) throws IOException {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code < 400 ? "OK" : "error")
                .body(ResponseBody.create(MediaType.parse("application/json"), body))
                .build();
    }
}
