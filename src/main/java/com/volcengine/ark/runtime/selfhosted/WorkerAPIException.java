// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

public class WorkerAPIException extends RuntimeException {
    private final int statusCode;
    private final String requestId;

    public WorkerAPIException(int statusCode, String message, String requestId) {
        super("worker api status " + statusCode + ": " + message);
        this.statusCode = statusCode;
        this.requestId = requestId == null ? "" : requestId;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public static boolean isStatus(Throwable t, int statusCode) {
        return t instanceof WorkerAPIException && ((WorkerAPIException) t).getStatusCode() == statusCode;
    }

    public static boolean isFatal4xx(Throwable t) {
        if (!(t instanceof WorkerAPIException)) {
            return false;
        }
        int status = ((WorkerAPIException) t).getStatusCode();
        return status >= 400 && status < 500 && status != 408 && status != 409 && status != 412 && status != 429;
    }
}
