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
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class DefaultToolsTest {
    @Test
    public void bashScrubsSensitiveExplicitEnvironment() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-tools-");
        ToolContext context = new ToolContext(workdir.toString());
        Map<String, String> env = new LinkedHashMap<>();
        env.put(envName("ARK", "API", "KEY"), "redacted");
        env.put("SAFE_VALUE", "ok");
        context.setEnv(env);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(
                "command",
                "printf '%s/%s/%s' \"${ARK_API_KEY-unset}\" \"${HOME-unset}\" \"$SAFE_VALUE\"");

        ToolResult result = new DefaultTools.BashTool().execute(input, context);

        assertFalse(result.isError());
        assertEquals("unset/unset/ok", result.getContent().get(0).getText());
    }

    @Test
    public void bashScrubsExtendedCredentialNames() {
        assertTrue(DefaultTools.isSensitiveEnvKey(envName("AIME", "SESSION")));
        assertTrue(DefaultTools.isSensitiveEnvKey(envName("X", "CODE", "AUTH")));
        assertTrue(DefaultTools.isSensitiveEnvKey(envName("GITHUB", "JWT")));
        assertTrue(DefaultTools.isSensitiveEnvKey(envName("GITHUB", "PAT")));
        assertFalse(DefaultTools.isSensitiveEnvKey("SAFE_VALUE"));
    }

    @Test
    public void bashTimeoutReturnsPromptly() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-timeout-");
        ToolContext context = new ToolContext(workdir.toString());
        context.setToolTimeoutMillis(100L);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("command", "sleep 10");

        long started = System.nanoTime();
        ToolResult result = new DefaultTools.BashTool().execute(input, context);

        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 3000L);
        assertTrue(result.isError());
        assertTrue(result.getContent().get(0).getText().contains("timed out"));
    }

    @Test
    public void fileToolRejectsSymlinkEscape() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-root-");
        Path outside = Files.createTempDirectory("ark-java-outside-");
        Files.write(outside.resolve("secret.txt"), "secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.createSymbolicLink(workdir.resolve("escape"), outside);
        ToolContext context = new ToolContext(workdir.toString());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("path", "escape/secret.txt");

        ToolResult result = new DefaultTools.ReadTool().execute(input, context);

        assertTrue(result.isError());
        assertTrue(result.getContent().get(0).getText().contains("escapes workdir"));
    }

    @Test
    public void grepSkipsSymlinkThatEscapesWorkdir() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-grep-root-");
        Path outside = Files.createTempDirectory("ark-java-grep-outside-");
        Files.write(
                outside.resolve("secret.txt"),
                "SELFHOST_SECRET_MARKER\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.createSymbolicLink(workdir.resolve("escape.txt"), outside.resolve("secret.txt"));
        ToolContext context = new ToolContext(workdir.toString());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("path", ".");
        input.put("pattern", "SELFHOST_SECRET_MARKER");

        ToolResult result = new DefaultTools.GrepTool().execute(input, context);

        assertFalse(result.isError());
        assertFalse(result.getContent().get(0).getText().contains("SELFHOST_SECRET_MARKER"));
    }

    @Test
    public void writeKeepsExistingTargetWhenAtomicReplaceFails() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-write-");
        Path target = Files.createDirectory(workdir.resolve("example.txt"));
        Files.write(target.resolve("marker.txt"), "old".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ToolContext context = new ToolContext(workdir.toString());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("path", "example.txt");
        input.put("content", "new");

        ToolResult result = new DefaultTools.WriteTool().execute(input, context);

        assertTrue(result.isError());
        assertEquals(
                "old",
                new String(
                        Files.readAllBytes(target.resolve("marker.txt")),
                        java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    public void editRejectsEmptyOldString() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-edit-");
        Path file = workdir.resolve("example.txt");
        Files.write(file, "original".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ToolContext context = new ToolContext(workdir.toString());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("path", "example.txt");
        input.put("new_string", "unexpected");

        ToolResult result = new DefaultTools.EditTool().execute(input, context);

        assertTrue(result.isError());
        assertTrue(result.getContent().get(0).getText().contains("old_string is required"));
        assertEquals("original", new String(
                Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    public void bashDrainsAndBoundsLargeOutput() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-output-");
        ToolContext context = new ToolContext(workdir.toString());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("command", "yes x | head -c 200000");

        ToolResult result = new DefaultTools.BashTool().execute(input, context);

        assertFalse(result.isError());
        assertTrue(result.getContent().get(0).getText().length() <= 100000);
        assertTrue(result.getContent().get(0).getText().contains("truncated"));
    }

    private static String envName(String... parts) {
        return String.join("_", parts);
    }
}
