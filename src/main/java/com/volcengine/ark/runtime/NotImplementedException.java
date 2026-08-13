// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime;

/**
 * Thrown when a non-responses API method is called on ark-runtime-java.
 * This SDK currently ships only the Responses API. Other APIs will be
 * un-stubbed as ark-apis adds codegen for them.
 *
 * See ROADMAP section in README.md.
 */
public final class NotImplementedException extends UnsupportedOperationException {
    public NotImplementedException(String api) {
        super(api + ": not implemented in ark-runtime-java. this sdk currently ships only the responses api, backed by ark-apis generated models. see the ROADMAP section in README.md.");
    }
}
