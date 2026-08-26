// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SessionToolRunner {
    private static final int STREAM_QUEUE_SIZE = 256;
    private static final Logger LOGGER = Logger.getLogger(SessionToolRunner.class.getName());
    private final SelfHostedClient api;
    private final String sessionId;
    private final Options options;
    private final State state = new State();
    private final List<ToolCallResult> results = new ArrayList<>();
    private final Random random = new Random();
    private volatile boolean closed;
    private volatile EventStream activeStream;

    public SessionToolRunner(SelfHostedClient api, String sessionId, Options options) {
        if (api == null) {
            throw new IllegalArgumentException("api is required");
        }
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("session id must not be empty");
        }
        if (options == null || options.tools == null) {
            throw new IllegalArgumentException("session tool runner tools must not be empty");
        }
        if (options.toolContext == null) {
            throw new IllegalArgumentException("session tool runner tool context must not be empty");
        }
        this.api = api;
        this.sessionId = sessionId;
        this.options = options;
    }

    public List<ToolCallResult> run() throws IOException {
        if (options.resultStore != null) {
            FileToolResultStore.RecoverResult recovered = options.resultStore.recover();
            state.pendingResults.putAll(recovered.getPending());
            state.processed.putAll(recovered.getProcessed());
            state.answered.putAll(recovered.getProcessed());
        }
        if (options.preferStream) {
            try {
                consumeStreamLoop();
                return results;
            } catch (StreamUnsupportedException ignored) {
            }
        }
        consumeList();
        return results;
    }

    private void consumeStreamLoop() throws IOException {
        long backoff = 500L;
        while (!isClosed()) {
            LinkedBlockingQueue<Object> events = new LinkedBlockingQueue<>(STREAM_QUEUE_SIZE);
            AtomicReference<EventStream> streamRef = new AtomicReference<>();
            Thread pump = new Thread(() -> pumpStream(events, streamRef), "ma-self-host-event-stream");
            pump.setDaemon(true);
            pump.start();
            try {
                reconcile(true);
                while (!isClosed() && (pump.isAlive() || !events.isEmpty())) {
                    flushResults();
                    if (idleExpired()) {
                        throw new IdleTimeoutException();
                    }
                    Object item;
                    try {
                        item = events.poll(nextIdleWait(500L), TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        closed = true;
                        return;
                    }
                    if (item == null) {
                        continue;
                    }
                    if (item instanceof Throwable) {
                        if (WorkerAPIException.isFatal4xx((Throwable) item)) {
                            throw (RuntimeException) item;
                        }
                        break;
                    }
                    handleStreamEvent((Event) item);
                }
            } finally {
                closeStream(streamRef.get());
            }
            sleepOrIdle(jitter(backoff));
            backoff = Math.min(backoff * 2, 10000L);
        }
    }

    private void pumpStream(LinkedBlockingQueue<Object> events, AtomicReference<EventStream> streamRef) {
        try (EventStream stream = api.openEventStream(sessionId)) {
            streamRef.set(stream);
            activeStream = stream;
            if (isClosed()) {
                return;
            }
            while (!isClosed()) {
                Event event = stream.next();
                if (event == null) {
                    return;
                }
                while (!isClosed() && !events.offer(event, 100L, TimeUnit.MILLISECONDS)) {
                    // Apply backpressure while the owner processes tool events.
                }
            }
        } catch (Throwable t) {
            while (!isClosed()) {
                try {
                    if (events.offer(t, 100L, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } finally {
            activeStream = null;
        }
    }

    private void closeStream(EventStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private void reconcile(boolean reconcile) throws IOException {
        long backoff = 500L;
        while (!isClosed()) {
            try {
                reconcileOnce(reconcile);
                return;
            } catch (RuntimeException error) {
                if (WorkerAPIException.isFatal4xx(error)) {
                    throw error;
                }
                sleepOrIdle(jitter(backoff));
                backoff = Math.min(backoff * 2L, 10000L);
            }
        }
    }

    private void reconcileOnce(boolean reconcile) throws IOException {
        String page = "";
        List<Event> events = new ArrayList<>();
        while (!isClosed()) {
            ListEventsResponse resp = api.listEvents(
                    sessionId,
                    "",
                    page,
                    Math.min(Math.max(options.eventLimit, 1), 1000),
                    SelfHostedConstants.EVENT_LIST_ORDER_ASC,
                    null);
            events.addAll(resp.getEvents());
            if (resp.getNextPage().isEmpty()) {
                break;
            }
            page = resp.getNextPage();
        }
        processListedEvents(events, reconcile);
    }

    private void consumeList() throws IOException {
        while (!isClosed()) {
            flushResults();
            reconcile(false);
            if (idleExpired()) {
                throw new IdleTimeoutException();
            }
            sleepOrIdle(options.eventPollIntervalMillis);
        }
    }

    public void close() {
        closed = true;
        closeStream(activeStream);
    }

    public List<ToolCallResult> getResults() {
        return results;
    }

    private void processListedEvents(List<Event> events, boolean reconcile) throws IOException {
        List<Event> pending = new ArrayList<>();
        Map<String, Boolean> pendingIds = new LinkedHashMap<>();
        boolean touchedIdle = false;
        boolean lastWasEndTurn = false;
        for (Event event : events) {
            boolean seenNow = markEventSeen(event);
            if (!reconcile && !seenNow) {
                continue;
            }
            if (seenNow && !SelfHostedConstants.EVENT_TYPE_USER_TOOL_CONFIRMATION.equals(event.getType())) {
                touchedIdle = true;
                lastWasEndTurn = SelfHostedConstants.EVENT_TYPE_SESSION_STATUS_IDLE.equals(event.getType())
                        && SelfHostedConstants.SESSION_STOP_REASON_END_TURN.equals(event.stopReasonType());
            }
            String type = event.getType();
            if (SelfHostedConstants.EVENT_TYPE_USER_TOOL_CONFIRMATION.equals(type)) {
                recordConfirmation(event);
            } else if (SelfHostedConstants.EVENT_TYPE_USER_TOOL_RESULT.equals(type)
                    || SelfHostedConstants.EVENT_TYPE_USER_CUSTOM_TOOL_RESULT.equals(type)) {
                markAnswered(event.resultCallId());
            } else if (SelfHostedConstants.EVENT_TYPE_AGENT_TOOL_USE.equals(type)
                    || SelfHostedConstants.EVENT_TYPE_AGENT_CUSTOM_TOOL_USE.equals(type)) {
                String callId = event.callId();
                if (!callId.isEmpty() && !pendingIds.containsKey(callId)) {
                    pending.add(event);
                    pendingIds.put(callId, Boolean.TRUE);
                }
            } else if (SelfHostedConstants.EVENT_TYPE_SESSION_STATUS_TERMINATED.equals(type)
                    || SelfHostedConstants.EVENT_TYPE_SESSION_DELETED.equals(type)) {
                throw new SessionTerminatedException();
            }
        }
        if (touchedIdle) {
            disarmIdle();
        }
        for (Event event : pending) {
            if (!isAnswered(event.callId())) {
                handleToolUse(event, SelfHostedConstants.EVENT_TYPE_AGENT_CUSTOM_TOOL_USE.equals(event.getType()));
            }
        }
        releaseConfirmedToolUses();
        if (touchedIdle && lastWasEndTurn) {
            if (hasUnblockedOutstandingTool(pending)) {
                disarmIdle();
            } else {
                armIdle();
            }
        }
    }

    private void noteIdleEvent(Event event) {
        if (SelfHostedConstants.EVENT_TYPE_USER_TOOL_CONFIRMATION.equals(event.getType())) {
            return;
        }
        if (SelfHostedConstants.EVENT_TYPE_SESSION_STATUS_IDLE.equals(event.getType())
                && SelfHostedConstants.SESSION_STOP_REASON_END_TURN.equals(event.stopReasonType())) {
            armIdle();
            return;
        }
        disarmIdle();
    }

    private void handleStreamEvent(Event event) throws IOException {
        if (!markEventSeen(event)) {
            return;
        }
        noteIdleEvent(event);
        handleEvent(event);
    }

    private void handleEvent(Event event) throws IOException {
        String type = event.getType();
        if (SelfHostedConstants.EVENT_TYPE_USER_TOOL_CONFIRMATION.equals(type)) {
            recordConfirmation(event);
            releaseConfirmedToolUses();
        } else if (SelfHostedConstants.EVENT_TYPE_USER_TOOL_RESULT.equals(type)
                || SelfHostedConstants.EVENT_TYPE_USER_CUSTOM_TOOL_RESULT.equals(type)) {
            markAnswered(event.resultCallId());
        } else if (SelfHostedConstants.EVENT_TYPE_AGENT_TOOL_USE.equals(type)
                || SelfHostedConstants.EVENT_TYPE_AGENT_CUSTOM_TOOL_USE.equals(type)) {
            handleToolUse(event, SelfHostedConstants.EVENT_TYPE_AGENT_CUSTOM_TOOL_USE.equals(type));
        } else if (SelfHostedConstants.EVENT_TYPE_SESSION_STATUS_TERMINATED.equals(type)
                || SelfHostedConstants.EVENT_TYPE_SESSION_DELETED.equals(type)) {
            throw new SessionTerminatedException();
        }
    }

    private void handleToolUse(Event event, boolean custom) throws IOException {
        String callId = event.callId();
        if (callId.isEmpty() || isAnswered(callId)) {
            return;
        }
        Event pending = state.pendingResults.get(callId);
        if (pending != null) {
            sendResult(callId, event, custom, "", pending);
            return;
        }
        if (!ownsTool(event, custom)) {
            state.externalTools.put(callId, event);
            maybeArmPendingIdle();
            results.add(new ToolCallResult(callId, event.getName(), custom, "", false, event, null));
            return;
        }
        PermissionDecision decision = permissionAllows(event, custom, callId);
        if (!decision.allowed) {
            results.add(new ToolCallResult(callId, event.getName(), custom, decision.confirmation, false, event, null));
            return;
        }
        if (options.resultStore != null) {
            FileToolResultStore.ToolCallStoreDecision storeDecision = options.resultStore.begin(callId, event);
            if (storeDecision.isSent()) {
                markAnswered(callId);
                return;
            }
            if (storeDecision.getResult() != null) {
                state.pendingResults.put(callId, storeDecision.getResult());
                sendResult(callId, event, custom, "", storeDecision.getResult());
                return;
            }
        }
        ToolResult result = executeTool(event, custom);
        Event out = custom
                ? Event.newUserCustomToolResultEvent(callId, result.getContent(), result.isError(), event.getSessionThreadId())
                : Event.newUserToolResultEvent(callId, result.getContent(), result.isError(), event.getSessionThreadId());
        if (options.resultStore != null) {
            try {
                options.resultStore.saveResult(callId, out);
            } catch (IOException error) {
                LOGGER.log(Level.WARNING, "persist tool result failed tool_use_id=" + callId, error);
            }
            state.pendingResults.put(callId, out);
        }
        sendResult(callId, event, custom, decision.confirmation, out);
    }

    private ToolResult executeTool(Event event, boolean custom) {
        if (custom) {
            try {
                return options.customTools.get(event.getName()).execute(event.getInput(), options.toolContext);
            } catch (RuntimeException error) {
                return ToolResult.error(error.getMessage());
            }
        }
        return options.tools.execute(event.getName(), event.getInput(), options.toolContext);
    }

    private void sendResult(String callId, Event source, boolean custom, String confirmation, Event out) throws IOException {
        boolean posted = retrySendEvent(out);
        if (posted) {
            markAnswered(callId);
            if (options.resultStore != null) {
                try {
                    options.resultStore.markSent(callId);
                } catch (IOException error) {
                    LOGGER.log(
                            Level.WARNING,
                            "mark tool result sent failed tool_use_id=" + callId + " event_id=" + out.getId(),
                            error);
                }
            }
        } else if (options.resultStore != null) {
            state.pendingResults.put(callId, out);
        }
        results.add(new ToolCallResult(callId, source.getName(), custom, confirmation, posted, source, out));
    }

    private boolean retrySendEvent(Event event) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                api.sendEvent(sessionId, event);
                return true;
            } catch (RuntimeException e) {
                if (WorkerAPIException.isFatal4xx(e) || isClosed()) {
                    return false;
                }
                if (attempt < 2) {
                    sleep((attempt + 1) * 1000L);
                }
            }
        }
        return false;
    }

    private void flushResults() throws IOException {
        for (Map.Entry<String, Event> entry : new ArrayList<>(state.pendingResults.entrySet())) {
            if (retrySendEvent(entry.getValue())) {
                markAnswered(entry.getKey());
                if (options.resultStore != null) {
                    try {
                        options.resultStore.markSent(entry.getKey());
                    } catch (IOException error) {
                        LOGGER.log(
                                Level.WARNING,
                                "mark pending tool result sent failed tool_use_id=" + entry.getKey()
                                        + " event_id=" + entry.getValue().getId(),
                                error);
                    }
                }
            }
        }
        maybeArmPendingIdle();
    }

    private boolean ownsTool(Event event, boolean custom) {
        return custom ? options.customTools.containsKey(event.getName()) : options.tools.has(event.getName());
    }

    private PermissionDecision permissionAllows(Event event, boolean custom, String callId) {
        if (custom) {
            return new PermissionDecision("", true);
        }
        String permission = event.getEvaluatedPermission();
        if (permission == null || permission.isEmpty() || SelfHostedConstants.PERMISSION_ALLOW.equals(permission)) {
            return new PermissionDecision("", true);
        }
        if (SelfHostedConstants.PERMISSION_ASK.equals(permission)) {
            Event confirmation = state.confirmations.get(callId);
            if (confirmation == null) {
                state.pendingAsk.put(callId, event);
                return new PermissionDecision("", false);
            }
            if (SelfHostedConstants.CONFIRMATION_ALLOW.equals(confirmation.getResult())) {
                return new PermissionDecision(SelfHostedConstants.CONFIRMATION_ALLOW, true);
            }
            markAnswered(callId);
            return new PermissionDecision(SelfHostedConstants.CONFIRMATION_DENY, false);
        }
        if (SelfHostedConstants.PERMISSION_DENY.equals(permission)) {
            markAnswered(callId);
            return new PermissionDecision(SelfHostedConstants.CONFIRMATION_DENY, false);
        }
        state.pendingAsk.put(callId, event);
        return new PermissionDecision("", false);
    }

    private boolean markEventSeen(Event event) {
        String key = event.getId().isEmpty() ? event.callId() : event.getId();
        if (key.isEmpty()) {
            return true;
        }
        if (state.seen.containsKey(key)) {
            return false;
        }
        state.seen.put(key, Boolean.TRUE);
        return true;
    }

    private void markAnswered(String callId) {
        if (callId == null || callId.isEmpty()) {
            return;
        }
        state.answered.put(callId, Boolean.TRUE);
        state.processed.put(callId, Boolean.TRUE);
        state.pendingResults.remove(callId);
        state.pendingAsk.remove(callId);
        state.externalTools.remove(callId);
        maybeArmPendingIdle();
    }

    private boolean isAnswered(String callId) {
        return callId != null && state.answered.containsKey(callId);
    }

    private void recordConfirmation(Event event) {
        String callId = event.resultCallId().isEmpty() ? event.callId() : event.resultCallId();
        if (!callId.isEmpty() && !isAnswered(callId)) {
            state.confirmations.put(callId, event);
        }
    }

    private void releaseConfirmedToolUses() throws IOException {
        for (Map.Entry<String, Event> entry : new ArrayList<>(state.pendingAsk.entrySet())) {
            if (state.confirmations.containsKey(entry.getKey())) {
                state.pendingAsk.remove(entry.getKey());
                handleToolUse(entry.getValue(), SelfHostedConstants.EVENT_TYPE_AGENT_CUSTOM_TOOL_USE.equals(entry.getValue().getType()));
            }
        }
    }

    private boolean hasUnblockedOutstandingTool(List<Event> pending) {
        for (Event event : pending) {
            String callId = event.callId();
            if (callId.isEmpty() || isAnswered(callId)) {
                continue;
            }
            if (state.pendingAsk.containsKey(callId) || state.pendingResults.containsKey(callId)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private void armIdle() {
        if (options.maxIdleMillis <= 0) {
            return;
        }
        if (hasIdleBlockers()) {
            state.idleArmPending = true;
            state.idleArmedAt = 0L;
            return;
        }
        state.idleArmPending = false;
        state.idleArmedAt = System.currentTimeMillis();
    }

    private void disarmIdle() {
        state.idleArmPending = false;
        state.idleArmedAt = 0L;
    }

    private void maybeArmPendingIdle() {
        if (state.idleArmPending && !hasIdleBlockers()) {
            state.idleArmPending = false;
            state.idleArmedAt = System.currentTimeMillis();
        }
    }

    private boolean hasIdleBlockers() {
        return !state.pendingAsk.isEmpty() || !state.pendingResults.isEmpty() || !state.externalTools.isEmpty();
    }

    private boolean idleExpired() {
        return options.maxIdleMillis > 0
                && state.idleArmedAt > 0
                && System.currentTimeMillis() - state.idleArmedAt >= options.maxIdleMillis;
    }

    private void sleepOrIdle(long millis) {
        long deadline = System.currentTimeMillis() + Math.max(millis, 0L);
        while (!isClosed()) {
            if (idleExpired()) {
                throw new IdleTimeoutException();
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return;
            }
            long wait = Math.min(remaining, nextIdleWait(500L));
            sleep(wait);
        }
    }

    private long nextIdleWait(long fallback) {
        if (state.idleArmedAt <= 0 || options.maxIdleMillis <= 0) {
            return fallback;
        }
        long remaining = options.maxIdleMillis - (System.currentTimeMillis() - state.idleArmedAt);
        return Math.max(1L, Math.min(fallback, remaining));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(1L, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closed = true;
        }
    }

    private boolean isClosed() {
        return closed || (options.stopSignal != null && options.stopSignal.getAsBoolean());
    }

    private long jitter(long millis) {
        if (millis <= 1L) {
            return Math.max(millis, 0L);
        }
        long half = millis / 2L;
        return half + Math.abs(random.nextLong()) % Math.max(1L, millis - half);
    }

    private static class State {
        String page = "";
        Map<String, Boolean> processed = new LinkedHashMap<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        Map<String, Boolean> answered = new LinkedHashMap<>();
        Map<String, Event> pendingResults = new LinkedHashMap<>();
        Map<String, Event> pendingAsk = new LinkedHashMap<>();
        Map<String, Event> confirmations = new LinkedHashMap<>();
        Map<String, Event> externalTools = new LinkedHashMap<>();
        long idleArmedAt;
        boolean idleArmPending;
    }

    private static class PermissionDecision {
        final String confirmation;
        final boolean allowed;

        PermissionDecision(String confirmation, boolean allowed) {
            this.confirmation = confirmation;
            this.allowed = allowed;
        }
    }

    public static class Options {
        private String workId = "";
        private ToolSet tools;
        private ToolContext toolContext;
        private Map<String, Tool> customTools = new LinkedHashMap<>();
        private FileToolResultStore resultStore;
        private String eventPage = "";
        private long eventPollIntervalMillis = 500L;
        private int eventLimit = 100;
        private long maxIdleMillis = SelfHostedConstants.DEFAULT_MAX_IDLE_MILLIS;
        private boolean preferStream = true;
        private BooleanSupplier stopSignal = () -> false;

        public Options tools(ToolSet tools) {
            this.tools = tools;
            return this;
        }

        public Options toolContext(ToolContext toolContext) {
            this.toolContext = toolContext;
            return this;
        }

        public Options customTools(Map<String, Tool> customTools) {
            this.customTools = customTools == null ? new LinkedHashMap<String, Tool>() : customTools;
            return this;
        }

        public Options resultStore(FileToolResultStore resultStore) {
            this.resultStore = resultStore;
            return this;
        }

        public Options workId(String workId) {
            this.workId = workId;
            return this;
        }

        public Options maxIdleMillis(long maxIdleMillis) {
            this.maxIdleMillis = maxIdleMillis;
            return this;
        }

        public Options eventPollIntervalMillis(long eventPollIntervalMillis) {
            this.eventPollIntervalMillis = eventPollIntervalMillis;
            return this;
        }

        public Options eventLimit(int eventLimit) {
            this.eventLimit = eventLimit;
            return this;
        }

        public Options preferStream(boolean preferStream) {
            this.preferStream = preferStream;
            return this;
        }

        public Options stopSignal(BooleanSupplier stopSignal) {
            this.stopSignal = stopSignal == null ? () -> false : stopSignal;
            return this;
        }
    }

    public static class IdleTimeoutException extends RuntimeException {
    }

    public static class SessionTerminatedException extends RuntimeException {
    }

    public static class StreamUnsupportedException extends RuntimeException {
    }
}
