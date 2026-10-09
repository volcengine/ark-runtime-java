// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0
package com.volcengine.ark.runtime.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import java.io.IOException;

/** Shared immutable storage/serialization for generated unknown response variants. */
public abstract class UnknownVariantSupport implements UnknownVariant {
    private final String discriminatorValue;
    private final JsonNode rawJson;

    protected UnknownVariantSupport(String discriminator, JsonNode rawJson) {
        if (!hasDiscriminator(rawJson, discriminator)) {
            throw new IllegalArgumentException("Expected an object with nonempty string discriminator: " + discriminator);
        }
        this.discriminatorValue = rawJson.get(discriminator).textValue();
        this.rawJson = rawJson.deepCopy();
    }

    public static boolean hasDiscriminator(JsonNode node, String discriminator) {
        return node != null && node.isObject() && node.has(discriminator)
                && node.get(discriminator).isTextual() && !node.get(discriminator).textValue().isEmpty();
    }

    @Override
    public final String getDiscriminatorValue() {
        return discriminatorValue;
    }

    @Override
    public final JsonNode getRawJson() {
        return rawJson.deepCopy();
    }

    public static final class Serializer extends JsonSerializer<UnknownVariant> {
        @Override
        public void serialize(UnknownVariant value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeTree(value.getRawJson());
        }

        @Override
        public void serializeWithType(UnknownVariant value, JsonGenerator generator,
                SerializerProvider provider, TypeSerializer typeSerializer) throws IOException {
            // Raw JSON already contains the original discriminator. Do not emit a
            // generated sentinel or Jackson class id when writing via an interface.
            serialize(value, generator, provider);
        }
    }
}
