// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.exception;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class ArkAPIError {

    ArkErrorDetails error;

    public ArkAPIError(ArkErrorDetails error) {
        this.error = error;
    }

    public ArkAPIError() {}

    public ArkErrorDetails getError() {
        return error;
    }

    public void setError(ArkErrorDetails error) {
        this.error = error;
    }

    /**
     * Parses both the standard {"error": {...}} envelope and services that
     * return the error details directly. Unknown response shapes retain the
     * raw body as the exception message instead of producing a null error.
     */
    public static ArkAPIError fromResponseBody(ObjectMapper mapper, String responseBody, String fallbackMessage) {
        if (responseBody != null && !responseBody.trim().isEmpty()) {
            try {
                JsonNode root = mapper.readTree(responseBody);
                if (root != null && root.isObject()) {
                    JsonNode detailsNode = root.get("error");
                    if (detailsNode == null && root.has("message")) {
                        detailsNode = root;
                    }
                    if (detailsNode != null && detailsNode.isObject()) {
                        ArkErrorDetails details = mapper.treeToValue(detailsNode, ArkErrorDetails.class);
                        if (details != null) {
                            return new ArkAPIError(details);
                        }
                    }
                }
            } catch (IOException ignored) {
                // Fall through and preserve the raw response body.
            }
        }

        String message = responseBody;
        if (message == null || message.trim().isEmpty()) {
            message = fallbackMessage;
        }
        if (message == null || message.trim().isEmpty()) {
            message = "HTTP request failed with an empty response body";
        }
        return new ArkAPIError(new ArkErrorDetails(message, "", "", "HTTPError"));
    }

    @Override
    public String toString() {
        return "ArkAPIError{" +
                "error=" + error +
                '}';
    }

    public static class ArkErrorDetails {

        String message;

        String type;

        String param;

        String code;

        public ArkErrorDetails(String message, String type, String param, String code) {
            this.message = message;
            this.type = type;
            this.param = param;
            this.code = code;
        }

        public ArkErrorDetails(){}

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getParam() {
            return param;
        }

        public void setParam(String param) {
            this.param = param;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        @Override
        public String toString() {
            return "ArkErrorDetails{" +
                    "message='" + message + '\'' +
                    ", type='" + type + '\'' +
                    ", param='" + param + '\'' +
                    ", code='" + code + '\'' +
                    '}';
        }
    }
}
