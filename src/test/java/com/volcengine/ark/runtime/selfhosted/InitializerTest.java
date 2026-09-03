// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class InitializerTest {
    @Test
    public void constructorRejectsMissingDependenciesAndWorkdir() {
        SelfHostedClient client = new SelfHostedClient("test-key");

        assertInvalidInitializer(() -> new Initializer(null, new Initializer.Options("/tmp")), "api");
        assertInvalidInitializer(() -> new Initializer(client, null), "options");
        assertInvalidInitializer(() -> new Initializer(client, new Initializer.Options(" ")), "workdir");
    }

    @Test
    public void zipArchiveEntryLimitIsEnforced() throws Exception {
        byte[] archive = zipWithTwoEntries();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            if (request.url().encodedPath().equals("/api/v3/skills/skill-1")) {
                return response(
                        request,
                        MediaType.parse("application/json"),
                        ("{\"id\":\"skill-1\",\"object\":\"skill\",\"created_at\":1,"
                                + "\"name\":\"demo\",\"latest_version\":\"1\"}")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return response(request, MediaType.parse("application/zip"), archive);
        }).build();
        SelfHostedClient client = new SelfHostedClient.Builder()
                .apiKey("test-key")
                .baseUrl("https://ark.example.com/api/v3")
                .httpClient(http)
                .build();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("skill_id", "skill-1");
        raw.put("version", "1");
        Initializer initializer = new Initializer(
                client,
                new Initializer.Options(Files.createTempDirectory("ark-java-skill-").toString())
                        .maxArchiveEntries(1));

        try {
            initializer.installSkill("session-1", SkillRef.fromMap(raw));
        } catch (IOException error) {
            assertTrue(error.getMessage().contains("too many entries"));
            return;
        }
        throw new AssertionError("expected archive entry limit failure");
    }

    @Test
    public void cleanupRemovesOnlyInstalledSkills() throws Exception {
        byte[] archive = zipWithTwoEntries();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request();
            if (request.url().encodedPath().equals("/api/v3/skills/skill-1")) {
                return response(
                        request,
                        MediaType.parse("application/json"),
                        ("{\"id\":\"skill-1\",\"object\":\"skill\",\"created_at\":1,"
                                + "\"name\":\"demo\",\"latest_version\":\"1\"}")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return response(request, MediaType.parse("application/zip"), archive);
        }).build();
        SelfHostedClient client = new SelfHostedClient.Builder()
                .apiKey("test-key")
                .baseUrl("https://ark.example.com/api/v3")
                .httpClient(http)
                .build();
        Path root = Files.createTempDirectory("ark-java-skill-cleanup-");
        Initializer initializer = new Initializer(client, new Initializer.Options(root.toString()));
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("skill_id", "skill-1");
        raw.put("version", "1");

        initializer.installSkill("session-1", SkillRef.fromMap(raw));
        Path retained = Files.createDirectories(root.resolve("skills").resolve("retained"));
        initializer.cleanup();

        assertFalse(Files.exists(root.resolve("skills").resolve("demo")));
        assertTrue(Files.isDirectory(retained));
    }

    @Test
    public void replaceSkillRollsBackOldVersionWhenCommitFails() throws Exception {
        Path root = Files.createTempDirectory("ark-java-skill-rollback-");
        Path source = root.resolve("missing-new-skill");
        Path target = Files.createDirectory(root.resolve("installed-skill"));
        Files.write(
                target.resolve("marker.txt"),
                "old".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Method replace = Initializer.class.getDeclaredMethod("replaceSkillDir", Path.class, Path.class);
        replace.setAccessible(true);

        try {
            replace.invoke(null, source, target);
        } catch (InvocationTargetException error) {
            assertTrue(error.getCause() instanceof IOException);
        }

        assertEquals(
                "old",
                new String(
                        Files.readAllBytes(target.resolve("marker.txt")),
                        java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] zipWithTwoEntries() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("one"));
            zip.write('1');
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("two"));
            zip.write('2');
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static void assertInvalidInitializer(Runnable create, String message) {
        try {
            create.run();
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains(message));
            return;
        }
        throw new AssertionError("expected invalid initializer");
    }

    private static Response response(Request request, MediaType type, byte[] body) throws IOException {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(type, body))
                .build();
    }
}
