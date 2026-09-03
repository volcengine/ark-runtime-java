// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class FileToolResultStoreTest {
    @Test
    public void recoveryUsesPersistedCallId() throws Exception {
        FileToolResultStore store = new FileToolResultStore(Files.createTempDirectory("ark-java-store-").toString());
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", "event-1");
        raw.put("type", "agent.tool_use");
        raw.put("name", "bash");
        store.begin("call-1", Event.fromMap(raw));

        FileToolResultStore.RecoverResult recovered = store.recover();

        assertEquals("call-1", recovered.getPending().get("call-1").resultCallId());
    }

    @Test
    public void recoveryRemovesStaleTemporaryRecords() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-store-");
        FileToolResultStore store = new FileToolResultStore(workdir.toString());
        Path stale = workdir
                .resolve(".ma_self_hosted_worker")
                .resolve("tool_ledger")
                .resolve(".tool-result-stale.tmp");
        Files.write(stale, "partial".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        store.recover();

        assertFalse(Files.exists(stale));
    }

    @Test
    public void sessionStoreIsolatesSessions() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-store-");
        FileToolResultStore first = new FileToolResultStore(workdir.toString(), "session-a");
        FileToolResultStore second = new FileToolResultStore(workdir.toString(), "session-b");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", "event-1");
        raw.put("type", "agent.tool_use");
        raw.put("name", "bash");

        first.begin("call-1", Event.fromMap(raw));

        assertTrue(second.recover().getPending().isEmpty());
        assertTrue(Files.isDirectory(workdir
                .resolve(".ma_self_hosted_worker")
                .resolve("tool_ledger")
                .resolve("session-a")));
    }

    @Test
    public void sessionStoreSanitizesSessionId() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-store-");
        new FileToolResultStore(workdir.toString(), "../../outside");
        Path base = workdir.resolve(".ma_self_hosted_worker").resolve("tool_ledger");

        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(base, "session-*")) {
            assertTrue(entries.iterator().hasNext());
        }
        assertFalse(Files.exists(workdir.resolve("outside")));
    }
}
