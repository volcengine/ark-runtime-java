// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.interceptor;

import static com.volcengine.ark.runtime.Const.*;
import static java.lang.Math.random;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RetryInterceptor implements Interceptor {

    private final int retryTimes;
    private static final double INITIAL_RETRY_DELAY = 0.5;
    private static final double MAX_RETRY_DELAY = 8.0;
    private static final Duration MAX_SERVER_RETRY_DELAY = Duration.ofSeconds(60);

    public RetryInterceptor(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        int requestRetryTimes = getRetryTimes(request);
        boolean shouldSendRetryCount = request.header(RETRY_COUNT_HEADER) == null;

        Response response = null;
        int retryCount = 0;
        boolean shouldRetry;
        IOException exception;
        do {
            if (response != null) {
                response.close();
                response = null;
            }
            exception = null;

            try {
                Request attempt = shouldSendRetryCount
                        ? request.newBuilder().header(RETRY_COUNT_HEADER, Integer.toString(retryCount)).build()
                        : request;
                response = chain.proceed(attempt);
                shouldRetry = shouldRetry(response);
            } catch (IOException e) {
                shouldRetry = true;
                exception = e;
            }

            if (!(shouldRetry && retryCount < requestRetryTimes && isRetryable(request))) {
                break;
            }

            try {
                Duration delay = retryDelay(response, retryCount);
                if (response != null) {
                    response.close();
                    response = null;
                }
                sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
            retryCount++;
        } while (true);

        if (response != null) {
            return response;
        }
        throw exception;
    }

    /**
     * Returns the legacy retry interval retained for source compatibility.
     * Production requests use the response-aware retry delay instead.
     *
     * @deprecated This compatibility method is not used by the interceptor retry loop.
     */
    @Deprecated
    public double retryInterval(int max, int remain) {
        double nbRetries = Math.min(max - remain, MAX_RETRY_DELAY / INITIAL_RETRY_DELAY);
        double sleepSeconds = Math.min(INITIAL_RETRY_DELAY * Math.pow(2.0, nbRetries), MAX_RETRY_DELAY);
        double jitter = 1 - 0.25 * random();
        return sleepSeconds * jitter;
    }

    protected void sleep(Duration duration) throws InterruptedException {
        long millis = duration.toMillis();
        int nanos = (int) (duration.minusMillis(millis).toNanos());
        Thread.sleep(millis, nanos);
    }

    private boolean shouldRetry(Response response) {
        String shouldRetry = response.header(SHOULD_RETRY_HEADER);
        if ("true".equalsIgnoreCase(shouldRetry)) {
            return true;
        }
        if ("false".equalsIgnoreCase(shouldRetry)) {
            return false;
        }
        int statusCode = response.code();
        return statusCode == 408 || statusCode == 409 || statusCode == 429 || statusCode >= 500;
    }

    private boolean isRetryable(Request request) {
        RequestBody body = request.body();
        return body == null || (!body.isOneShot() && !body.isDuplex());
    }

    private Duration retryDelay(Response response, int retryCount) {
        Duration retryAfter = parseRetryAfter(response);
        if (retryAfter != null && retryAfter.compareTo(Duration.ZERO) > 0
                && retryAfter.compareTo(MAX_SERVER_RETRY_DELAY) <= 0) {
            return retryAfter;
        }
        double seconds = Math.min(INITIAL_RETRY_DELAY * Math.pow(2.0, retryCount), MAX_RETRY_DELAY);
        double jitter = 1 - 0.25 * random();
        return Duration.ofNanos((long) (TimeUnit.SECONDS.toNanos(1) * seconds * jitter));
    }

    private Duration parseRetryAfter(Response response) {
        if (response == null) {
            return null;
        }
        Duration milliseconds = parseNumericDuration(response.header(RETRY_AFTER_MS), TimeUnit.MILLISECONDS);
        if (milliseconds != null) {
            return milliseconds;
        }
        String retryAfter = response.header(RETRY_AFTER);
        Duration seconds = parseNumericDuration(retryAfter, TimeUnit.SECONDS);
        if (seconds != null) {
            return seconds;
        }
        if (retryAfter == null) {
            return null;
        }
        try {
            Instant retryAt = ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Duration.between(Instant.now(), retryAt);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Duration parseNumericDuration(String value, TimeUnit unit) {
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value);
            double nanos = parsed * unit.toNanos(1);
            if (!Double.isFinite(parsed) || !Double.isFinite(nanos)
                    || nanos > Long.MAX_VALUE || nanos < Long.MIN_VALUE) {
                return null;
            }
            return Duration.ofNanos((long) nanos);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public int getRetryTimes(Request request) {
        String path = request.url().encodedPath();
        if (path.startsWith(BATCH_PATH_PREFIX)) {
            return MAX_RETRY_TIMES;
        }
        return retryTimes;
    }
}
