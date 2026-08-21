// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.volcengine.ark.runtime.Const;
import com.volcengine.ark.runtime.exception.ArkHttpException;
import io.reactivex.Completable;
import io.reactivex.Single;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.Test;
import retrofit2.HttpException;
import retrofit2.Response;

public class ArkServiceErrorTest {
    @Test
    public void singlePreservesNonstandardBodyStatusAndServerRequestId() {
        HttpException source = httpException("{\"detail\":\"model is invalid\"}", "server-request-id");

        try {
            ArkService.execute(Single.error(source));
            fail("expected ArkHttpException");
        } catch (ArkHttpException error) {
            assertEquals(400, error.statusCode);
            assertEquals("server-request-id", error.requestId);
            assertTrue(error.getMessage().contains("model is invalid"));
        }
    }

    @Test
    public void completablePreservesNonstandardBodyStatusAndServerRequestId() {
        HttpException source = httpException("bad request", "server-request-id");

        try {
            ArkService.execute(Completable.error(source));
            fail("expected ArkHttpException");
        } catch (ArkHttpException error) {
            assertEquals(400, error.statusCode);
            assertEquals("server-request-id", error.requestId);
            assertTrue(error.getMessage().contains("bad request"));
        }
    }

    private static HttpException httpException(String body, String requestId) {
        Request request = new Request.Builder()
                .url("https://example.com/api/v3/tokenization")
                .header(Const.CLIENT_REQUEST_HEADER, "client-request-id")
                .build();
        okhttp3.Response rawResponse = new okhttp3.Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Bad Request")
                .header(Const.SERVER_REQUEST_HEADER, requestId)
                .build();
        ResponseBody responseBody = ResponseBody.create(
                MediaType.get("application/json"), body);
        Response<Object> response = Response.error(responseBody, rawResponse);
        return new HttpException(response);
    }
}
