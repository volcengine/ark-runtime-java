// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import com.volcengine.ark.runtime.models.environment.WorkItem;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorkPoller implements AutoCloseable {
    private static final long POLL_BACKOFF_CAP_MILLIS = 60000L;

    private final SelfHostedClient api;
    private final Options options;
    private final Random random = new Random();
    private WorkItem current;
    private RuntimeException error;
    private volatile boolean closed;
    private Runnable pendingStop;
    private int failures;
    private int discards;

    public WorkPoller(SelfHostedClient api, Options options) {
        if (api == null) {
            throw new IllegalArgumentException("api is required");
        }
        if (options == null || options.environmentId == null || options.environmentId.isEmpty()) {
            throw new IllegalArgumentException("environment id is required");
        }
        if (options.workerId == null || options.workerId.isEmpty()) {
            options.workerId = EnvironmentWorker.defaultWorkerId();
        }
        this.api = api;
        this.options = options;
    }

    public WorkItem next() {
        runPendingStop();
        if (closed) {
            return null;
        }
        while (!closed) {
            WorkItem item;
            try {
                item = api.pollWork(options.environmentId, options.workerId, options.blockMs, options.reclaimOlderThanMs);
            } catch (RuntimeException e) {
                if (WorkerAPIException.isFatal4xx(e)) {
                    error = e;
                    return null;
                }
                failures++;
                long sleepMillis = jitter(backoff(failures) / 2, backoff(failures));
                options.logger.warning(
                        "poll work failed err=" + e + " retry_in_ms=" + sleepMillis);
                sleep(sleepMillis);
                continue;
            }
            failures = 0;
            if (item == null || item.getId().isEmpty()) {
                if (options.drain) {
                    return null;
                }
                sleep(jitter(1000L, 3000L));
                continue;
            }
            if (item.getEnvironmentId() == null || item.getEnvironmentId().isEmpty()) {
                item.setEnvironmentId(options.environmentId);
            }
            if (WorkItems.sessionId(item).isEmpty()) {
                options.logger.warning(
                        "discard invalid work work_id=" + item.getId() + " reason=missing session id");
                discardInvalidWork(item);
                continue;
            }
            try {
                api.ackWork(item.getEnvironmentId(), item.getId(), options.workerId);
            } catch (RuntimeException e) {
                options.logger.log(Level.WARNING, "ack work failed", e);
                if (isResolvedStatus(e)) {
                    continue;
                }
                if (WorkerAPIException.isFatal4xx(e)) {
                    error = e;
                    return null;
                }
                backoffDiscard();
                continue;
            }
            current = item;
            if (options.autoStop) {
                pendingStop = () -> stopItem(item, false);
            }
            discards = 0;
            options.logger.info("claimed work work_id=" + item.getId() + " session_id=" + WorkItems.sessionId(item));
            return item;
        }
        return null;
    }

    public WorkItem current() {
        return current;
    }

    public RuntimeException error() {
        return error;
    }

    @Override
    public void close() {
        closed = true;
        runPendingStop();
    }

    private void runPendingStop() {
        Runnable stop = pendingStop;
        pendingStop = null;
        current = null;
        if (stop != null) {
            stop.run();
        }
    }

    private void discardInvalidWork(WorkItem item) {
        try {
            api.ackWork(item.getEnvironmentId(), item.getId(), options.workerId);
        } catch (RuntimeException e) {
            options.logger.log(Level.WARNING, "ack invalid work failed", e);
            return;
        }
        stopItem(item, true);
        backoffDiscard();
    }

    private void stopItem(WorkItem item, boolean force) {
        try {
            api.stopWork(item.getEnvironmentId(), item.getId(), force);
        } catch (RuntimeException e) {
            if (!isResolvedStatus(e)) {
                options.logger.log(Level.WARNING, "stop work failed", e);
            }
        }
    }

    private void backoffDiscard() {
        discards++;
        sleep(jitter(backoff(discards) / 2, backoff(discards)));
    }

    private long backoff(int count) {
        if (count > 6) {
            return POLL_BACKOFF_CAP_MILLIS;
        }
        long value = 1L << Math.max(count - 1, 0);
        return Math.min(POLL_BACKOFF_CAP_MILLIS, value * 1000L);
    }

    private static boolean isResolvedStatus(Throwable error) {
        return WorkerAPIException.isStatus(error, 404)
                || WorkerAPIException.isStatus(error, 409)
                || WorkerAPIException.isStatus(error, 412);
    }

    private long jitter(long low, long high) {
        if (high <= low) {
            return Math.max(0L, high);
        }
        return low + Math.abs(random.nextLong()) % (high - low);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(1L, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closed = true;
        }
    }

    public static class Options {
        private String environmentId;
        private String workerId = "";
        private int blockMs = SelfHostedConstants.DEFAULT_POLL_BLOCK_MILLIS;
        private int reclaimOlderThanMs;
        private boolean drain;
        private boolean autoStop = true;
        private Logger logger = Logger.getLogger("arkruntime.selfhosted.work_poller");

        public Options(String environmentId) {
            this.environmentId = environmentId;
        }

        public Options workerId(String workerId) {
            this.workerId = workerId;
            return this;
        }

        public Options blockMs(int blockMs) {
            this.blockMs = blockMs;
            return this;
        }

        public Options reclaimOlderThanMs(int reclaimOlderThanMs) {
            this.reclaimOlderThanMs = reclaimOlderThanMs;
            return this;
        }

        public Options drain(boolean drain) {
            this.drain = drain;
            return this;
        }

        public Options autoStop(boolean autoStop) {
            this.autoStop = autoStop;
            return this;
        }

        public Options logger(Logger logger) {
            if (logger != null) {
                this.logger = logger;
            }
            return this;
        }
    }
}
