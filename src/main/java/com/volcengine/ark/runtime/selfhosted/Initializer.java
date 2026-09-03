// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Initializer {
    public static final long DEFAULT_MAX_ARCHIVE_BYTES = 128L << 20;
    public static final long DEFAULT_MAX_EXTRACTED_BYTES = 512L << 20;
    public static final int DEFAULT_MAX_ARCHIVE_ENTRIES = 10000;
    private static final Logger LOGGER = Logger.getLogger(Initializer.class.getName());

    private final SelfHostedClient api;
    private final Options options;
    private final List<Path> installedSkillDirs = new ArrayList<>();

    public Initializer(SelfHostedClient api, Options options) {
        if (api == null) {
            throw new IllegalArgumentException("api is required");
        }
        if (options == null) {
            throw new IllegalArgumentException("initializer options are required");
        }
        if (options.workdir == null || options.workdir.trim().isEmpty()) {
            throw new IllegalArgumentException("initializer workdir must not be empty");
        }
        this.api = api;
        this.options = options;
        if (this.options.skillsDir == null || this.options.skillsDir.isEmpty()) {
            this.options.skillsDir = Paths.get(this.options.workdir, "skills").toString();
        }
        if (this.options.maxArchiveBytes <= 0) {
            this.options.maxArchiveBytes = DEFAULT_MAX_ARCHIVE_BYTES;
        }
        if (this.options.maxExtractedBytes <= 0) {
            this.options.maxExtractedBytes = DEFAULT_MAX_EXTRACTED_BYTES;
        }
        if (this.options.maxArchiveEntries <= 0) {
            this.options.maxArchiveEntries = DEFAULT_MAX_ARCHIVE_ENTRIES;
        }
    }

    public void setup(SessionSnapshot session) throws IOException {
        Files.createDirectories(Paths.get(options.workdir));
        Files.createDirectories(Paths.get(options.skillsDir));
        for (SkillRef skill : session.skillRefs()) {
            try {
                installSkill(session.getId(), skill);
            } catch (Exception error) {
                LOGGER.log(
                        Level.WARNING,
                        "failed to install skill session_id=" + session.getId()
                                + " skill=" + skill.nameValue()
                                + " version=" + skill.getVersion(),
                        error);
            }
        }
    }

    public void cleanup() throws IOException {
        IOException firstError = null;
        for (Path path : installedSkillDirs) {
            try {
                deleteRecursively(path);
            } catch (IOException error) {
                if (firstError == null) {
                    firstError = error;
                } else {
                    firstError.addSuppressed(error);
                }
            }
        }
        installedSkillDirs.clear();
        if (firstError != null) {
            throw firstError;
        }
    }

    public void installSkill(String sessionId, SkillRef skill) throws IOException {
        Files.createDirectories(Paths.get(options.workdir));
        Files.createDirectories(Paths.get(options.skillsDir));
        if (skill.idValue() != null && !skill.idValue().trim().isEmpty()) {
            skill = api.resolveSkill(skill);
        }
        String name = safeSkillDirName(skill);
        SkillContent content = api.openSkill(sessionId, skill);
        if (content == null || content.getBody() == null) {
            throw new IOException("download skill " + name + ": empty content");
        }
        Path archive = null;
        Path tmp = null;
        boolean committed = false;
        try {
            archive = copyArchive(name, content.getBody());
            tmp = Files.createTempDirectory(Paths.get(options.skillsDir), "." + name + "-");
            extractArchive(archive, tmp);
            Path source = installSourceDir(tmp);
            Path target = Paths.get(options.skillsDir, name);
            Path backup = replaceSkillDir(source, target);
            installedSkillDirs.add(target);
            committed = true;
            if (!source.equals(tmp)) {
                try {
                    deleteRecursively(tmp);
                } catch (IOException error) {
                    LOGGER.log(Level.WARNING, "remove skill staging directory failed path=" + tmp, error);
                }
            }
            if (backup != null) {
                try {
                    deleteRecursively(backup);
                } catch (IOException error) {
                    LOGGER.log(
                            Level.WARNING,
                            "remove old skill backup failed session_id=" + sessionId
                                    + " skill=" + name + " path=" + backup,
                            error);
                }
            }
        } finally {
            if (!committed && tmp != null) {
                deleteRecursively(tmp);
            }
            content.close();
            if (archive != null) {
                Files.deleteIfExists(archive);
            }
        }
    }

    private Path copyArchive(String name, InputStream body) throws IOException {
        Path tmp = Files.createTempFile("ark-skill-" + name + "-", ".archive");
        long total = 0;
        byte[] buf = new byte[65536];
        try (java.io.OutputStream out = Files.newOutputStream(tmp)) {
            int n;
            while ((n = body.read(buf)) >= 0) {
                total += n;
                if (total > options.maxArchiveBytes) {
                    throw new IOException("skill archive too large: " + total + " bytes");
                }
                out.write(buf, 0, n);
            }
            if (total == 0L) {
                throw new IOException("skill archive is empty");
            }
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        return tmp;
    }

    private void extractArchive(Path archive, Path dst) throws IOException {
        byte[] magic = new byte[4];
        try (InputStream in = Files.newInputStream(archive)) {
            int ignored = in.read(magic);
        }
        if (magic[0] == 'P' && magic[1] == 'K') {
            extractZip(archive, dst);
            return;
        }
        if ((magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b) {
            extractTarGz(archive, dst);
            return;
        }
        throw new IOException("unsupported skill archive format");
    }

    private void extractZip(Path archive, Path dst) throws IOException {
        long[] total = new long[] {0L};
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > options.maxArchiveEntries) {
                    throw new IOException("skill archive contains too many entries: " + entries);
                }
                Path target = safeJoin(dst, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                copyExtracted(zip, target, total);
            }
        }
    }

    private void extractTarGz(Path archive, Path dst) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(Files.newInputStream(archive))) {
            TarReader reader = new TarReader(gzip);
            TarReader.Entry entry;
            long[] total = new long[] {0L};
            int entries = 0;
            while ((entry = reader.next()) != null) {
                entries++;
                if (entries > options.maxArchiveEntries) {
                    throw new IOException("skill archive contains too many entries: " + entries);
                }
                Path target = safeJoin(dst, entry.name);
                if (entry.directory) {
                    Files.createDirectories(target);
                    continue;
                }
                if (!entry.regular) {
                    throw new IOException("unsupported tar entry type: " + entry.name);
                }
                Files.createDirectories(target.getParent());
                copyExtracted(reader, target, total, entry.size);
            }
        }
    }

    private void copyExtracted(InputStream in, Path target, long[] total) throws IOException {
        copyExtracted(in, target, total, Long.MAX_VALUE);
    }

    private void copyExtracted(InputStream in, Path target, long[] total, long maxBytes) throws IOException {
        byte[] buf = new byte[65536];
        long remaining = maxBytes;
        try (java.io.OutputStream out = Files.newOutputStream(target)) {
            while (remaining > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (n < 0) {
                    break;
                }
                remaining -= n;
                total[0] += n;
                if (total[0] > options.maxExtractedBytes) {
                    throw new IOException("skill extracted content too large: " + total[0] + " bytes");
                }
                out.write(buf, 0, n);
            }
            if (maxBytes != Long.MAX_VALUE && remaining > 0) {
                throw new IOException("unexpected end of tar entry: " + target.getFileName());
            }
        }
    }

    private static String safeSkillDirName(SkillRef skill) throws IOException {
        String[] candidates = new String[] {skill.getName(), skill.getDisplayName(), skill.idValue()};
        for (String candidate : candidates) {
            if (candidate != null && candidate.matches("[A-Za-z0-9._-]+") && !".".equals(candidate) && !"..".equals(candidate)) {
                return candidate;
            }
        }
        throw new IOException("invalid skill name: " + skill.getName());
    }

    private static Path safeJoin(Path root, String name) throws IOException {
        if (name == null || name.isEmpty() || Paths.get(name).isAbsolute()) {
            throw new IOException("invalid archive path: " + name);
        }
        Path normalized = root.resolve(name).normalize();
        if (!normalized.startsWith(root.normalize())) {
            throw new IOException("archive path escapes skill dir: " + name);
        }
        return normalized;
    }

    private static Path installSourceDir(Path tmp) throws IOException {
        List<Path> entries = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(tmp)) {
            stream.forEach(entries::add);
        }
        if (entries.size() == 1 && Files.isDirectory(entries.get(0))) {
            return entries.get(0);
        }
        return tmp;
    }

    private static Path replaceSkillDir(Path source, Path target) throws IOException {
        if (!Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Files.move(source, target);
            return null;
        }
        Path backup = Files.createTempDirectory(target.getParent(), "." + target.getFileName() + "-backup-");
        Files.delete(backup);
        Files.move(target, backup);
        try {
            Files.move(source, target);
        } catch (IOException error) {
            try {
                Files.move(backup, target);
            } catch (IOException rollbackError) {
                throw new IOException("replace skill: " + error.getMessage()
                        + "; rollback: " + rollbackError.getMessage(), error);
            }
            throw error;
        }
        return backup;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static class Options {
        private String workdir;
        private String skillsDir;
        private long maxArchiveBytes = DEFAULT_MAX_ARCHIVE_BYTES;
        private long maxExtractedBytes = DEFAULT_MAX_EXTRACTED_BYTES;
        private int maxArchiveEntries = DEFAULT_MAX_ARCHIVE_ENTRIES;

        public Options(String workdir) {
            this.workdir = workdir;
        }

        public Options skillsDir(String skillsDir) {
            this.skillsDir = skillsDir;
            return this;
        }

        public Options maxArchiveBytes(long maxArchiveBytes) {
            this.maxArchiveBytes = maxArchiveBytes;
            return this;
        }

        public Options maxExtractedBytes(long maxExtractedBytes) {
            this.maxExtractedBytes = maxExtractedBytes;
            return this;
        }

        public Options maxArchiveEntries(int maxArchiveEntries) {
            this.maxArchiveEntries = maxArchiveEntries;
            return this;
        }
    }
}
