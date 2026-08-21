// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.exception;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class ArkAPIErrorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void parsesWrappedError() {
        ArkAPIError error = ArkAPIError.fromResponseBody(
                mapper,
                "{\"error\":{\"message\":\"model not found\",\"code\":\"InvalidModel\"}}",
                "fallback");

        assertEquals("model not found", error.getError().getMessage());
        assertEquals("InvalidModel", error.getError().getCode());
    }

    @Test
    public void parsesDirectError() {
        ArkAPIError error = ArkAPIError.fromResponseBody(
                mapper,
                "{\"message\":\"model not found\",\"code\":\"InvalidModel\"}",
                "fallback");

        assertEquals("model not found", error.getError().getMessage());
        assertEquals("InvalidModel", error.getError().getCode());
    }

    @Test
    public void preservesNonstandardJsonBody() {
        String body = "{\"detail\":\"model is invalid\"}";
        ArkAPIError error = ArkAPIError.fromResponseBody(mapper, body, "fallback");
        ArkHttpException exception = new ArkHttpException(error, null, 400, "request-id");

        assertEquals(body, error.getError().getMessage());
        assertEquals(400, exception.statusCode);
        assertEquals("request-id", exception.requestId);
        assertEquals("HTTPError", exception.code);
    }

    @Test
    public void preservesPlainTextAndEmptyBodies() {
        ArkAPIError plain = ArkAPIError.fromResponseBody(mapper, "bad gateway", "fallback");
        ArkAPIError empty = ArkAPIError.fromResponseBody(mapper, "", "HTTP 400");

        assertEquals("bad gateway", plain.getError().getMessage());
        assertEquals("HTTP 400", empty.getError().getMessage());
    }

    @Test
    public void exceptionConstructorHandlesMissingDetails() {
        ArkHttpException exception = new ArkHttpException(new ArkAPIError(), null, 400, "request-id");

        assertNotNull(exception.getMessage());
        assertEquals("HTTPError", exception.code);
        assertEquals("request-id", exception.requestId);
    }
}
