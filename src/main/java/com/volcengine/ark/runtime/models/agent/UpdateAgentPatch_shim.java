// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.models.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Supplier;

/**
 * 手工维护的 UpdateAgent patch 辅助类型。
 *
 * <p>{@code _shim} 后缀用于和 codegen 管理的模型文件区分。
 *
 * <p>empty-array 语义仅覆盖 multiagent patch 子树中的 {@code agents} 和
 * {@code predefined_agents}。顶层 {@code tools} / {@code skills} /
 * {@code mcp_servers} 仍沿用生成模型的 {@code NON_EMPTY} 行为，显式空数组会被省略。
 *
 * <p>{@link Request} 的普通 fluent setter 继承自 {@link UpdateAgentRequest}，
 * 调用后返回类型会变成父类。需要继续调用 patch 专用方法时，请以
 * {@code multiagent(...)} 结束链式调用，或使用
 * {@link #removeMultiagent(UpdateAgentRequest)} / {@link #omitMultiagent(UpdateAgentRequest)}。
 */
public final class UpdateAgentPatch_shim {
    private UpdateAgentPatch_shim() {
    }

    public static Request request(Integer version) {
        Request request = new Request();
        request.setVersion(version);
        return request;
    }

    public static Request from(UpdateAgentRequest source) {
        Request target = new Request();
        copyDeclaredFields(source, target);
        return target;
    }

    public static Multiagent multiagent(String type) {
        return new Multiagent().type(type);
    }

    public static Subagents subagents(String type) {
        return new Subagents().type(type);
    }

    public static InlineAgents inlineAgents(String type) {
        return new InlineAgents().type(type);
    }

    public static <T extends UpdateAgentRequest> T removeMultiagent(T request) {
        request.setMultiagent(NullMultiagent_shim.INSTANCE);
        return request;
    }

    public static <T extends UpdateAgentRequest> T omitMultiagent(T request) {
        request.setMultiagent(null);
        return request;
    }

    private static void copyDeclaredFields(Object source, Object target) {
        try {
            for (Class<?> type = source.getClass(); type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    field.setAccessible(true);
                    field.set(target, field.get(source));
                }
            }
        } catch (ReflectiveOperationException err) {
            throw new IllegalStateException("cannot copy UpdateAgentRequest fields", err);
        }
    }

    @JsonDeserialize(using = RequestDeserializer_shim.class)
    public static final class Request extends UpdateAgentRequest {
        public Request removeMultiagent() {
            setMultiagent(NullMultiagent_shim.INSTANCE);
            return this;
        }

        public Request omitMultiagent() {
            setMultiagent(null);
            return this;
        }

        @Override
        public Request multiagent(MultiagentPatch multiagent) {
            setMultiagent(multiagent);
            return this;
        }

        @Override
        @JsonProperty(JSON_PROPERTY_MULTIAGENT)
        @JsonSerialize(using = MultiagentSerializer_shim.class)
        public MultiagentPatch getMultiagent() {
            return super.getMultiagent();
        }
    }

    @JsonSerialize(using = MultiagentSerializer_shim.class)
    public static final class Multiagent extends MultiagentPatch {
        private String typeValue;
        private FieldValue<List<AgentRef>> agentsValue = FieldValue.omitted();
        private FieldValue<MultiagentSubagentsConfig> subagentsValue = FieldValue.omitted();

        @Override
        public Multiagent type(MultiagentType type) {
            super.setType(type);
            this.typeValue = type == null ? null : type.getValue();
            return this;
        }

        public Multiagent type(String type) {
            return type(MultiagentType.fromValue(type));
        }

        @Override
        public void setType(MultiagentType type) {
            type(type);
        }

        public String getTypeValue() {
            return typeValue;
        }

        @Override
        public Multiagent agents(List<AgentRef> agents) {
            if (agents == null) {
                throw new IllegalArgumentException("agents must be an array");
            }
            super.setAgents(agents);
            this.agentsValue = FieldValue.of(agents);
            return this;
        }

        @Override
        public void setAgents(List<AgentRef> agents) {
            agents(agents);
        }

        @Override
        public Multiagent addAgentsItem(AgentRef agentsItem) {
            super.addAgentsItem(agentsItem);
            this.agentsValue = FieldValue.of(getAgents());
            return this;
        }

        public Multiagent omitAgents() {
            super.setAgents(null);
            this.agentsValue = FieldValue.omitted();
            return this;
        }

        public boolean hasAgents() {
            return agentsValue.present;
        }

        @Override
        public Multiagent subagents(MultiagentSubagentsConfig subagents) {
            if (subagents == null) {
                throw new IllegalArgumentException(
                        "subagents must not be null; use explicitNullSubagents() or omitSubagents()");
            }
            super.setSubagents(subagents);
            this.subagentsValue = FieldValue.of(subagents);
            return this;
        }

        @Override
        public void setSubagents(MultiagentSubagentsConfig subagents) {
            subagents(subagents);
        }

        public Multiagent explicitNullSubagents() {
            super.setSubagents(null);
            this.subagentsValue = FieldValue.explicitNull();
            return this;
        }

        public Multiagent omitSubagents() {
            super.setSubagents(null);
            this.subagentsValue = FieldValue.omitted();
            return this;
        }

        public boolean hasSubagents() {
            return subagentsValue.present;
        }

        public boolean subagentsExplicitNull() {
            return subagentsValue.explicitNull;
        }

        public MultiagentSubagentsConfig getSubagentsValue() {
            return subagentsValue.value;
        }
    }

    @JsonSerialize(using = SubagentsSerializer_shim.class)
    public static final class Subagents extends MultiagentSubagentsConfig {
        private String typeValue;
        private FieldValue<MultiagentInlineAgentsConfig> inlineAgentsValue = FieldValue.omitted();
        private FieldValue<List<AgentRef>> predefinedAgentsValue = FieldValue.omitted();

        @Override
        public Subagents type(MultiagentFeatureType type) {
            super.setType(type);
            this.typeValue = type == null ? null : type.getValue();
            return this;
        }

        public Subagents type(String type) {
            return type(MultiagentFeatureType.fromValue(type));
        }

        @Override
        public void setType(MultiagentFeatureType type) {
            type(type);
        }

        public String getTypeValue() {
            return typeValue;
        }

        @Override
        public Subagents inlineAgents(MultiagentInlineAgentsConfig inlineAgents) {
            if (inlineAgents == null) {
                throw new IllegalArgumentException(
                        "inlineAgents must not be null; use explicitNullInlineAgents() or omitInlineAgents()");
            }
            super.setInlineAgents(inlineAgents);
            this.inlineAgentsValue = FieldValue.of(inlineAgents);
            return this;
        }

        @Override
        public void setInlineAgents(MultiagentInlineAgentsConfig inlineAgents) {
            inlineAgents(inlineAgents);
        }

        public Subagents explicitNullInlineAgents() {
            super.setInlineAgents(null);
            this.inlineAgentsValue = FieldValue.explicitNull();
            return this;
        }

        public Subagents omitInlineAgents() {
            super.setInlineAgents(null);
            this.inlineAgentsValue = FieldValue.omitted();
            return this;
        }

        public boolean hasInlineAgents() {
            return inlineAgentsValue.present;
        }

        public boolean inlineAgentsExplicitNull() {
            return inlineAgentsValue.explicitNull;
        }

        @Override
        public Subagents predefinedAgents(List<AgentRef> predefinedAgents) {
            if (predefinedAgents == null) {
                throw new IllegalArgumentException(
                        "predefinedAgents must not be null; use explicitNullPredefinedAgents() "
                                + "or omitPredefinedAgents()");
            }
            super.setPredefinedAgents(predefinedAgents);
            this.predefinedAgentsValue = FieldValue.of(predefinedAgents);
            return this;
        }

        @Override
        public void setPredefinedAgents(List<AgentRef> predefinedAgents) {
            predefinedAgents(predefinedAgents);
        }

        @Override
        public Subagents addPredefinedAgentsItem(AgentRef predefinedAgentsItem) {
            super.addPredefinedAgentsItem(predefinedAgentsItem);
            this.predefinedAgentsValue = FieldValue.of(getPredefinedAgents());
            return this;
        }

        public Subagents explicitNullPredefinedAgents() {
            super.setPredefinedAgents(null);
            this.predefinedAgentsValue = FieldValue.explicitNull();
            return this;
        }

        public Subagents omitPredefinedAgents() {
            super.setPredefinedAgents(null);
            this.predefinedAgentsValue = FieldValue.omitted();
            return this;
        }

        public boolean hasPredefinedAgents() {
            return predefinedAgentsValue.present;
        }

        public boolean predefinedAgentsExplicitNull() {
            return predefinedAgentsValue.explicitNull;
        }

        public List<AgentRef> getPredefinedAgentsValue() {
            return predefinedAgentsValue.value;
        }
    }

    @JsonSerialize(using = InlineAgentsSerializer_shim.class)
    public static final class InlineAgents extends MultiagentInlineAgentsConfig {
        private String typeValue;

        @Override
        public InlineAgents type(MultiagentFeatureType type) {
            super.setType(type);
            this.typeValue = type == null ? null : type.getValue();
            return this;
        }

        public InlineAgents type(String type) {
            return type(MultiagentFeatureType.fromValue(type));
        }

        @Override
        public void setType(MultiagentFeatureType type) {
            type(type);
        }

        public String getTypeValue() {
            return typeValue;
        }
    }

    private static final class FieldValue<T> {
        private final T value;
        private final boolean present;
        private final boolean explicitNull;

        private FieldValue(T value, boolean present, boolean explicitNull) {
            this.value = value;
            this.present = present;
            this.explicitNull = explicitNull;
        }

        private static <T> FieldValue<T> of(T value) {
            return new FieldValue<>(value, true, value == null);
        }

        private static <T> FieldValue<T> explicitNull() {
            return new FieldValue<>(null, true, true);
        }

        private static <T> FieldValue<T> omitted() {
            return new FieldValue<>(null, false, false);
        }
    }

    private static final class RequestDeserializer_shim extends JsonDeserializer<Request> {
        @Override
        public Request deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectMapper mapper = (ObjectMapper) parser.getCodec();
            JsonNode root = mapper.readTree(parser);
            if (!root.isObject()) {
                throw new JsonMappingException(parser, "expected UpdateAgentPatch Request object");
            }
            RawRequest raw = new RawRequest();
            JsonNode withoutMultiagent = root.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) withoutMultiagent).remove("multiagent");
            mapper.readerForUpdating(raw).readValue(withoutMultiagent);
            Request request = new Request();
            copyDeclaredFields(raw, request);
            if (root.has("multiagent")) {
                request.setMultiagent(parseMultiagent(root.get("multiagent"), mapper, parser));
            }
            return request;
        }

        private static final class RawRequest extends UpdateAgentRequest {
        }
    }

    @JsonSerialize(using = NullValueSerializer_shim.class)
    private static final class NullMultiagent_shim extends MultiagentPatch {
        private static final NullMultiagent_shim INSTANCE = new NullMultiagent_shim();
    }

    private static final class MultiagentSerializer_shim extends JsonSerializer<MultiagentPatch> {
        @Override
        public void serialize(MultiagentPatch value, com.fasterxml.jackson.core.JsonGenerator generator,
                              SerializerProvider provider) throws IOException {
            if (value == null || value instanceof NullMultiagent_shim) {
                generator.writeNull();
                return;
            }
            if (value instanceof Multiagent) {
                Multiagent multiagent = (Multiagent) value;
                generator.writeStartObject();
                generator.writeStringField(
                        "type",
                        requiredType(typeValue(multiagent.typeValue, multiagent.getType()), "multiagent"));
                writeField(generator, "agents", multiagent.agentsValue);
                writeField(generator, "subagents", multiagent.subagentsValue);
                generator.writeEndObject();
                return;
            }
            generator.writeObject(value);
        }
    }

    private static final class SubagentsSerializer_shim extends JsonSerializer<Subagents> {
        @Override
        public void serialize(Subagents value, com.fasterxml.jackson.core.JsonGenerator generator,
                              SerializerProvider provider) throws IOException {
            generator.writeStartObject();
            generator.writeStringField(
                    "type", requiredType(typeValue(value.typeValue, value.getType()), "subagents"));
            writeField(generator, "inline_agents", value.inlineAgentsValue);
            writeField(generator, "predefined_agents", value.predefinedAgentsValue);
            generator.writeEndObject();
        }
    }

    private static final class InlineAgentsSerializer_shim extends JsonSerializer<InlineAgents> {
        @Override
        public void serialize(InlineAgents value, com.fasterxml.jackson.core.JsonGenerator generator,
                              SerializerProvider provider) throws IOException {
            generator.writeStartObject();
            generator.writeStringField(
                    "type", requiredType(typeValue(value.typeValue, value.getType()), "inline_agents"));
            generator.writeEndObject();
        }
    }

    private static String typeValue(String value, MultiagentType fallback) {
        if (value != null) {
            return value;
        }
        return fallback == null ? null : fallback.getValue();
    }

    private static String typeValue(String value, MultiagentFeatureType fallback) {
        if (value != null) {
            return value;
        }
        return fallback == null ? null : fallback.getValue();
    }

    private static String requiredType(String type, String owner) {
        if (type == null) {
            throw new IllegalStateException(owner + " type is required");
        }
        return type;
    }

    private static final class NullValueSerializer_shim extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, com.fasterxml.jackson.core.JsonGenerator generator,
                              SerializerProvider provider) throws IOException {
            generator.writeNull();
        }
    }

    private static <T> void writeField(com.fasterxml.jackson.core.JsonGenerator generator, String name,
                                       FieldValue<T> value) throws IOException {
        if (!value.present) {
            return;
        }
        generator.writeFieldName(name);
        if (value.explicitNull) {
            generator.writeNull();
        } else {
            generator.writeObject(value.value);
        }
    }

    private static MultiagentPatch parseMultiagent(JsonNode node, ObjectMapper mapper,
                                                    JsonParser parser) throws IOException {
        if (node.isNull()) {
            return NullMultiagent_shim.INSTANCE;
        }
        if (!node.isObject() || !node.hasNonNull("type")) {
            throw new JsonMappingException(parser, "expected MultiagentPatch object with type");
        }
        String type = node.get("type").asText();
        Multiagent multiagent = parseFactory(
                parser, "multiagent", type, () -> multiagent(type));
        if (node.has("agents")) {
            JsonNode agents = node.get("agents");
            if (agents.isNull()) {
                throw new JsonMappingException(parser, "agents must be an array");
            }
            multiagent.agents(mapper.convertValue(agents, new TypeReference<List<AgentRef>>() {
            }));
        }
        if (node.has("subagents")) {
            JsonNode subagents = node.get("subagents");
            if (subagents.isNull()) {
                multiagent.explicitNullSubagents();
            } else {
                multiagent.subagents(parseSubagents(subagents, mapper, parser));
            }
        }
        return multiagent;
    }

    private static Subagents parseSubagents(JsonNode node, ObjectMapper mapper,
                                             JsonParser parser) throws IOException {
        if (!node.isObject() || !node.hasNonNull("type")) {
            throw new JsonMappingException(parser, "expected Subagents object with type");
        }
        String type = node.get("type").asText();
        Subagents subagents = parseFactory(
                parser, "subagents", type, () -> subagents(type));
        if (node.has("inline_agents")) {
            JsonNode inlineAgents = node.get("inline_agents");
            if (inlineAgents.isNull()) {
                subagents.explicitNullInlineAgents();
            } else if (!inlineAgents.isObject() || !inlineAgents.hasNonNull("type")) {
                throw new JsonMappingException(parser, "expected InlineAgents object with type");
            } else {
                String inlineType = inlineAgents.get("type").asText();
                subagents.inlineAgents(parseFactory(
                        parser, "inline_agents", inlineType, () -> inlineAgents(inlineType)));
            }
        }
        if (node.has("predefined_agents")) {
            JsonNode predefinedAgents = node.get("predefined_agents");
            if (predefinedAgents.isNull()) {
                subagents.explicitNullPredefinedAgents();
            } else {
                subagents.predefinedAgents(mapper.convertValue(
                        predefinedAgents,
                        new TypeReference<List<AgentRef>>() {
                        }));
            }
        }
        return subagents;
    }

    private static <T> T parseFactory(JsonParser parser, String owner, String type, Supplier<T> factory)
            throws JsonMappingException {
        try {
            return factory.get();
        } catch (IllegalArgumentException error) {
            throw new JsonMappingException(parser, "unknown " + owner + " type '" + type + "'", error);
        }
    }
}
