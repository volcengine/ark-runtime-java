// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class DefaultToolsTest {
    @Test
    public void readReturnsNativeImageAndDocumentBlocks() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-media-");
        byte[] image = new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1a, '\n', 1};
        byte[] document = "%PDF-1.7\ndata".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Files.write(workdir.resolve("image.png"), image);
        Files.write(workdir.resolve("document.pdf"), document);
        ToolContext context = new ToolContext(workdir.toString());

        ToolResult imageResult = new DefaultTools.ReadTool().execute(
                Collections.singletonMap("file_path", "image.png"), context);
        ToolResult documentResult = new DefaultTools.ReadTool().execute(
                Collections.singletonMap("file_path", "document.pdf"), context);

        assertMediaBlock(imageResult, "image", "image/png", image);
        assertMediaBlock(documentResult, "document", "application/pdf", document);
    }

    @Test
    public void readRejectsRangesAndOversizedMedia() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-media-limit-");
        byte[] image = new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1a, '\n', 1};
        Files.write(workdir.resolve("image.png"), image);
        ToolContext context = new ToolContext(workdir.toString());
        Map<String, Object> rangedInput = new LinkedHashMap<>();
        rangedInput.put("path", "image.png");
        rangedInput.put("limit", 1);

        ToolResult ranged = new DefaultTools.ReadTool().execute(rangedInput, context);
        context.setMaxMediaFileBytes(image.length - 1L);
        ToolResult oversized = new DefaultTools.ReadTool().execute(
                Collections.singletonMap("path", "image.png"), context);

        assertTrue(ranged.isError());
        assertEquals(
                "view_range, offset, and limit are only supported for text files",
                ranged.getContent().get(0).getText());
        assertTrue(oversized.isError());
        assertEquals("media file too large: " + image.length + " bytes", oversized.getContent().get(0).getText());
    }

    @Test
    public void readReportsInvalidMediaRangeField() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-media-range-");
        Files.write(workdir.resolve("image.png"), new byte[] {
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1a, '\n', 1
        });
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("path", "image.png");
        input.put("offset", "abc");

        ToolResult result = new DefaultTools.ReadTool().execute(input, new ToolContext(workdir.toString()));

        assertTrue(result.isError());
        assertEquals("offset must be an integer", result.getContent().get(0).getText());
    }

    @Test
    public void readUsesManagedAgentLineRange() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-read-lines-");
        Files.write(
                workdir.resolve("example.txt"),
                "a\nb\nc\nd\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ToolContext context = new ToolContext(workdir.toString());
        DefaultTools.ReadTool tool = new DefaultTools.ReadTool();
        Map<String, Object> rangedInput = new LinkedHashMap<>();
        rangedInput.put("file_path", "example.txt");
        rangedInput.put("offset", 2);
        rangedInput.put("limit", 2);

        ToolResult whole = tool.execute(Collections.singletonMap("file_path", "example.txt"), context);
        ToolResult ranged = tool.execute(rangedInput, context);
        Map<String, Object> invalidInput = new LinkedHashMap<>();
        invalidInput.put("file_path", "example.txt");
        invalidInput.put("offset", 0);
        ToolResult invalid = tool.execute(invalidInput, context);

        assertFalse(whole.isError());
        assertEquals("     1\ta\n     2\tb\n     3\tc\n     4\td\n", whole.getContent().get(0).getText());
        assertFalse(ranged.isError());
        assertEquals(
                "     2\tb\n     3\tc\n\n[truncated: showing lines 2-3 of 4]\n",
                ranged.getContent().get(0).getText());
        assertTrue(invalid.isError());
        assertEquals(
                "offset is the 1-based start line and must be >= 1, got 0",
                invalid.getContent().get(0).getText());
    }

    @Test
    public void readMarksTruncatedLinesAndRejectsInvalidUTF8() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-read-truncated-");
        Files.write(
                workdir.resolve("long.txt"),
                repeat('a', 2001).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(workdir.resolve("binary.txt"), new byte[] {(byte) 0xff});
        ToolContext context = new ToolContext(workdir.toString());
        DefaultTools.ReadTool tool = new DefaultTools.ReadTool();

        ToolResult truncated = tool.execute(Collections.singletonMap("file_path", "long.txt"), context);
        ToolResult invalidUTF8 = tool.execute(Collections.singletonMap("file_path", "binary.txt"), context);

        assertFalse(truncated.isError());
        assertEquals(
                "     1\t" + repeat('a', 2000) + " [line truncated]\n",
                truncated.getContent().get(0).getText());
        assertTrue(invalidUTF8.isError());
        assertEquals("binary file cannot be read directly", invalidUTF8.getContent().get(0).getText());
    }

    @Test
    public void readKeepsLegacyInputsWithoutAmbiguity() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-read-compat-");
        Files.write(
                workdir.resolve("example.txt"),
                "a\nb\nc\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ToolContext context = new ToolContext(workdir.toString());
        DefaultTools.ReadTool tool = new DefaultTools.ReadTool();

        assertFalse(tool.execute(Collections.singletonMap("path", "example.txt"), context).isError());
        assertFalse(tool.execute(Collections.singletonMap("file", "example.txt"), context).isError());
        Map<String, Object> samePath = new LinkedHashMap<>();
        samePath.put("file_path", "example.txt");
        samePath.put("path", "example.txt");
        assertFalse(tool.execute(samePath, context).isError());

        Map<String, Object> legacyRangeInput = new LinkedHashMap<>();
        legacyRangeInput.put("file_path", "example.txt");
        legacyRangeInput.put("view_range", java.util.Arrays.asList(2, 3));
        ToolResult legacyRange = tool.execute(legacyRangeInput, context);
        assertFalse(legacyRange.isError());
        assertEquals("b\nc", legacyRange.getContent().get(0).getText());

        Map<String, Object> pathConflictInput = new LinkedHashMap<>();
        pathConflictInput.put("file_path", "example.txt");
        pathConflictInput.put("path", "other.txt");
        ToolResult pathConflict = tool.execute(pathConflictInput, context);
        assertTrue(pathConflict.isError());
        assertEquals(
                "file_path, path, and file must not conflict",
                pathConflict.getContent().get(0).getText());

        Map<String, Object> rangeConflictInput = new LinkedHashMap<>();
        rangeConflictInput.put("file_path", "example.txt");
        rangeConflictInput.put("view_range", java.util.Arrays.asList(1, 1));
        rangeConflictInput.put("offset", 1);
        ToolResult rangeConflict = tool.execute(rangeConflictInput, context);
        assertTrue(rangeConflict.isError());
        assertEquals(
                "view_range cannot be combined with offset or limit",
                rangeConflict.getContent().get(0).getText());
    }

    @Test
    public void fileToolsAcceptPathAliases() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-file-aliases-");
        ToolContext context = new ToolContext(workdir.toString());

        for (String key : new String[] {"file_path", "path", "file"}) {
            String name = key + ".txt";
            Map<String, Object> writeInput = new LinkedHashMap<>();
            writeInput.put(key, name);
            writeInput.put("content", "old");
            assertFalse(new DefaultTools.WriteTool().execute(writeInput, context).isError());

            assertFalse(new DefaultTools.ReadTool()
                    .execute(Collections.singletonMap(key, name), context)
                    .isError());

            Map<String, Object> editInput = new LinkedHashMap<>();
            editInput.put(key, name);
            editInput.put("old_string", "old");
            editInput.put("new_string", "new");
            assertFalse(new DefaultTools.EditTool().execute(editInput, context).isError());
            assertEquals(
                    "new",
                    new String(Files.readAllBytes(workdir.resolve(name)), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    public void fileToolsValidatePathAliases() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-file-alias-validation-");
        Files.write(workdir.resolve("example.txt"), "old".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ToolContext context = new ToolContext(workdir.toString());
        Map<String, Object> common = new LinkedHashMap<>();
        common.put("file_path", "example.txt");
        common.put("path", "example.txt");
        common.put("file", "example.txt");
        common.put("content", "old");
        common.put("old_string", "old");
        common.put("new_string", "new");

        for (Tool tool : new Tool[] {
                new DefaultTools.ReadTool(), new DefaultTools.WriteTool(), new DefaultTools.EditTool()
        }) {
            assertFalse(tool.execute(common, context).isError());

            Map<String, Object> conflictInput = new LinkedHashMap<>(common);
            conflictInput.put("path", "other.txt");
            ToolResult conflict = tool.execute(conflictInput, context);
            assertTrue(conflict.isError());
            assertEquals(
                    "file_path, path, and file must not conflict",
                    conflict.getContent().get(0).getText());

            Map<String, Object> missingInput = new LinkedHashMap<>();
            missingInput.put("content", "new");
            missingInput.put("old_string", "old");
            missingInput.put("new_string", "new");
            ToolResult missing = tool.execute(missingInput, context);
            assertTrue(missing.isError());
            assertEquals("file_path is required", missing.getContent().get(0).getText());
        }
    }

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
    public void bashInterruptionTerminatesCompoundCommand() throws Exception {
        Path workdir = Files.createTempDirectory("ark-java-cancel-");
        Path started = workdir.resolve("started");
        Path completed = workdir.resolve("completed");
        AtomicBoolean canceled = new AtomicBoolean();
        ToolContext context = new ToolContext(workdir.toString());
        context.setCancelled(canceled::get);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("command", "printf started > started; sleep 1; printf completed > completed");
        AtomicReference<ToolResult> result = new AtomicReference<>();
        Thread execution = new Thread(
                () -> result.set(new DefaultTools.BashTool().execute(input, context)),
                "bash-cancellation-test");

        execution.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (!Files.exists(started) && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue("bash command did not start", Files.exists(started));
        canceled.set(true);
        execution.interrupt();
        execution.join(3000L);

        assertFalse("bash execution did not return after cancellation", execution.isAlive());
        assertTrue(result.get().isError());
        assertTrue(result.get().getContent().get(0).getText().contains("canceled"));
        Thread.sleep(1200L);
        assertFalse("compound command continued after interruption", Files.exists(completed));
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

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private static void assertMediaBlock(
            ToolResult result, String blockType, String mediaType, byte[] expected) {
        assertFalse(result.isError());
        ContentBlock block = result.getContent().get(0);
        assertEquals(blockType, block.getType());
        Map<String, Object> source = (Map<String, Object>) block.getSource();
        assertEquals("base64", source.get("type"));
        assertEquals(mediaType, source.get("media_type"));
        assertEquals(Base64.getEncoder().encodeToString(expected), source.get("data"));
    }
}
