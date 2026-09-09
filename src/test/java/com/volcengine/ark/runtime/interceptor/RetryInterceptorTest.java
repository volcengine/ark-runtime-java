// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.interceptor;

import static com.volcengine.ark.runtime.Const.RETRY_COUNT_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import okio.BufferedSink;
import okhttp3.OkHttpClient;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;

public class RetryInterceptorTest {
    @Test
    public void retryAfterMillisecondsTakesPriorityAndRetryCountTracksAttempts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> retryCounts = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/retry", exchange -> {
            retryCounts.add(exchange.getRequestHeaders().getFirst(RETRY_COUNT_HEADER));
            int call = calls.getAndIncrement();
            if (call == 0) {
                exchange.getResponseHeaders().add("Retry-After-Ms", "125.5");
                exchange.getResponseHeaders().add("Retry-After", "9");
                exchange.sendResponseHeaders(429, -1);
            } else {
                exchange.sendResponseHeaders(200, -1);
            }
            exchange.close();
        });
        server.start();
        RecordingRetryInterceptor interceptor = new RecordingRetryInterceptor(2);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor).build();
        try (Response response = client.newCall(new Request.Builder()
                .url("http://127.0.0.1:" + server.getAddress().getPort() + "/retry")
                .build()).execute()) {
            assertEquals(200, response.code());
        } finally {
            server.stop(0);
        }

        assertEquals(Arrays.asList("0", "1"), retryCounts);
        assertEquals(Arrays.asList(Duration.ofNanos(125_500_000L)), interceptor.delays);
    }

    @Test
    public void legacyRetryIntervalKeepsOriginalBehavior() {
        RetryInterceptor interceptor = new RetryInterceptor(2);
        double first = interceptor.retryInterval(2, 1);
        double second = interceptor.retryInterval(2, 0);
        assertTrue(first >= 0.75 && first <= 1.0);
        assertTrue(second >= 1.5 && second <= 2.0);
    }

    @Test
    public void invalidServerRetryDelaysFallBackToInitialBackoff() throws Exception {
        assertInvalidRetryAfterFallsBack("Retry-After", "0");
        assertInvalidRetryAfterFallsBack("Retry-After", "-1");
        assertInvalidRetryAfterFallsBack("Retry-After", "61");
        assertInvalidRetryAfterFallsBack("Retry-After", "Infinity");
        assertInvalidRetryAfterFallsBack("Retry-After-Ms", "NaN");
    }

    @Test
    public void serverRetryOverridesAndCustomRetryHeaderAreHonored() throws Exception {
        AtomicInteger forcedRetryCalls = new AtomicInteger();
        AtomicInteger suppressedRetryCalls = new AtomicInteger();
        List<String> customRetryCounts = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forced", exchange -> {
            customRetryCounts.add(exchange.getRequestHeaders().getFirst(RETRY_COUNT_HEADER));
            if (forcedRetryCalls.getAndIncrement() == 0) {
                exchange.getResponseHeaders().add("X-Should-Retry", "true");
                exchange.sendResponseHeaders(400, -1);
            } else {
                exchange.sendResponseHeaders(200, -1);
            }
            exchange.close();
        });
        server.createContext("/suppressed", exchange -> {
            suppressedRetryCalls.incrementAndGet();
            exchange.getResponseHeaders().add("X-Should-Retry", "false");
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        RecordingRetryInterceptor interceptor = new RecordingRetryInterceptor(2);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor).build();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            try (Response response = client.newCall(new Request.Builder()
                    .url(baseUrl + "/forced")
                    .header(RETRY_COUNT_HEADER, "custom")
                    .build()).execute()) {
                assertEquals(200, response.code());
            }
            try (Response response = client.newCall(new Request.Builder()
                    .url(baseUrl + "/suppressed")
                    .build()).execute()) {
                assertEquals(500, response.code());
            }
        } finally {
            server.stop(0);
        }

        assertEquals(Arrays.asList("custom", "custom"), customRetryCounts);
        assertEquals(2, forcedRetryCalls.get());
        assertEquals(1, suppressedRetryCalls.get());
    }

    @Test
    public void conflictIsRetriedByDefault() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/conflict", exchange -> {
            if (calls.getAndIncrement() == 0) {
                exchange.sendResponseHeaders(409, -1);
            } else {
                exchange.sendResponseHeaders(200, -1);
            }
            exchange.close();
        });
        server.start();
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new RetryInterceptor(1)).build();
        try (Response response = client.newCall(new Request.Builder()
                .url("http://127.0.0.1:" + server.getAddress().getPort() + "/conflict")
                .build()).execute()) {
            assertEquals(200, response.code());
        } finally {
            server.stop(0);
        }
        assertEquals(2, calls.get());
    }

    @Test
    public void runtimeExceptionsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("broken interceptor");
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(2))
                .addInterceptor(chain -> {
                    calls.incrementAndGet();
                    throw failure;
                })
                .build();

        try {
            client.newCall(new Request.Builder().url("https://ark.example.com/test").build()).execute();
        } catch (IllegalStateException actual) {
            assertTrue(actual == failure);
        } catch (Exception actual) {
            throw new AssertionError(actual);
        }
        assertEquals(1, calls.get());
    }

    @Test
    public void oneShotRequestBodiesAreNotRetried() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RequestBody oneShotBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/json");
            }

            @Override
            public void writeTo(BufferedSink sink) throws java.io.IOException {
                sink.writeUtf8("{}");
            }

            @Override
            public boolean isOneShot() {
                return true;
            }
        };
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(2))
                .addInterceptor(chain -> {
                    calls.incrementAndGet();
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(500)
                            .message("error")
                            .body(okhttp3.ResponseBody.create(MediaType.parse("application/json"), "{}"))
                            .build();
                })
                .build();

        try (Response response = client.newCall(new Request.Builder()
                .url("https://ark.example.com/test")
                .post(oneShotBody)
                .build()).execute()) {
            assertEquals(500, response.code());
        }
        assertEquals(1, calls.get());
    }

    private void assertInvalidRetryAfterFallsBack(String header, String value) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/retry", exchange -> {
            if (calls.getAndIncrement() == 0) {
                exchange.getResponseHeaders().add(header, value);
                exchange.sendResponseHeaders(500, -1);
            } else {
                exchange.sendResponseHeaders(200, -1);
            }
            exchange.close();
        });
        server.start();
        RecordingRetryInterceptor interceptor = new RecordingRetryInterceptor(1);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor).build();
        try (Response response = client.newCall(new Request.Builder()
                .url("http://127.0.0.1:" + server.getAddress().getPort() + "/retry")
                .build()).execute()) {
            assertEquals(200, response.code());
        } finally {
            server.stop(0);
        }
        assertEquals(1, interceptor.delays.size());
        Duration delay = interceptor.delays.get(0);
        assertTrue(delay.compareTo(Duration.ofMillis(375)) >= 0);
        assertTrue(delay.compareTo(Duration.ofMillis(500)) <= 0);
    }

    private static final class RecordingRetryInterceptor extends RetryInterceptor {
        private final List<Duration> delays = new ArrayList<>();

        private RecordingRetryInterceptor(int retryTimes) {
            super(retryTimes);
        }

        @Override
        protected void sleep(Duration duration) {
            delays.add(duration);
        }
    }
}
