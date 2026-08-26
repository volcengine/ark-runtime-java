// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class WorkPollerTest {
    @Test
    public void leaseAndRetryStatusesAreNotFatal() {
        int[] statuses = {408, 409, 412, 429};
        for (int status : statuses) {
            assertFalse(WorkerAPIException.isFatal4xx(new WorkerAPIException(status, "recoverable", "")));
        }
    }

    @Test
    public void ackConflictDoesNotStopUnownedWork() {
        AtomicInteger polls = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        SelfHostedClient client = client(chain -> {
            Request request = chain.request();
            String path = request.url().encodedPath();
            if (path.endsWith("/work/poll")) {
                String body = polls.getAndIncrement() == 0
                        ? "{\"id\":\"work-1\",\"environment_id\":\"env-1\","
                                + "\"data\":{\"id\":\"session-1\",\"type\":\"session\"}}"
                        : "{}";
                return response(request, 200, body);
            }
            if (path.endsWith("/ack")) {
                return response(request, 409, "already claimed");
            }
            if (path.endsWith("/stop")) {
                stops.incrementAndGet();
            }
            return response(request, 200, "{}");
        });
        WorkPoller poller = new WorkPoller(client, new WorkPoller.Options("env-1").drain(true));

        assertNull(poller.next());
        assertNull(poller.error());
        assertEquals(0, stops.get());
    }

    @Test
    public void fatalAckErrorStopsPollerWithoutStoppingWork() {
        AtomicInteger stops = new AtomicInteger();
        SelfHostedClient client = client(chain -> {
            Request request = chain.request();
            String path = request.url().encodedPath();
            if (path.endsWith("/work/poll")) {
                return response(
                        request,
                        200,
                        "{\"id\":\"work-1\",\"environment_id\":\"env-1\","
                                + "\"data\":{\"id\":\"session-1\",\"type\":\"session\"}}");
            }
            if (path.endsWith("/ack")) {
                return response(request, 403, "forbidden");
            }
            if (path.endsWith("/stop")) {
                stops.incrementAndGet();
            }
            return response(request, 200, "{}");
        });
        WorkPoller poller = new WorkPoller(client, new WorkPoller.Options("env-1").drain(true));

        assertNull(poller.next());
        assertEquals(403, ((WorkerAPIException) poller.error()).getStatusCode());
        assertEquals(0, stops.get());
    }

    @Test
    public void autoStopCanBeDisabledForEnvironmentWorkerOwnership() {
        AtomicInteger stops = new AtomicInteger();
        SelfHostedClient client = client(chain -> {
            Request request = chain.request();
            if (request.url().encodedPath().endsWith("/work/poll")) {
                return response(
                        request,
                        200,
                        "{\"id\":\"work-1\",\"environment_id\":\"env-1\","
                                + "\"data\":{\"id\":\"session-1\",\"type\":\"session\"}}");
            }
            if (request.url().encodedPath().endsWith("/stop")) {
                stops.incrementAndGet();
            }
            return response(request, 200, "{}");
        });
        WorkPoller poller = new WorkPoller(
                client, new WorkPoller.Options("env-1").autoStop(false));

        assertNotNull(poller.next());
        poller.close();

        assertEquals(0, stops.get());
    }

    private static SelfHostedClient client(okhttp3.Interceptor interceptor) {
        return new SelfHostedClient.Builder()
                .apiKey("test-key")
                .baseUrl("https://ark.example.com/api/v3")
                .httpClient(new OkHttpClient.Builder().addInterceptor(interceptor).build())
                .build();
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
