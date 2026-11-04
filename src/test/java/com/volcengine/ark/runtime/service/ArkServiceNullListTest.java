// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.volcengine.ark.runtime.models.file.FileListRequest;
import com.volcengine.ark.runtime.models.file.FileListResponse;
import com.volcengine.ark.runtime.models.session.ListSessionResourcesResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class ArkServiceNullListTest {
    @Test
    public void nullAndEmptyPagesReturnEmptyLists() {
        for (String body : new String[]{"null", " \n null \t", "{\"data\":null}", "{\"data\":[]}"}) {
            ArkService service = service(body, 200);
            FileListResponse files = service.listFiles(new FileListRequest());
            assertNotNull(files);
            assertNotNull(files.getData());
            assertTrue(files.getData().isEmpty());
            ListSessionResourcesResponse resources = service.listSessionResources("session-1");
            assertNotNull(resources);
            assertNotNull(resources.getData());
            assertTrue(resources.getData().isEmpty());
        }
    }

    @Test
    public void preservesPaginationAndItems() {
        ArkService service = service("{\"object\":\"file\",\"data\":[{\"id\":\"file-1\"}],"
            + "\"first_id\":\"file-1\",\"last_id\":\"file-1\",\"has_more\":true}", 200);
        FileListResponse files = service.listFiles(new FileListRequest());
        assertEquals("file-1", files.getData().get(0).getId());
        assertEquals("file-1", files.getFirstId());
        assertEquals("file-1", files.getLastId());
        assertEquals(Boolean.TRUE, files.getHasMore());
        ListSessionResourcesResponse resources = service(
            "{\"data\":[{\"id\":\"resource-1\",\"type\":\"file\",\"file_id\":\"file-1\"}]}", 200)
            .listSessionResources("session-1");
        assertEquals("resource-1", resources.getData().get(0).getId());
    }

    @Test
    public void nullDataPreservesOtherFields() {
        FileListResponse files = service("{\"data\":null,\"first_id\":\"cursor\",\"has_more\":true}", 200)
            .listFiles(new FileListRequest());
        assertTrue(files.getData().isEmpty());
        assertEquals("cursor", files.getFirstId());
        assertEquals(Boolean.TRUE, files.getHasMore());
    }

    @Test
    public void errorsAndOtherModelsKeepTheirBehavior() {
        assertFails(() -> service("null", 400).listFiles(new FileListRequest()));
        assertFails(() -> service("null", 400).listSessionResources("session-1"));
        assertFails(() -> service("null", 200).retrieveFile("file-1"));
        assertFails(() -> service("{\"data\":\"invalid\"}", 200).listFiles(new FileListRequest()));
        assertFails(() -> service("{\"data\":\"invalid\"}", 200).listSessionResources("session-1"));
        assertFails(() -> service("null {}", 200).listFiles(new FileListRequest()));
    }

    @Test
    public void duplicateFieldsKeepTheirOriginalValidation() {
        assertFails(() -> service("{\"data\":\"invalid\",\"data\":null}", 200).listFiles(new FileListRequest()));
        assertFails(() -> service("{\"data\":\"invalid\",\"data\":null}", 200).listSessionResources("session-1"));
        assertFails(() -> service("{\"data\":null,\"has_more\":\"invalid\",\"has_more\":false}", 200)
            .listFiles(new FileListRequest()));
        assertTrue(service("{\"data\":[],\"data\":[]}", 200).listFiles(new FileListRequest()).getData().isEmpty());
        assertTrue(service("{\"data\":[],\"data\":[]}", 200).listSessionResources("session-1").getData().isEmpty());
        assertEquals(Boolean.FALSE, service("{\"data\":null,\"has_more\":true,\"has_more\":false}", 200)
            .listFiles(new FileListRequest()).getHasMore());
    }

    @Test
    public void nullTokenOffsetsPreserveUnicodeAndWhitespace() {
        String body = "{\"extra\":\"中文\",\"data\": \n null}";
        assertTrue(service(body, 200).listFiles(new FileListRequest()).getData().isEmpty());
        assertTrue(service(body, 200).listSessionResources("session-1").getData().isEmpty());
        assertFails(() -> service("\u000bnull", 200).listFiles(new FileListRequest()));
    }

    private static void assertFails(Runnable call) {
        try {
            call.run();
            fail("expected response failure");
        } catch (RuntimeException expected) {
            // The compatibility adapter must not turn errors into empty pages.
        }
    }

    private static ArkService service(String body, int status) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
            .request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("test")
            .body(ResponseBody.create(MediaType.get("application/json"), body)).build()).build();
        ArkApi api = ArkService.defaultRetrofit(client, ArkService.defaultObjectMapper(),
            "https://example.test/", null).create(ArkApi.class);
        return new ArkService(api);
    }
}
