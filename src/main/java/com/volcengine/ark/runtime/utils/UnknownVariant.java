// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0
package com.volcengine.ark.runtime.utils;

import com.fasterxml.jackson.databind.JsonNode;

/** An unrecognized response union member, retained instead of discarded. */
public interface UnknownVariant {
    /** The original wire discriminator, such as a future event or item type. */
    String getDiscriminatorValue();

    /** A defensive copy of the complete JSON object, including the discriminator. */
    JsonNode getRawJson();
}
