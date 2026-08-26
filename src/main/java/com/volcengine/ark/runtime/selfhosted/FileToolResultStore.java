// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.service.ArkService;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileToolResultStore {
    private static final String STATE_STARTED = "started";
    private static final String STATE_RESULT = "result";
    private static final String STATE_SENT = "sent";
    private static final ObjectMapper MAPPER = ArkService.defaultObjectMapper();

    private final Path dir;

    public FileToolResultStore(String workdir) throws IOException {
        if (workdir == null || workdir.isEmpty()) {
            throw new IllegalArgumentException("workdir must not be empty");
        }
        this.dir = Paths.get(workdir, ".ma_self_host_worker", "tool_ledger");
        Files.createDirectories(this.dir);
    }

    public RecoverResult recover() throws IOException {
        Map<String, Event> pending = new LinkedHashMap<>();
        Map<String, Boolean> processed = new LinkedHashMap<>();
        try (DirectoryStream<Path> stale = Files.newDirectoryStream(dir, ".tool-result-*.tmp")) {
            for (Path path : stale) {
                Files.deleteIfExists(path);
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path path : stream) {
                Map<String, Object> record = readPath(path);
                String callId = stringValue(record.get("call_id"));
                String state = stringValue(record.get("state"));
                if (STATE_SENT.equals(state)) {
                    processed.put(callId, Boolean.TRUE);
                } else if (STATE_RESULT.equals(state)) {
                    pending.put(callId, Event.fromMap(asMap(record.get("result"))));
                } else if (STATE_STARTED.equals(state)) {
                    Event result = unknownToolExecutionResult(callId, Event.fromMap(asMap(record.get("event"))));
                    record.put("result", result.toMap());
                    record.put("state", STATE_RESULT);
                    write(record);
                    pending.put(callId, result);
                } else {
                    throw new IOException("unknown tool result state " + state + " for call " + callId);
                }
            }
        }
        return new RecoverResult(pending, processed);
    }

    public ToolCallStoreDecision begin(String callId, Event event) throws IOException {
        try {
            Map<String, Object> record = read(callId);
            String state = stringValue(record.get("state"));
            if (STATE_SENT.equals(state)) {
                return new ToolCallStoreDecision(true, null);
            }
            if (STATE_RESULT.equals(state)) {
                return new ToolCallStoreDecision(false, Event.fromMap(asMap(record.get("result"))));
            }
            if (STATE_STARTED.equals(state)) {
                Event result = unknownToolExecutionResult(callId, Event.fromMap(asMap(record.get("event"))));
                record.put("result", result.toMap());
                record.put("state", STATE_RESULT);
                write(record);
                return new ToolCallStoreDecision(false, result);
            }
            throw new IOException("unknown tool result state " + state + " for call " + callId);
        } catch (java.io.FileNotFoundException e) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("call_id", callId);
            record.put("state", STATE_STARTED);
            record.put("event", event.toMap());
            write(record);
            return new ToolCallStoreDecision(false, null);
        }
    }

    public void saveResult(String callId, Event result) throws IOException {
        Map<String, Object> record = read(callId);
        record.put("state", STATE_RESULT);
        record.put("result", result.toMap());
        write(record);
    }

    public void markSent(String callId) throws IOException {
        Map<String, Object> record = read(callId);
        record.put("state", STATE_SENT);
        write(record);
    }

    private Map<String, Object> read(String callId) throws IOException {
        Path path = path(callId);
        if (!Files.exists(path)) {
            throw new java.io.FileNotFoundException(path.toString());
        }
        return readPath(path);
    }

    private Map<String, Object> readPath(Path path) throws IOException {
        return MAPPER.readValue(Files.readAllBytes(path), new TypeReference<Map<String, Object>>() {
        });
    }

    private void write(Map<String, Object> record) throws IOException {
        String callId = stringValue(record.get("call_id"));
        if (callId.isEmpty()) {
            throw new IOException("call id must not be empty");
        }
        record.put("updated_at", Instant.now().toString());
        Path target = path(callId);
        Path tmp = Files.createTempFile(dir, ".tool-result-", ".tmp");
        try {
            byte[] data = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(record);
            try (FileChannel channel = FileChannel.open(
                    tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        tmp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            syncDirectory();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private void syncDirectory() {
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Some non-POSIX filesystems do not support syncing directories.
        }
    }

    private Path path(String callId) {
        return dir.resolve(sha256(callId) + ".json");
    }

    private static Event unknownToolExecutionResult(String callId, Event event) {
        Event out;
        java.util.List<ContentBlock> content = Collections.singletonList(new ContentBlock(
                "text",
                "tool execution state is unknown after worker restart; refusing to re-execute this tool_use to avoid duplicate side effects"));
        if (SelfHostedConstants.EVENT_TYPE_AGENT_CUSTOM_TOOL_USE.equals(event.getType())) {
            out = Event.newUserCustomToolResultEvent(callId, content, true, event.getSessionThreadId());
        } else {
            out = Event.newUserToolResultEvent(callId, content, true, event.getSessionThreadId());
        }
        return out;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.<String, Object>emptyMap();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static class RecoverResult {
        private final Map<String, Event> pending;
        private final Map<String, Boolean> processed;

        public RecoverResult(Map<String, Event> pending, Map<String, Boolean> processed) {
            this.pending = pending;
            this.processed = processed;
        }

        public Map<String, Event> getPending() {
            return pending;
        }

        public Map<String, Boolean> getProcessed() {
            return processed;
        }
    }

    public static class ToolCallStoreDecision {
        private final boolean sent;
        private final Event result;

        public ToolCallStoreDecision(boolean sent, Event result) {
            this.sent = sent;
            this.result = result;
        }

        public boolean isSent() {
            return sent;
        }

        public Event getResult() {
            return result;
        }
    }
}
