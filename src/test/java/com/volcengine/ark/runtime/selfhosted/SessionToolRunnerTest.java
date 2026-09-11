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

    @Test
    public void builtinToolTimeoutAbandonsNoncooperativeTool() throws Exception {
        assertToolTimeoutAbandonsNoncooperativeTool(false);
    }

    @Test
    public void customToolTimeoutAbandonsNoncooperativeTool() throws Exception {
        assertToolTimeoutAbandonsNoncooperativeTool(true);
    }

    @Test
    public void recoveredResultsAreFilteredAgainstCurrentBlockers() throws Exception {
        List<String> discarded = new ArrayList<>();
        List<String> marked = new ArrayList<>();
        FileToolResultStore store = recordingStore(discarded, marked);
        AtomicInteger executions = new AtomicInteger();
        List<Event> sent = new ArrayList<>();
        Tool tool = countingTool(executions);
        SessionToolRunner runner = runnerWithStore(store, tool, sent);
        stateMap(runner, "pendingResults").put(
                "foreign-call", Event.newUserCustomToolResultEvent("foreign-call", Collections.emptyList(), false, ""));
        stateMap(runner, "pendingResults").put(
                "stale-call", Event.newUserCustomToolResultEvent("stale-call", Collections.emptyList(), false, ""));
        stateMap(runner, "recoveredResults").put("foreign-call", Boolean.TRUE);
        stateMap(runner, "recoveredResults").put("stale-call", Boolean.TRUE);

        processListedEvents(
                runner,
                java.util.Arrays.asList(
                        toolUse("stale-call"),
                        toolUse("current-call"),
                        requiresAction("current-call")));

        assertEquals(1, executions.get());
        assertEquals(1, sent.size());
        assertEquals("current-call", sent.get(0).resultCallId());
        assertTrue(discarded.contains("foreign-call"));
        assertTrue(discarded.contains("stale-call"));
        assertTrue(stateMap(runner, "pendingResults").isEmpty());
        assertTrue(stateMap(runner, "recoveredResults").isEmpty());
        runner.close();
    }

    @Test
    public void currentBlockerReusesRecoveredResultWithoutReexecution() throws Exception {
        List<String> discarded = new ArrayList<>();
        List<String> marked = new ArrayList<>();
        FileToolResultStore store = recordingStore(discarded, marked);
        AtomicInteger executions = new AtomicInteger();
        List<Event> sent = new ArrayList<>();
        SessionToolRunner runner = runnerWithStore(store, countingTool(executions), sent);
        Event result = Event.newUserCustomToolResultEvent("current-call", Collections.emptyList(), false, "");
        stateMap(runner, "pendingResults").put("current-call", result);
        stateMap(runner, "recoveredResults").put("current-call", Boolean.TRUE);

        processListedEvents(runner, java.util.Arrays.asList(toolUse("current-call"), requiresAction("current-call")));

        assertEquals(0, executions.get());
        assertEquals(Collections.singletonList(result), sent);
        assertEquals(Collections.singletonList("current-call"), marked);
        assertTrue(discarded.isEmpty());
        runner.close();
    }

    @Test
    public void recoveredResultWaitsForAuthoritativeStatus() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        List<Event> sent = new ArrayList<>();
        SessionToolRunner runner = runnerWithStore(null, countingTool(executions), sent);
        stateMap(runner, "pendingResults").put(
                "current-call", Event.newUserCustomToolResultEvent("current-call", Collections.emptyList(), false, ""));
        stateMap(runner, "recoveredResults").put("current-call", Boolean.TRUE);

        processListedEvents(runner, Collections.singletonList(toolUse("current-call")));

        assertEquals(0, executions.get());
        assertTrue(sent.isEmpty());
        assertTrue(stateMap(runner, "recoveredResults").containsKey("current-call"));
        runner.close();
    }

    private static void assertToolTimeoutAbandonsNoncooperativeTool(boolean custom) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Tool tool = new Tool() {
            @Override
            public String name() {
                return "blocking";
            }

            @Override
            public ToolResult execute(Object input, ToolContext context) {
                started.countDown();
                while (true) {
                    try {
                        release.await();
                        return ToolResult.text("late");
                    } catch (InterruptedException ignored) {
                        // Deliberately ignore cancellation to verify the runner's outer deadline.
                    }
                }
            }
        };
        ToolContext context = new ToolContext(Files.createTempDirectory("ark-java-timeout-").toString());
        context.setToolTimeoutMillis(20L);
        SessionToolRunner.Options options = new SessionToolRunner.Options()
                .tools(custom ? new ToolSet() : new ToolSet().add(tool))
                .toolContext(context);
        if (custom) {
            options.customTools(Collections.singletonMap(tool.name(), tool));
        }
        SessionToolRunner runner = new SessionToolRunner(new SelfHostedClient("test-key"), "session-1", options);
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", "tool-1");
        raw.put("type", custom ? "agent.custom_tool_use" : "agent.tool_use");
        raw.put("name", tool.name());
        raw.put(custom ? "custom_tool_use_id" : "tool_use_id", "call-1");
        raw.put("input", Collections.emptyMap());
        Method execute = SessionToolRunner.class.getDeclaredMethod("executeTool", Event.class, boolean.class);
        execute.setAccessible(true);

        long startedAt = System.nanoTime();
        ToolResult result;
        try {
            result = (ToolResult) execute.invoke(runner, Event.fromMap(raw), custom);
            assertTrue(started.await(1L, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            runner.close();
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertTrue("elapsed=" + elapsedMillis, elapsedMillis < 500L);
        assertTrue(result.isError());
        assertEquals("tool execution timed out after 20ms", result.getContent().get(0).getText());
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

    private static FileToolResultStore recordingStore(List<String> discarded, List<String> marked)
            throws IOException {
        return new FileToolResultStore(Files.createTempDirectory("ark-java-recovery-store-").toString()) {
            @Override
            public ToolCallStoreDecision begin(String callId, Event event) {
                return new ToolCallStoreDecision(false, null);
            }

            @Override
            public void saveResult(String callId, Event result) {
            }

            @Override
            public void markSent(String callId) {
                marked.add(callId);
            }

            @Override
            public void discard(String callId) {
                discarded.add(callId);
            }
        };
    }

    private static Tool countingTool(AtomicInteger executions) {
        return new Tool() {
            @Override
            public String name() {
                return "custom";
            }

            @Override
            public ToolResult execute(Object input, ToolContext context) {
                executions.incrementAndGet();
                return ToolResult.text("ok");
            }
        };
    }

    private static SessionToolRunner runnerWithStore(FileToolResultStore store, Tool tool, List<Event> sent)
            throws IOException {
        SelfHostedClient client = new SelfHostedClient("test-key") {
            @Override
            public void sendEvent(String sessionId, Event event) {
                sent.add(event);
            }
        };
        return new SessionToolRunner(
                client,
                "session-1",
                new SessionToolRunner.Options()
                        .tools(new ToolSet())
                        .toolContext(new ToolContext(
                                Files.createTempDirectory("ark-java-recovery-runner-").toString()))
                        .customTools(Collections.singletonMap("custom", tool))
                        .resultStore(store));
    }

    private static Event toolUse(String callId) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", callId);
        raw.put("type", "agent.custom_tool_use");
        raw.put("name", "custom");
        raw.put("input", Collections.emptyMap());
        return Event.fromMap(raw);
    }

    private static Event requiresAction(String callId) {
        Map<String, Object> stopReason = new LinkedHashMap<>();
        stopReason.put("type", "requires_action");
        stopReason.put("event_ids", Collections.singletonList(callId));
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", "idle-" + callId);
        raw.put("type", "session.status_idle");
        raw.put("stop_reason", stopReason);
        return Event.fromMap(raw);
    }

    private static void processListedEvents(SessionToolRunner runner, List<Event> events) throws Exception {
        Method process = SessionToolRunner.class.getDeclaredMethod("processListedEvents", List.class, boolean.class);
        process.setAccessible(true);
        process.invoke(runner, events, true);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stateMap(SessionToolRunner runner, String fieldName) throws Exception {
        Field stateField = SessionToolRunner.class.getDeclaredField("state");
        stateField.setAccessible(true);
        Object state = stateField.get(runner);
        Field field = state.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<String, Object>) field.get(state);
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
