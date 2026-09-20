// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.service.ArkService;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class DefaultTools {
    private static final ObjectMapper MAPPER = ArkService.defaultObjectMapper();
    private static final int MAX_OUTPUT_BYTES = 100000;
    private static final int DEFAULT_READ_LINES = 2000;
    private static final int MAX_READ_LINE_CHARS = 2000;
    private static final int MAX_SEARCH_MATCHES = 1000;
    private static final long PROCESS_TERMINATION_GRACE_MILLIS = 1000L;

    private DefaultTools() {
    }

    public static ToolSet create() {
        return new ToolSet()
                .add(new BashTool())
                .add(new ReadTool())
                .add(new WriteTool())
                .add(new EditTool())
                .add(new GlobTool())
                .add(new GrepTool());
    }

    static class BashTool implements Tool {
        @Override
        public String name() {
            return "bash";
        }

        @Override
        public ToolResult execute(Object input, ToolContext context) {
            Map<String, Object> args = asMap(input);
            String command = stringValue(args.get("command"));
            if (command.isEmpty()) {
                command = stringValue(args.get("cmd"));
            }
            if (command.isEmpty()) {
                return ToolResult.error("bash command is required");
            }
            ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-c", command);
            builder.directory(new File(context.getWorkdir()));
            builder.environment().clear();
            Map<String, String> environment = context.hasExplicitEnv()
                    ? new LinkedHashMap<>(context.getEnv())
                    : new LinkedHashMap<>(System.getenv());
            builder.environment().putAll(scrubbedEnv(environment));
            builder.redirectErrorStream(true);
            try {
                Process process = builder.start();
                BoundedOutput output = new BoundedOutput(MAX_OUTPUT_BYTES);
                Thread reader = new Thread(() -> drainOutput(process.getInputStream(), output), "ma-self-host-bash-output");
                reader.setDaemon(true);
                reader.start();
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, context.getToolTimeoutMillis()));
                String failure = "";
                while (!process.waitFor(100L, TimeUnit.MILLISECONDS)) {
                    if (context.isCancelled()) {
                        failure = "tool execution canceled";
                        process.destroyForcibly();
                        break;
                    }
                    if (context.getToolTimeoutMillis() > 0L && System.nanoTime() >= deadline) {
                        failure = "tool execution timed out after " + context.getToolTimeoutMillis() + "ms";
                        process.destroyForcibly();
                        break;
                    }
                }
                boolean terminated = !process.isAlive()
                        || process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS);
                reader.join(1000L);
                String text = output.text();
                if (!terminated) {
                    process.destroyForcibly();
                    String message = failure.isEmpty() ? "tool process did not terminate" : failure;
                    return ToolResult.error(message + (text.isEmpty() ? "" : "\n" + text));
                }
                if (!failure.isEmpty()) {
                    return ToolResult.error(failure + (text.isEmpty() ? "" : "\n" + text));
                }
                if (process.exitValue() != 0) {
                    return ToolResult.error("exit code " + process.exitValue() + "\n" + text);
                }
                return ToolResult.text(text);
            } catch (Exception e) {
                return ToolResult.error(e.getMessage());
            }
        }
    }

    static class ReadTool implements Tool {
        @Override
        public String name() {
            return "read";
        }

        @Override
        public ToolResult execute(Object input, ToolContext context) {
            try {
                Map<String, Object> args = asMap(input);
                Path path = safePath(context, readFilePath(args));
                List<Long> viewRange = readViewRange(args.get("view_range"));
                boolean hasViewRange = viewRange != null && !viewRange.isEmpty();
                Long offset = optionalLong(args, "offset");
                Long lineLimit = optionalLong(args, "limit");
                if (hasViewRange && (offset != null || lineLimit != null)) {
                    return ToolResult.error("view_range cannot be combined with offset or limit");
                }
                MediaInfo media = detectReadMedia(path);
                if (media != null) {
                    if (hasViewRange || offset != null || lineLimit != null) {
                        return ToolResult.error(
                                "view_range, offset, and limit are only supported for text files");
                    }
                    long size = Files.size(path);
                    long mediaLimit = context.getMaxMediaFileBytes();
                    if (mediaLimit == 0L) {
                        mediaLimit = context.getMaxInputFileBytes();
                    }
                    if (mediaLimit > 0L && size > mediaLimit) {
                        return ToolResult.error("media file too large: " + size + " bytes");
                    }
                    int readLimit = mediaLimit > 0L
                            ? (int) Math.min(mediaLimit + 1L, Integer.MAX_VALUE)
                            : Integer.MAX_VALUE;
                    byte[] data = mediaLimit > 0L
                            ? readBounded(path, readLimit)
                            : Files.readAllBytes(path);
                    if (mediaLimit > 0L && data.length > mediaLimit) {
                        return ToolResult.error("media file too large: " + Math.max(size, data.length) + " bytes");
                    }
                    ContentBlock block = new ContentBlock();
                    block.setType(media.blockType);
                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("type", "base64");
                    source.put("media_type", media.mediaType);
                    source.put("data", Base64.getEncoder().encodeToString(data));
                    block.setSource(source);
                    return new ToolResult(Collections.singletonList(block), false);
                }
                long configuredLimit = context.getMaxInputFileBytes();
                int byteLimit = configuredLimit > 0L
                        ? (int) Math.min(configuredLimit, Integer.MAX_VALUE)
                        : MAX_OUTPUT_BYTES;
                long size = Files.size(path);
                if (byteLimit > 0 && size > byteLimit) {
                    return ToolResult.error("file too large: " + size + " bytes");
                }
                String text;
                try {
                    text = decodeUTF8(readBounded(path, byteLimit));
                } catch (CharacterCodingException e) {
                    return ToolResult.error("binary file cannot be read directly");
                }
                if (hasViewRange) {
                    return ToolResult.text(renderViewRange(text, viewRange));
                }
                if (offset != null && offset < 1L) {
                    return ToolResult.error(
                            "offset is the 1-based start line and must be >= 1, got " + offset);
                }
                return ToolResult.text(renderReadLines(text, offset, lineLimit));
            } catch (Exception e) {
                return ToolResult.error(e.getMessage());
            }
        }
    }

    static class WriteTool implements Tool {
        @Override
        public String name() {
            return "write";
        }

        @Override
        public ToolResult execute(Object input, ToolContext context) {
            try {
                Map<String, Object> args = asMap(input);
                String rawPath = firstNonEmpty(stringValue(args.get("path")), stringValue(args.get("file")));
                Path path = safePath(context, rawPath);
                String content = stringValue(args.get("content"));
                Files.createDirectories(path.getParent());
                Path verified = safePath(context, rawPath);
                if (!verified.equals(path)) {
                    throw new IOException("path resolution changed while writing");
                }
                writeFileAtomically(path, content.getBytes(StandardCharsets.UTF_8), null);
                return ToolResult.text("wrote " + content.length() + " bytes");
            } catch (Exception e) {
                return ToolResult.error(e.getMessage());
            }
        }
    }

    static class EditTool implements Tool {
        @Override
        public String name() {
            return "edit";
        }

        @Override
        public ToolResult execute(Object input, ToolContext context) {
            try {
                Map<String, Object> args = asMap(input);
                String rawPath = firstNonEmpty(stringValue(args.get("path")), stringValue(args.get("file")));
                Path path = safePath(context, rawPath);
                String oldString = firstNonEmpty(stringValue(args.get("old_string")), stringValue(args.get("old")));
                String newString = firstNonEmpty(stringValue(args.get("new_string")), stringValue(args.get("new")));
                if (oldString.isEmpty()) {
                    return ToolResult.error("old_string is required");
                }
                String data = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                if (!data.contains(oldString)) {
                    return ToolResult.error("old_string was not found");
                }
                Set<PosixFilePermission> permissions = posixPermissions(path);
                Path verified = safePath(context, rawPath);
                if (!verified.equals(path)) {
                    throw new IOException("path resolution changed while editing");
                }
                byte[] updated = data.replaceFirst(Pattern.quote(oldString), Matcher.quoteReplacement(newString))
                        .getBytes(StandardCharsets.UTF_8);
                writeFileAtomically(path, updated, permissions);
                return ToolResult.text("edited");
            } catch (Exception e) {
                return ToolResult.error(e.getMessage());
            }
        }
    }

    static class GlobTool implements Tool {
        @Override
        public String name() {
            return "glob";
        }

        @Override
        public ToolResult execute(Object input, ToolContext context) {
            Map<String, Object> args = asMap(input);
            String pattern = stringValue(args.get("pattern"));
            if (pattern.isEmpty()) {
                return ToolResult.error("pattern is required");
            }
            Path root = Paths.get(context.getWorkdir()).toAbsolutePath().normalize();
            PathMatcherCompat matcher = new PathMatcherCompat(pattern);
            StringBuilder out = new StringBuilder();
            try (Stream<Path> stream = Files.walk(root)) {
                Iterator<Path> paths = stream.filter(Files::isRegularFile).iterator();
                int matches = 0;
                while (!context.isCancelled() && paths.hasNext() && matches < MAX_SEARCH_MATCHES) {
                    Path path = paths.next();
                    Path rel = root.relativize(path);
                    if (matcher.matches(rel)) {
                        if (!appendWithinLimit(out, rel.toString() + '\n')) {
                            break;
                        }
                        matches++;
                    }
                }
            } catch (IOException e) {
                return ToolResult.error(e.getMessage());
            }
            return context.isCancelled() ? ToolResult.error("tool execution canceled") : ToolResult.text(out.toString());
        }
    }

    static class GrepTool implements Tool {
        @Override
        public String name() {
            return "grep";
        }

        @Override
        public ToolResult execute(Object input, ToolContext context) {
            Map<String, Object> args = asMap(input);
            String pattern = firstNonEmpty(stringValue(args.get("pattern")), stringValue(args.get("query")));
            if (pattern.isEmpty()) {
                return ToolResult.error("pattern is required");
            }
            Pattern regex = Pattern.compile(pattern);
            Path root;
            try {
                root = safePath(context, firstNonEmpty(stringValue(args.get("path")), "."));
            } catch (IOException e) {
                return ToolResult.error(e.getMessage());
            }
            StringBuilder out = new StringBuilder();
            try (Stream<Path> stream = Files.walk(root)) {
                Iterator<Path> paths = stream.filter(Files::isRegularFile).iterator();
                int matches = 0;
                while (!context.isCancelled() && paths.hasNext() && matches < MAX_SEARCH_MATCHES) {
                    Path candidate = paths.next();
                    Path verified;
                    try {
                        verified = safePath(context, candidate.toString());
                    } catch (IOException ignored) {
                        continue;
                    }
                    String displayPath = Paths.get(context.getWorkdir())
                            .toAbsolutePath()
                            .normalize()
                            .relativize(candidate)
                            .toString();
                    matches += appendMatches(
                            regex, context, verified, displayPath, out, MAX_SEARCH_MATCHES - matches);
                    if (out.length() >= MAX_OUTPUT_BYTES) {
                        break;
                    }
                }
            } catch (IOException e) {
                return ToolResult.error(e.getMessage());
            }
            return context.isCancelled() ? ToolResult.error("tool execution canceled") : ToolResult.text(out.toString());
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object input) {
        if (input instanceof Map) {
            return (Map<String, Object>) input;
        }
        if (input instanceof String) {
            try {
                return MAPPER.readValue((String) input, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ignored) {
                return Collections.singletonMap("command", input);
            }
        }
        return Collections.emptyMap();
    }

    private static MediaInfo detectReadMedia(Path path) throws IOException {
        byte[] header = readBounded(path, 512);
        if (startsWith(header, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return new MediaInfo("image", "image/jpeg");
        }
        if (startsWith(header, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1a, '\n'})) {
            return new MediaInfo("image", "image/png");
        }
        if (startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
            return new MediaInfo("image", "image/gif");
        }
        if (header.length >= 12
                && startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && matchesAt(header, 8, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return new MediaInfo("image", "image/webp");
        }
        if (startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return new MediaInfo("document", "application/pdf");
        }
        if (!looksBinary(header)) {
            return null;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return new MediaInfo("image", "image/jpeg");
        }
        if (name.endsWith(".png")) {
            return new MediaInfo("image", "image/png");
        }
        if (name.endsWith(".gif")) {
            return new MediaInfo("image", "image/gif");
        }
        if (name.endsWith(".webp")) {
            return new MediaInfo("image", "image/webp");
        }
        if (name.endsWith(".pdf")) {
            return new MediaInfo("document", "application/pdf");
        }
        return null;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        return matchesAt(value, 0, prefix);
    }

    private static boolean matchesAt(byte[] value, int offset, byte[] expected) {
        if (value.length - offset < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (value[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean looksBinary(byte[] value) {
        for (byte item : value) {
            if (item == 0) {
                return true;
            }
        }
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value));
            return false;
        } catch (CharacterCodingException error) {
            return true;
        }
    }

    private static long longValue(Object value, String name) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value == null || value.toString().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " must be an integer", error);
        }
    }

    private static String readFilePath(Map<String, Object> args) {
        String path = "";
        for (String name : new String[] {"file_path", "path", "file"}) {
            Object value = args.get(name);
            if (value != null && !(value instanceof String)) {
                throw new IllegalArgumentException(name + " must be a string");
            }
            String candidate = stringValue(value);
            if (candidate.isEmpty()) {
                continue;
            }
            if (!path.isEmpty() && !path.equals(candidate)) {
                throw new IllegalArgumentException("file_path, path, and file must not conflict");
            }
            path = candidate;
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("file_path is required");
        }
        return path;
    }

    private static Long optionalLong(Map<String, Object> args, String name) {
        return args.containsKey(name) && args.get(name) != null ? longValue(args.get(name), name) : null;
    }

    private static List<Long> readViewRange(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("view_range must be [start_line, end_line]");
        }
        List<?> raw = (List<?>) value;
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        if (raw.size() != 2) {
            throw new IllegalArgumentException("view_range must be [start_line, end_line]");
        }
        List<Long> result = new ArrayList<>(2);
        result.add(longValue(raw.get(0), "view_range"));
        result.add(longValue(raw.get(1), "view_range"));
        return result;
    }

    private static String decodeUTF8(byte[] data) throws CharacterCodingException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data))
                .toString();
    }

    private static String renderViewRange(String text, List<Long> viewRange) {
        String[] lines = text.split("\n", -1);
        long startLine = viewRange.get(0);
        long zeroBasedStart = startLine <= 1L ? 0L : startLine - 1L;
        int start = (int) Math.min(zeroBasedStart, lines.length);
        int end = lines.length;
        if (viewRange.get(1) > 0L) {
            end = (int) Math.min(viewRange.get(1), lines.length);
        }
        if (end < start) {
            throw new IllegalArgumentException(
                    "view_range end line " + viewRange.get(1)
                            + " is before start line " + viewRange.get(0));
        }
        StringBuilder output = new StringBuilder();
        for (int index = start; index < end; index++) {
            if (index > start) {
                output.append('\n');
            }
            output.append(lines[index]);
        }
        return output.toString();
    }

    private static String renderReadLines(String text, Long offset, Long limit) {
        String[] lines = text.split("\n", -1);
        int total = lines.length;
        if (total > 0 && lines[total - 1].isEmpty() && text.endsWith("\n")) {
            total--;
        }
        long startValue = offset == null ? 0L : offset - 1L;
        int start = (int) Math.min(startValue, total);
        long count = limit != null && limit > 0L ? limit : DEFAULT_READ_LINES;
        int end = count >= total - start ? total : start + (int) count;
        StringBuilder output = new StringBuilder();
        for (int index = start; index < end; index++) {
            output.append(String.format(Locale.ROOT, "%6d\t%s\n", index + 1, truncateLine(lines[index])));
        }
        if (end < total) {
            output.append(String.format(
                    Locale.ROOT,
                    "\n[truncated: showing lines %d-%d of %d]\n",
                    start + 1,
                    end,
                    total));
        }
        return output.toString();
    }

    private static String truncateLine(String line) {
        if (line.codePointCount(0, line.length()) <= MAX_READ_LINE_CHARS) {
            return line;
        }
        return line.substring(0, line.offsetByCodePoints(0, MAX_READ_LINE_CHARS))
                + " [line truncated]";
    }

    private static class MediaInfo {
        final String blockType;
        final String mediaType;

        MediaInfo(String blockType, String mediaType) {
            this.blockType = blockType;
            this.mediaType = mediaType;
        }
    }

    private static int appendMatches(
            Pattern regex,
            ToolContext context,
            Path path,
            String displayPath,
            StringBuilder out,
            int remainingMatches) {
        int matches = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            int lineNo = 0;
            String line;
            while (!context.isCancelled() && matches < remainingMatches && (line = reader.readLine()) != null) {
                lineNo++;
                if (regex.matcher(line).find()) {
                    String value = displayPath + ':' + lineNo + ':' + line + '\n';
                    if (!appendWithinLimit(out, value)) {
                        break;
                    }
                    matches++;
                }
            }
        } catch (IOException ignored) {
        }
        return matches;
    }

    private static Path safePath(ToolContext context, String rawPath) throws IOException {
        if (rawPath == null || rawPath.isEmpty()) {
            throw new IOException("path is required");
        }
        Path root = Paths.get(context.getWorkdir()).toAbsolutePath().normalize();
        Path path = Paths.get(rawPath);
        if (!path.isAbsolute()) {
            path = root.resolve(path);
        }
        Path resolved = path.toAbsolutePath().normalize();
        if (context.isUnrestrictedPaths()) {
            return resolved;
        }
        Path realRoot = root.toRealPath();
        Path realResolved;
        if (Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
            realResolved = resolved.toRealPath();
        } else {
            Path existing = resolved.getParent();
            while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing = existing.getParent();
            }
            if (existing == null) {
                throw new IOException("path has no existing parent: " + rawPath);
            }
            Path realExisting = existing.toRealPath();
            realResolved = realExisting.resolve(existing.relativize(resolved)).normalize();
        }
        if (!realResolved.startsWith(realRoot)) {
            throw new IOException("path escapes workdir: " + rawPath);
        }
        return realResolved;
    }

    private static Map<String, String> scrubbedEnv(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (!isSensitiveEnvKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    static boolean isSensitiveEnvKey(String key) {
        String value = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        String[] prefixes = {
            "AIME_", "ARK_", "MA_", "X_CODE_", "ANTHROPIC_", "OPENAI_", "AWS_", "AZURE_", "GOOGLE_"
        };
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return value.equals(joinEnvKey("VOLC_", "ACCESS", "KEY"))
                || value.equals(joinEnvKey("VOLC_", "SEC", "RET", "KEY"))
                || value.equals(joinEnvKey("BYTEPLUS_", "ACCESS", "KEY"))
                || value.equals(joinEnvKey("BYTEPLUS_", "SEC", "RET", "KEY"))
                || matchesCredentialName(value, "TO", "KEN")
                || matchesCredentialName(value, "SEC", "RET")
                || matchesCredentialName(value, "PA", "SS", "WO", "RD")
                || matchesCredentialName(value, "PA", "SS", "WD")
                || matchesCredentialName(value, "PRIVATE_", "KEY")
                || matchesCredentialName(value, "API_", "KEY")
                || matchesCredentialName(value, "ACCESS_", "KEY")
                || matchesCredentialName(value, "SECRET_", "KEY")
                || matchesCredentialName(value, "J", "WT")
                || matchesCredentialName(value, "P", "AT");
    }

    private static boolean matchesCredentialName(String value, String... parts) {
        String name = joinEnvKey(parts);
        return value.equals(name) || value.endsWith("_" + name);
    }

    private static String joinEnvKey(String... parts) {
        StringBuilder value = new StringBuilder();
        for (String part : parts) {
            value.append(part);
        }
        return value.toString();
    }

    private static byte[] readBounded(Path path, int limit) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 65536));
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) >= 0 && output.size() < limit) {
                output.write(buffer, 0, Math.min(count, limit - output.size()));
            }
            return output.toByteArray();
        }
    }

    private static void writeFileAtomically(
            Path target, byte[] data, Set<PosixFilePermission> permissions) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), ".ark-write-", ".tmp");
        try {
            if (permissions != null) {
                Files.setPosixFilePermissions(tmp, permissions);
            }
            Files.write(tmp, data);
            try {
                Files.move(
                        tmp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static Set<PosixFilePermission> posixPermissions(Path path) throws IOException {
        try {
            return Files.getPosixFilePermissions(path);
        } catch (UnsupportedOperationException ignored) {
            return null;
        }
    }

    private static boolean appendWithinLimit(StringBuilder output, String value) {
        int remaining = MAX_OUTPUT_BYTES - output.length();
        if (remaining <= 0) {
            return false;
        }
        output.append(value, 0, Math.min(value.length(), remaining));
        return value.length() <= remaining;
    }

    private static void drainOutput(InputStream stream, BoundedOutput output) {
        byte[] buffer = new byte[65536];
        try (InputStream input = stream) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.append(buffer, count);
            }
        } catch (IOException ignored) {
        }
    }

    private static class BoundedOutput {
        private static final byte[] TRUNCATION_MARKER = "\n... truncated ...".getBytes(StandardCharsets.UTF_8);
        private final int limit;
        private final ByteArrayOutputStream output;
        private boolean truncated;

        BoundedOutput(int limit) {
            this.limit = limit;
            this.output = new ByteArrayOutputStream(Math.min(limit, 65536));
        }

        synchronized void append(byte[] data, int count) {
            int remaining = Math.max(0, limit - TRUNCATION_MARKER.length - output.size());
            if (remaining > 0) {
                output.write(data, 0, Math.min(count, remaining));
            }
            if (count > remaining) {
                truncated = true;
            }
        }

        synchronized String text() {
            if (truncated) {
                output.write(TRUNCATION_MARKER, 0, TRUNCATION_MARKER.length);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonEmpty(String first, String second) {
        return first != null && !first.isEmpty() ? first : (second == null ? "" : second);
    }

    static class PathMatcherCompat {
        private final java.nio.file.PathMatcher matcher;

        PathMatcherCompat(String pattern) {
            this.matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        }

        boolean matches(Path path) {
            return matcher.matches(path);
        }
    }
}
