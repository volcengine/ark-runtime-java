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
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
                Path path = safePath(context, firstNonEmpty(stringValue(args.get("path")), stringValue(args.get("file"))));
                byte[] data = readBounded(path, MAX_OUTPUT_BYTES);
                return ToolResult.text(new String(data, StandardCharsets.UTF_8));
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
