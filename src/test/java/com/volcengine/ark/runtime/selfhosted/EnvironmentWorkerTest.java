// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.volcengine.ark.runtime.models.environment.HeartbeatWorkResponse;
import com.volcengine.ark.runtime.models.environment.WorkState;
import com.volcengine.ark.runtime.models.environment.WorkStopReason;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;

public class EnvironmentWorkerTest {
    @Test
    public void defaultWorkerIdIsUniquePerWorker() {
        String first = EnvironmentWorker.defaultWorkerId();
        String second = EnvironmentWorker.defaultWorkerId();

        assertNotEquals(first, second);
        assertEquals(12, first.substring(first.lastIndexOf('-') + 1).length());
    }

    @Test
    public void workerUsesConfiguredWorkdir() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-worker-");
        EnvironmentWorker worker = new EnvironmentWorker(
                new SelfHostedClient("test-key"),
                new EnvironmentWorker.Options().workdir(workdir.toString()));
        Method method = EnvironmentWorker.class.getDeclaredMethod("workdir");
        method.setAccessible(true);

        assertEquals(workdir.toAbsolutePath().normalize().toString(), method.invoke(worker));
    }

    @Test
    public void emptyHeartbeatResponseDoesNotSpin() throws Exception {
        NullHeartbeatClient client = new NullHeartbeatClient();
        EnvironmentWorker worker = new EnvironmentWorker(
                client,
                new EnvironmentWorker.Options().workdir(Files.createTempDirectory("ark-java-worker-").toString()));

        try {
            worker.handleItem(new EnvironmentWorker.HandleItemOptions()
                    .environmentId("env-1")
                    .workId("work-1")
                    .sessionId("session-1"));
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("session response is empty"));
        }

        assertEquals(1, client.heartbeats.get());
        assertEquals(WorkStopReason.WORKER_ABNORMAL, client.stopReason.get());
    }

    @Test
    public void heartbeatStopCancelsRunnerBeforeEventPolling() throws Exception {
        CountDownLatch heartbeat = new CountDownLatch(1);
        AtomicInteger lists = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        AtomicReference<String> stopBody = new AtomicReference<>();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            String path = request.url().encodedPath();
            if (path.endsWith("/heartbeat")) {
                heartbeat.countDown();
                return response(request, "{\"state\":\"stopping\",\"lease_extended\":true}");
            }
            if (path.endsWith("/sessions/session-1")) {
                try {
                    assertTrue(heartbeat.await(2, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException(error);
                }
                return response(request, "{\"id\":\"session-1\"}");
            }
            if (path.endsWith("/events")) {
                lists.incrementAndGet();
                return response(request, "{\"data\":[]}");
            }
            if (path.endsWith("/stop")) {
                stops.incrementAndGet();
                Buffer buffer = new Buffer();
                request.body().writeTo(buffer);
                stopBody.set(buffer.readUtf8());
            }
            return response(request, "{}");
        }).build();
        SelfHostedClient client = new SelfHostedClient.Builder()
                .apiKey("test-key")
                .baseUrl("https://ark.example.com/api/v3")
                .httpClient(http)
                .build();
        EnvironmentWorker worker = new EnvironmentWorker(
                client,
                new EnvironmentWorker.Options().workdir(Files.createTempDirectory("ark-java-worker-").toString()));

        long started = System.nanoTime();
        worker.handleItem(new EnvironmentWorker.HandleItemOptions()
                .environmentId("env-1")
                .workId("work-1")
                .sessionId("session-1"));

        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1000L);
        assertEquals(0, lists.get());
        assertEquals(1, stops.get());
        assertEquals("{\"force\":true}", stopBody.get());
    }

    @Test
    public void externalCloseReportsWorkerAbnormal() throws Exception {
        CloseClient client = new CloseClient();
        EnvironmentWorker worker = new EnvironmentWorker(
                client,
                new EnvironmentWorker.Options().workdir(Files.createTempDirectory("ark-java-worker-").toString()));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread handling = new Thread(() -> {
            try {
                worker.handleItem(new EnvironmentWorker.HandleItemOptions()
                        .environmentId("env-1")
                        .workId("work-1")
                        .sessionId("session-1"));
            } catch (Throwable error) {
                failure.set(error);
            }
        });

        handling.start();
        assertTrue(client.eventPolling.await(2, TimeUnit.SECONDS));
        worker.close();
        handling.join(2000L);

        assertFalse(handling.isAlive());
        assertNull(failure.get());
        assertEquals(WorkStopReason.WORKER_ABNORMAL, client.stopReason.get());
    }

    @Test
    public void leaseLostDoesNotStopWorkOwnedByAnotherWorker() throws Exception {
        LeaseLostClient client = new LeaseLostClient();
        EnvironmentWorker worker = new EnvironmentWorker(
                client,
                new EnvironmentWorker.Options()
                        .workdir(Files.createTempDirectory("ark-java-worker-").toString()));

        worker.handleItem(new EnvironmentWorker.HandleItemOptions()
                .environmentId("env-1")
                .workId("work-1")
                .sessionId("session-1"));

        assertEquals(0, client.stops.get());
    }

    @Test
    public void leaseNotExtendedWithoutTtlDoesNotStopWorkOwnedByAnotherWorker() throws Exception {
        LeaseNotExtendedClient client = new LeaseNotExtendedClient();
        EnvironmentWorker worker = new EnvironmentWorker(
                client,
                new EnvironmentWorker.Options()
                        .workdir(Files.createTempDirectory("ark-java-worker-").toString()));

        worker.handleItem(new EnvironmentWorker.HandleItemOptions()
                .environmentId("env-1")
                .workId("work-1")
                .sessionId("session-1"));

        assertEquals(0, client.stops.get());
    }

    @Test
    public void workerToolTimeoutOverridesToolContext() throws Exception {
        ToolContext baseContext = new ToolContext(".");
        baseContext.setToolTimeoutMillis(5000L);
        EnvironmentWorker worker = new EnvironmentWorker(
                new SelfHostedClient("test-key"),
                new EnvironmentWorker.Options()
                        .toolContext(baseContext)
                        .toolTimeoutMillis(20L));
        Method method = EnvironmentWorker.class.getDeclaredMethod(
                "toolContext", String.class, AtomicBoolean.class);
        method.setAccessible(true);

        ToolContext context = (ToolContext) method.invoke(worker, ".", new AtomicBoolean(false));

        assertEquals(20L, context.getToolTimeoutMillis());
    }

    @Test
    public void workerKeepsToolContextTimeoutWhenUnset() throws Exception {
        ToolContext baseContext = new ToolContext(".");
        baseContext.setToolTimeoutMillis(5000L);
        EnvironmentWorker worker = new EnvironmentWorker(
                new SelfHostedClient("test-key"),
                new EnvironmentWorker.Options().toolContext(baseContext));
        Method method = EnvironmentWorker.class.getDeclaredMethod(
                "toolContext", String.class, AtomicBoolean.class);
        method.setAccessible(true);

        ToolContext context = (ToolContext) method.invoke(worker, ".", new AtomicBoolean(false));

        assertEquals(5000L, context.getToolTimeoutMillis());
    }

    @Test
    public void workerKeepsToolContextTimeoutWhenNonpositive() throws Exception {
        ToolContext baseContext = new ToolContext(".");
        baseContext.setToolTimeoutMillis(5000L);
        EnvironmentWorker worker = new EnvironmentWorker(
                new SelfHostedClient("test-key"),
                new EnvironmentWorker.Options()
                        .toolContext(baseContext)
                        .toolTimeoutMillis(-1L));
        Method method = EnvironmentWorker.class.getDeclaredMethod(
                "toolContext", String.class, AtomicBoolean.class);
        method.setAccessible(true);

        ToolContext context = (ToolContext) method.invoke(worker, ".", new AtomicBoolean(false));

        assertEquals(5000L, context.getToolTimeoutMillis());
    }

    private static Response response(Request request, String body) throws IOException {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), body))
                .build();
    }

    private static class NullHeartbeatClient extends SelfHostedClient {
        private final CountDownLatch firstHeartbeat = new CountDownLatch(1);
        private final AtomicInteger heartbeats = new AtomicInteger();
        private final AtomicReference<WorkStopReason> stopReason = new AtomicReference<>();

        NullHeartbeatClient() {
            super("test-key");
        }

        @Override
        public HeartbeatWorkResponse heartbeatWork(
                String environmentId, String workId, String expectedLastHeartbeat, int desiredTTLSeconds) {
            heartbeats.incrementAndGet();
            firstHeartbeat.countDown();
            return null;
        }

        @Override
        public SessionSnapshot getSession(String sessionId) {
            try {
                assertTrue(firstHeartbeat.await(2, TimeUnit.SECONDS));
                Thread.sleep(100L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(error);
            }
            return null;
        }

        @Override
        public void stopWork(String environmentId, String workId, boolean force, WorkStopReason reason) {
            stopReason.set(reason);
        }
    }

    private static class CloseClient extends SelfHostedClient {
        private final CountDownLatch eventPolling = new CountDownLatch(1);
        private final AtomicReference<WorkStopReason> stopReason = new AtomicReference<>();

        CloseClient() {
            super("test-key");
        }

        @Override
        public HeartbeatWorkResponse heartbeatWork(
                String environmentId, String workId, String expectedLastHeartbeat, int desiredTTLSeconds) {
            return new HeartbeatWorkResponse()
                    .state(WorkState.ACTIVE)
                    .leaseExtended(Boolean.TRUE)
                    .lastHeartbeat("2026-09-20T00:00:00Z")
                    .ttlSeconds(30L);
        }

        @Override
        public SessionSnapshot getSession(String sessionId) {
            SessionSnapshot session = new SessionSnapshot();
            session.setId(sessionId);
            return session;
        }

        @Override
        public EventStream openEventStream(String sessionId) {
            throw new SessionToolRunner.StreamUnsupportedException();
        }

        @Override
        public ListEventsResponse listEvents(
                String sessionId, String createdAtGt, String page, int limit, String order, List<String> types) {
            eventPolling.countDown();
            return new ListEventsResponse(Collections.<Event>emptyList(), "");
        }

        @Override
        public void stopWork(String environmentId, String workId, boolean force, WorkStopReason reason) {
            stopReason.set(reason);
        }
    }

    private static class LeaseLostClient extends SelfHostedClient {
        private final CountDownLatch heartbeat = new CountDownLatch(1);
        private final AtomicInteger stops = new AtomicInteger();

        LeaseLostClient() {
            super("test-key");
        }

        @Override
        public HeartbeatWorkResponse heartbeatWork(
                String environmentId, String workId, String expectedLastHeartbeat, int desiredTTLSeconds) {
            heartbeat.countDown();
            throw new WorkerAPIException(412, "lease lost", "");
        }

        @Override
        public SessionSnapshot getSession(String sessionId) {
            try {
                assertTrue(heartbeat.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(error);
            }
            SessionSnapshot session = new SessionSnapshot();
            session.setId(sessionId);
            return session;
        }

        @Override
        public void stopWork(String environmentId, String workId, boolean force, WorkStopReason reason) {
            stops.incrementAndGet();
        }
    }

    private static class LeaseNotExtendedClient extends SelfHostedClient {
        private final CountDownLatch heartbeat = new CountDownLatch(1);
        private final AtomicInteger stops = new AtomicInteger();

        LeaseNotExtendedClient() {
            super("test-key");
        }

        @Override
        public HeartbeatWorkResponse heartbeatWork(
                String environmentId, String workId, String expectedLastHeartbeat, int desiredTTLSeconds) {
            heartbeat.countDown();
            return new HeartbeatWorkResponse()
                    .state(WorkState.ACTIVE)
                    .leaseExtended(Boolean.FALSE);
        }

        @Override
        public SessionSnapshot getSession(String sessionId) {
            try {
                assertTrue(heartbeat.await(2, TimeUnit.SECONDS));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(error);
            }
            SessionSnapshot session = new SessionSnapshot();
            session.setId(sessionId);
            return session;
        }

        @Override
        public void stopWork(String environmentId, String workId, boolean force, WorkStopReason reason) {
            stops.incrementAndGet();
        }
    }
}
