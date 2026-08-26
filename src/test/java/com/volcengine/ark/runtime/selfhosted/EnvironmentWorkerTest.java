// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class EnvironmentWorkerTest {
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
    }

    @Test
    public void heartbeatStopCancelsRunnerBeforeEventPolling() throws Exception {
        CountDownLatch heartbeat = new CountDownLatch(1);
        AtomicInteger lists = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            String path = request.url().encodedPath();
            if (path.endsWith("/heartbeat")) {
                heartbeat.countDown();
                return response(request, "{\"state\":\"stopping\",\"lease_extended\":true,\"ttl_seconds\":30}");
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

        NullHeartbeatClient() {
            super("test-key");
        }

        @Override
        public HeartbeatResponse heartbeatWork(
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
        public void stopWork(String environmentId, String workId, boolean force) {
        }
    }

    private static class LeaseLostClient extends SelfHostedClient {
        private final CountDownLatch heartbeat = new CountDownLatch(1);
        private final AtomicInteger stops = new AtomicInteger();

        LeaseLostClient() {
            super("test-key");
        }

        @Override
        public HeartbeatResponse heartbeatWork(
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
        public void stopWork(String environmentId, String workId, boolean force) {
            stops.incrementAndGet();
        }
    }
}
