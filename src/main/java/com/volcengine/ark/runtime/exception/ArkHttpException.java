// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.exception;


public class ArkHttpException extends RuntimeException {
    public static Integer INTERNAL_SERVICE_CODE = 500;

    public final int statusCode;

    public final String code;

    public final String param;

    public final String type;

    public final String requestId;

    public ArkHttpException(ArkAPIError error, Exception parent, int statusCode, String requestId) {
        super(errorMessage(error), parent);
        ArkAPIError.ArkErrorDetails details = errorDetails(error);
        this.statusCode = statusCode;
        this.code = details.getCode();
        this.param = details.getParam();
        this.type = details.getType();
        this.requestId = requestId;
    }

    private static String errorMessage(ArkAPIError error) {
        return errorDetails(error).getMessage();
    }

    private static ArkAPIError.ArkErrorDetails errorDetails(ArkAPIError error) {
        if (error != null && error.getError() != null) {
            return error.getError();
        }
        return new ArkAPIError.ArkErrorDetails(
                "HTTP request failed without error details", "", "", "HTTPError");
    }

    public String getMessage() {
        return this.toString();
    }

    @Override
    public String toString() {
        return "ArkHttpException{" +
                "statusCode=" + statusCode +
                ", message='" + super.getMessage() + '\'' +
                ", code='" + code + '\'' +
                ", param='" + param + '\'' +
                ", type='" + type + '\'' +
                ", requestId='" + requestId + '\'' +
                '}';
    }
}
