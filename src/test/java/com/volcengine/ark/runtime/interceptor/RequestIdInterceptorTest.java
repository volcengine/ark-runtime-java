// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.volcengine.ark.runtime.Const;
import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

public class RequestIdInterceptorTest {
    @Test
    public void outgoingRequestUsesProjectVersionAndPreservesRuntimeSuffix() throws Exception {
        Request request = new Request.Builder()
                .url("https://example.com/api/v3/chat/completions")
                .header("User-Agent", "custom-agent")
                .header(Const.CLIENT_REQUEST_HEADER, "provided-request-id")
                .build();
        Request outgoing = intercept(request);
        String version = XPathFactory.newInstance().newXPath().evaluate("/project/version",
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File("pom.xml")));
        assertTrue(!version.isEmpty());
        assertEquals("ark-runtime-java/" + version + "/(java-" + System.getProperty("java.version")
                + ";" + System.getProperty("os.name") + "-" + System.getProperty("os.version")
                + ";" + System.getProperty("os.arch") + ")", outgoing.header("User-Agent"));
        assertEquals(1, outgoing.headers("User-Agent").size());
        assertEquals("provided-request-id", outgoing.header(Const.CLIENT_REQUEST_HEADER));
    }

    @Test
    public void outgoingRequestStillGeneratesClientRequestId() throws Exception {
        Request outgoing = intercept(new Request.Builder().url("https://example.com").build());
        assertNotNull(outgoing.header(Const.CLIENT_REQUEST_HEADER));
        assertEquals(34, outgoing.header(Const.CLIENT_REQUEST_HEADER).length());
        assertTrue(outgoing.header("User-Agent").startsWith("ark-runtime-java/"));
    }

    private Request intercept(Request request) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new RequestIdInterceptor())
                .addInterceptor(chain -> new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(null, "{}"))
                        .build())
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.request();
        }
    }
}
