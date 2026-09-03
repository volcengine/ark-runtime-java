// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import com.volcengine.ark.runtime.models.environment.HeartbeatWorkResponse;
import com.volcengine.ark.runtime.models.environment.WorkItem;
import com.volcengine.ark.runtime.models.environment.WorkState;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnvironmentWorker implements AutoCloseable {
    private final SelfHostedClient api;
    private final Options options;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile SessionToolRunner activeRunner;
    private volatile Thread activeThread;

    public EnvironmentWorker(SelfHostedClient api, Options options) {
        if (api == null) {
            throw new IllegalArgumentException("api is required");
        }
        if (options == null) {
            throw new IllegalArgumentException("options is required");
        }
        if (options.workerId == null || options.workerId.isEmpty()) {
            options.workerId = defaultWorkerId();
        }
        this.api = api;
        this.options = options;
    }

    public void run() {
        if (options.environmentId == null || options.environmentId.isEmpty()) {
            throw new IllegalArgumentException("environment id is required");
        }
        activeThread = Thread.currentThread();
        WorkPoller poller = new WorkPoller(api, new WorkPoller.Options(options.environmentId)
                .workerId(options.workerId)
                .autoStop(false)
                .logger(options.logger));
        try {
            while (!closed.get()) {
                WorkItem item = poller.next();
                if (item == null) {
                    if (poller.error() != null) {
                        throw poller.error();
                    }
                    return;
                }
                try {
                    handleItem(claimedWorkFromItem(item));
                } catch (SessionToolRunner.IdleTimeoutException | SessionToolRunner.SessionTerminatedException ignored) {
                } catch (Exception e) {
                    options.logger.log(Level.WARNING, "handle work failed", e);
                }
            }
        } finally {
            poller.close();
            activeThread = null;
        }
    }

    public void handleItem(HandleItemOptions handleOptions) throws IOException {
        Thread previous = activeThread;
        activeThread = Thread.currentThread();
        try {
            handleItem(claimedWorkFromOptions(handleOptions));
        } catch (SessionToolRunner.IdleTimeoutException | SessionToolRunner.SessionTerminatedException ignored) {
        } finally {
            activeThread = previous;
        }
    }

    private void handleItem(ClaimedWork work) throws IOException {
        if (work.environmentId.isEmpty()) {
            work.environmentId = firstNonEmpty(options.environmentId, System.getenv("MA_ENVIRONMENT_ID"));
        }
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicReference<String> heartbeatCause = new AtomicReference<>("");
        Thread heartbeat = null;
        Initializer initializer = null;
        try {
            String workdir = workdir();
            Thread heartbeatThread = new Thread(
                    () -> heartbeatLoop(work, stop, heartbeatCause), "ma-self-host-heartbeat");
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();
            heartbeat = heartbeatThread;
            SessionSnapshot session = api.getSession(work.sessionId);
            if (closed.get() || stop.get()) {
                return;
            }
            if (session == null) {
                throw new IOException("session response is empty");
            }
            if (session.getId() == null || session.getId().isEmpty()) {
                session.setId(work.sessionId);
            }
            initializer = new Initializer(api, new Initializer.Options(workdir));
            initializer.setup(session);
            if (closed.get() || stop.get()) {
                return;
            }
            ToolContext toolContext = toolContext(workdir, stop);
            FileToolResultStore store = new FileToolResultStore(workdir, work.sessionId);
            SessionToolRunner runner = new SessionToolRunner(api, work.sessionId, new SessionToolRunner.Options()
                    .workId(work.id)
                    .tools(options.tools == null ? DefaultTools.create() : options.tools)
                    .toolContext(toolContext)
                    .customTools(options.customTools)
                    .resultStore(store)
                    .maxIdleMillis(options.maxIdleMillis)
                    .stopSignal(() -> closed.get() || stop.get()));
            activeRunner = runner;
            try {
                runner.run();
            } finally {
                runner.close();
                activeRunner = null;
            }
        } finally {
            if (initializer != null) {
                try {
                    initializer.cleanup();
                } catch (IOException error) {
                    options.logger.log(Level.WARNING, "cleanup session skills failed", error);
                }
            }
            stop.set(true);
            if (heartbeat != null) {
                try {
                    heartbeat.join(SelfHostedConstants.DEFAULT_HEARTBEAT_MILLIS + 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            String cause = heartbeatCause.get();
            if (shouldStopItem(cause)) {
                try {
                    api.stopWork(work.environmentId, work.id, true);
                } catch (RuntimeException e) {
                    if (!isResolvedStatus(e)) {
                        options.logger.log(Level.WARNING, "stop work failed", e);
                    }
                }
            } else {
                options.logger.info(
                        "skip stop work after heartbeat ownership became uncertain cause=" + cause);
            }
        }
    }

    private void heartbeatLoop(ClaimedWork work, AtomicBoolean stop, AtomicReference<String> cause) {
        long interval = Math.max(1000L, SelfHostedConstants.DEFAULT_HEARTBEAT_MILLIS / 2L);
        long ttl = SelfHostedConstants.DEFAULT_HEARTBEAT_MILLIS;
        String last = work.latestHeartbeatAt;
        if (last == null || last.isEmpty()) {
            last = SelfHostedConstants.EXPECTED_LAST_HEARTBEAT_NO_HEARTBEAT;
        }
        long lastSuccess = System.currentTimeMillis();
        while (!stop.get()) {
            try {
                HeartbeatWorkResponse response = api.heartbeatWork(
                        work.environmentId,
                        work.id,
                        last,
                        (int) (ttl / 1000L));
                if (response == null) {
                    if (System.currentTimeMillis() - lastSuccess > ttl) {
                        cause.set("heartbeat_lost");
                        stop.set(true);
                        return;
                    }
                    options.logger.warning(
                            "heartbeat empty response work_id=" + work.id
                                    + " session_id=" + work.sessionId);
                    sleep(interval, stop);
                    continue;
                }
                if (WorkState.STOPPING.equals(response.getState())
                        || WorkState.STOPPED.equals(response.getState())) {
                    cause.set("stop_requested");
                    stop.set(true);
                    return;
                }
                if (Boolean.FALSE.equals(response.getLeaseExtended())) {
                    cause.set("lease_not_extended");
                    stop.set(true);
                    return;
                }
                lastSuccess = System.currentTimeMillis();
                if (response.getLastHeartbeat() != null && !response.getLastHeartbeat().isEmpty()) {
                    last = response.getLastHeartbeat();
                }
                Long ttlSeconds = response.getTtlSeconds();
                if (ttlSeconds != null && ttlSeconds > 0) {
                    ttl = ttlSeconds * 1000L;
                    interval = Math.max(1000L, Math.min(ttl / 2, SelfHostedConstants.DEFAULT_HEARTBEAT_MILLIS));
                }
            } catch (RuntimeException e) {
                if (WorkerAPIException.isStatus(e, 412)) {
                    cause.set("lease_lost");
                    stop.set(true);
                    return;
                }
                if (WorkerAPIException.isFatal4xx(e)) {
                    cause.set("heartbeat_permanent_failure");
                    stop.set(true);
                    return;
                }
                if (System.currentTimeMillis() - lastSuccess > ttl) {
                    cause.set("heartbeat_lost");
                    stop.set(true);
                    return;
                }
                options.logger.log(
                        Level.WARNING,
                        "heartbeat failed work_id=" + work.id
                                + " session_id=" + work.sessionId
                                + " since_last_success_ms=" + (System.currentTimeMillis() - lastSuccess)
                                + " ttl_ms=" + ttl,
                        e);
            }
            sleep(interval, stop);
        }
    }

    private ToolContext toolContext(String workdir, AtomicBoolean workStop) {
        ToolContext context = options.toolContext == null ? new ToolContext(workdir) : options.toolContext;
        ToolContext copy = new ToolContext(workdir);
        if (context.hasExplicitEnv()) {
            copy.setEnv(new LinkedHashMap<>(context.getEnv()));
        }
        copy.setUnrestrictedPaths(options.unrestrictedPaths || context.isUnrestrictedPaths());
        copy.setToolTimeoutMillis(options.toolTimeoutMillis > 0L
                ? options.toolTimeoutMillis
                : context.getToolTimeoutMillis());
        copy.setCancelled(() -> closed.get() || workStop.get());
        return copy;
    }

    private String workdir() throws IOException {
        Path root = Paths.get(options.workdir == null || options.workdir.isEmpty() ? "." : options.workdir)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(root);
        return root.toString();
    }

    private ClaimedWork claimedWorkFromOptions(HandleItemOptions opts) {
        String workId = firstNonEmpty(opts.workId, System.getenv("MA_WORK_ID"));
        String environmentId = firstNonEmpty(opts.environmentId, System.getenv("MA_ENVIRONMENT_ID"));
        String sessionId = firstNonEmpty(opts.sessionId, System.getenv("MA_SESSION_ID"));
        String latestHeartbeat = firstNonEmpty(opts.latestHeartbeatAt, System.getenv("MA_LATEST_HEARTBEAT_AT"));
        if (workId.isEmpty()) {
            throw new IllegalArgumentException("work id is required");
        }
        if (environmentId.isEmpty()) {
            throw new IllegalArgumentException("environment id is required");
        }
        if (sessionId.isEmpty()) {
            throw new IllegalArgumentException("session id is required");
        }
        return new ClaimedWork(workId, environmentId, sessionId, latestHeartbeat);
    }

    private static ClaimedWork claimedWorkFromItem(WorkItem item) {
        if (item == null || item.getId() == null || item.getId().isEmpty()) {
            throw new IllegalArgumentException("work item id must not be empty");
        }
        String sessionId = WorkItems.sessionId(item);
        if (sessionId.isEmpty()) {
            throw new IllegalArgumentException("work item does not contain session id");
        }
        return new ClaimedWork(
                item.getId(),
                item.getEnvironmentId() == null ? "" : item.getEnvironmentId(),
                sessionId,
                WorkItems.latestHeartbeat(item));
    }

    private static void sleep(long millis, AtomicBoolean stop) {
        long deadline = System.currentTimeMillis() + Math.max(1L, millis);
        while (!stop.get() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(Math.min(500L, deadline - System.currentTimeMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stop.set(true);
                return;
            }
        }
    }

    static String defaultWorkerId() {
        try {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            String pid = runtimeName == null ? "" : runtimeName.split("@")[0];
            return InetAddress.getLocalHost().getHostName() + "-" + pid;
        } catch (Throwable ignored) {
            return "worker-" + System.currentTimeMillis();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        SessionToolRunner runner = activeRunner;
        if (runner != null) {
            runner.close();
        }
        Thread thread = activeThread;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    private static boolean isResolvedStatus(Throwable error) {
        return WorkerAPIException.isStatus(error, 404)
                || WorkerAPIException.isStatus(error, 409)
                || WorkerAPIException.isStatus(error, 412);
    }

    private static boolean shouldStopItem(String heartbeatCause) {
        return !"lease_lost".equals(heartbeatCause)
                && !"lease_not_extended".equals(heartbeatCause)
                && !"heartbeat_lost".equals(heartbeatCause)
                && !"heartbeat_permanent_failure".equals(heartbeatCause);
    }

    private static String firstNonEmpty(String first, String second) {
        return first != null && !first.isEmpty() ? first : (second == null ? "" : second);
    }

    private static final class ClaimedWork {
        private final String id;
        private String environmentId;
        private final String sessionId;
        private final String latestHeartbeatAt;

        private ClaimedWork(String id, String environmentId, String sessionId, String latestHeartbeatAt) {
            this.id = id;
            this.environmentId = environmentId;
            this.sessionId = sessionId;
            this.latestHeartbeatAt = latestHeartbeatAt;
        }
    }

    public static class HandleItemOptions {
        private String workId = "";
        private String environmentId = "";
        private String sessionId = "";
        private String latestHeartbeatAt = "";

        public HandleItemOptions workId(String workId) {
            this.workId = workId;
            return this;
        }

        public HandleItemOptions environmentId(String environmentId) {
            this.environmentId = environmentId;
            return this;
        }

        public HandleItemOptions sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public HandleItemOptions latestHeartbeatAt(String latestHeartbeatAt) {
            this.latestHeartbeatAt = latestHeartbeatAt;
            return this;
        }
    }

    public static class Options {
        private String environmentId = "";
        private String workerId = "";
        private String workdir = ".";
        private boolean unrestrictedPaths;
        private ToolContext toolContext;
        private long toolTimeoutMillis;
        private ToolSet tools;
        private long maxIdleMillis = SelfHostedConstants.DEFAULT_MAX_IDLE_MILLIS;
        private Map<String, Tool> customTools = new LinkedHashMap<>();
        private Logger logger = Logger.getLogger("arkruntime.selfhosted.environment_worker");

        public Options environmentId(String environmentId) {
            this.environmentId = environmentId;
            return this;
        }

        public Options workerId(String workerId) {
            this.workerId = workerId;
            return this;
        }

        public Options workdir(String workdir) {
            this.workdir = workdir;
            return this;
        }

        public Options unrestrictedPaths(boolean unrestrictedPaths) {
            this.unrestrictedPaths = unrestrictedPaths;
            return this;
        }

        public Options toolContext(ToolContext toolContext) {
            this.toolContext = toolContext;
            return this;
        }

        public Options toolTimeoutMillis(long toolTimeoutMillis) {
            this.toolTimeoutMillis = toolTimeoutMillis;
            return this;
        }

        public Options tools(ToolSet tools) {
            this.tools = tools;
            return this;
        }

        public Options maxIdleMillis(long maxIdleMillis) {
            this.maxIdleMillis = maxIdleMillis;
            return this;
        }

        public Options customTools(Map<String, Tool> customTools) {
            this.customTools = customTools == null ? new LinkedHashMap<>() : customTools;
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
