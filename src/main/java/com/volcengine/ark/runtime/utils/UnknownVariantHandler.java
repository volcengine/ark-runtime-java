// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0
package com.volcengine.ark.runtime.utils;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;

/** Only generated, opted-in response unions receive tolerant subtype decoding. */
public final class UnknownVariantHandler extends DeserializationProblemHandler {
    @Override
    public JavaType handleUnknownTypeId(DeserializationContext context, JavaType baseType,
            String subTypeId, TypeIdResolver resolver, String failureMessage) {
        UnknownVariantFallback fallback = baseType.getRawClass().getAnnotation(UnknownVariantFallback.class);
        if (fallback == null || subTypeId == null || subTypeId.isEmpty()
                || context.getParser().currentToken() != JsonToken.VALUE_STRING) {
            return null;
        }
        if (!baseType.getRawClass().isAssignableFrom(fallback.value())) {
            throw new IllegalStateException("Invalid unknown variant fallback for " + baseType);
        }
        return context.constructType(fallback.value());
    }
}
