// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.volcengine.ark.runtime.models.agent.CustomToolInputSchema;
import com.volcengine.ark.runtime.models.agent.ToolItem;
import com.volcengine.ark.runtime.selfhosted.ContentBlock;
import com.volcengine.ark.runtime.selfhosted.Tool;
import com.volcengine.ark.runtime.selfhosted.ToolContext;
import com.volcengine.ark.runtime.selfhosted.ToolResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts protocol-independent MCP values into Ark Agent and worker values. */
public final class McpTools {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectMapper SCHEMA_OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    private static final String COMPATIBILITY_DESCRIPTION_PREFIX =
            "\n\nMCP input constraints (JSON Schema): ";
    private static final int MAX_CUSTOM_TOOL_DESCRIPTION_CHARS = 10_000;
    private static final Set<String> SUPPORTED_IMAGE_MIME_TYPES;
    private static final Set<String> IGNORED_TOP_LEVEL_SCHEMA_KEYWORDS;

    static {
        Set<String> values = new HashSet<String>();
        values.add("image/gif");
        values.add("image/jpeg");
        values.add("image/png");
        values.add("image/webp");
        SUPPORTED_IMAGE_MIME_TYPES = Collections.unmodifiableSet(values);

        Set<String> ignoredKeywords = new HashSet<String>();
        ignoredKeywords.add("$anchor");
        ignoredKeywords.add("$comment");
        ignoredKeywords.add("$dynamicAnchor");
        ignoredKeywords.add("$id");
        ignoredKeywords.add("$schema");
        ignoredKeywords.add("title");
        IGNORED_TOP_LEVEL_SCHEMA_KEYWORDS = Collections.unmodifiableSet(ignoredKeywords);
    }

    private McpTools() {
    }

    /** Converts an MCP tool definition into an Ark Agent custom tool declaration. */
    public static ToolItem customToolItem(McpToolDefinition tool) {
        requireTool(tool);
        SchemaConversion conversion = customToolInputSchema(tool.getInputSchema());
        String description = tool.getDescription();
        if (description == null || description.isEmpty()) {
            description = tool.getName();
        }
        if (!conversion.constraints.isEmpty()) {
            description += COMPATIBILITY_DESCRIPTION_PREFIX + conversion.constraints;
        }
        if (description.codePointCount(0, description.length()) > MAX_CUSTOM_TOOL_DESCRIPTION_CHARS) {
            throw new IllegalArgumentException(
                    "mcp tool " + tool.getName() + " description exceeds "
                            + MAX_CUSTOM_TOOL_DESCRIPTION_CHARS
                            + " characters after adding input constraints");
        }
        return new ToolItem()
                .type("custom")
                .name(tool.getName())
                .description(description)
                .inputSchema(conversion.schema);
    }

    /** Converts MCP tool definitions into Ark Agent custom tool declarations. */
    public static List<ToolItem> customToolItems(List<McpToolDefinition> tools) {
        if (tools == null) {
            return Collections.emptyList();
        }
        Map<String, ToolItem> items = new LinkedHashMap<String, ToolItem>();
        for (McpToolDefinition tool : tools) {
            ToolItem item = customToolItem(tool);
            if (items.containsKey(tool.getName())) {
                throw new IllegalArgumentException("duplicate mcp tool name: " + tool.getName());
            }
            items.put(tool.getName(), item);
        }
        return new ArrayList<ToolItem>(items.values());
    }

    /** Wraps an MCP tool as a self-hosted worker custom tool. */
    public static Tool mcpTool(McpToolDefinition tool, McpClient client) {
        requireTool(tool);
        if (client == null) {
            throw new IllegalArgumentException("mcp client is required");
        }
        return new GenericMcpTool(tool.getName(), client);
    }

    /** Wraps MCP tools for EnvironmentWorkerOptions.customTools. */
    public static Map<String, Tool> mcpTools(List<McpToolDefinition> tools, McpClient client) {
        if (tools == null) {
            return Collections.emptyMap();
        }
        Map<String, Tool> wrapped = new LinkedHashMap<String, Tool>();
        for (McpToolDefinition tool : tools) {
            Tool value = mcpTool(tool, client);
            if (wrapped.containsKey(value.name())) {
                throw new IllegalArgumentException("duplicate mcp tool name: " + value.name());
            }
            wrapped.put(value.name(), value);
        }
        return wrapped;
    }

    /** Converts a protocol-independent MCP call result into a worker result. */
    public static ToolResult toolResult(McpCallToolResult result) {
        if (result == null) {
            return ToolResult.error("mcp tool returned no result");
        }
        List<ContentBlock> blocks = new ArrayList<ContentBlock>();
        try {
            for (McpContent content : result.getContent()) {
                blocks.add(contentBlock(content));
            }
        } catch (IllegalArgumentException exception) {
            return ToolResult.error(exception.getMessage());
        }
        if (blocks.isEmpty() && result.getStructuredContent() != null) {
            try {
                blocks.add(new ContentBlock(
                        "text", OBJECT_MAPPER.writeValueAsString(result.getStructuredContent())));
            } catch (JsonProcessingException exception) {
                return ToolResult.error("serialize mcp structured content: " + exception.getMessage());
            }
        }
        if (blocks.isEmpty() && result.isError()) {
            blocks.add(new ContentBlock("text", "tool returned an error"));
        }
        return new ToolResult(blocks, result.isError());
    }

    private static void requireTool(McpToolDefinition tool) {
        if (tool == null) {
            throw new IllegalArgumentException("mcp tool is required");
        }
        if (tool.getName() == null || tool.getName().isEmpty()) {
            throw new IllegalArgumentException("mcp tool name is required");
        }
    }

    private static SchemaConversion customToolInputSchema(Map<String, Object> schema) {
        Map<String, Object> raw = schema == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(schema);
        Object rawType = raw.remove("type");
        if (rawType != null && !(rawType instanceof String)) {
            throw new IllegalArgumentException("mcp tool input schema type must be a string");
        }
        String type = rawType == null ? "object" : (String) rawType;
        if (!"object".equals(type)) {
            throw new IllegalArgumentException("mcp tool input schema top-level type must be object");
        }
        Object properties = raw.remove("properties");
        if (properties != null && !(properties instanceof Map)) {
            throw new IllegalArgumentException("mcp tool input schema properties must be an object");
        }
        Object required = raw.remove("required");
        if (required != null && !(required instanceof List)) {
            throw new IllegalArgumentException("mcp tool input schema required must be an array");
        }

        Map<String, Object> constraints = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!IGNORED_TOP_LEVEL_SCHEMA_KEYWORDS.contains(entry.getKey())) {
                constraints.put(entry.getKey(), entry.getValue());
            }
        }

        ResolveResult resolvedProperties = properties == null
                ? new ResolveResult(null, false)
                : resolveSchemaValue(properties, schema, new HashSet<String>());
        if (resolvedProperties.unresolved) {
            constraints.put("properties", properties);
        }
        removeUnreferencedDefinitions(constraints);

        CustomToolInputSchema converted = new CustomToolInputSchema().type(type);
        if (properties != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propertyMap = (Map<String, Object>) resolvedProperties.value;
            converted.properties(propertyMap);
        }
        if (required != null) {
            List<String> names = new ArrayList<String>();
            for (Object name : (List<?>) required) {
                if (!(name instanceof String)) {
                    throw new IllegalArgumentException(
                            "mcp tool input schema required must be an array of strings");
                }
                names.add((String) name);
            }
            converted.required(names);
        }
        try {
            String constraintsJSON = constraints.isEmpty()
                    ? ""
                    : SCHEMA_OBJECT_MAPPER.writeValueAsString(constraints);
            return new SchemaConversion(converted, constraintsJSON);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "mcp tool input schema constraints are not JSON serializable", exception);
        }
    }

    private static void removeUnreferencedDefinitions(Map<String, Object> constraints) {
        Map<String, String> definitionReferences = new LinkedHashMap<String, String>();
        definitionReferences.put("$defs", "#/$defs");
        definitionReferences.put("definitions", "#/definitions");
        for (Map.Entry<String, String> definition : definitionReferences.entrySet()) {
            if (!constraints.containsKey(definition.getKey())) {
                continue;
            }
            boolean referenced = false;
            for (Map.Entry<String, Object> constraint : constraints.entrySet()) {
                if (!definition.getKey().equals(constraint.getKey())
                        && containsReference(constraint.getValue(), definition.getValue())) {
                    referenced = true;
                    break;
                }
            }
            if (!referenced) {
                constraints.remove(definition.getKey());
            }
        }
    }

    private static boolean containsReference(Object value, String prefix) {
        if (value instanceof Map) {
            Map<?, ?> object = (Map<?, ?>) value;
            Object reference = object.get("$ref");
            if (reference instanceof String
                    && (reference.equals(prefix) || ((String) reference).startsWith(prefix + "/"))) {
                return true;
            }
            for (Object item : object.values()) {
                if (containsReference(item, prefix)) {
                    return true;
                }
            }
        } else if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (containsReference(item, prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ResolveResult resolveSchemaValue(
            Object value,
            Map<String, Object> root,
            Set<String> resolving) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) value;
            Object rawReference = object.get("$ref");
            if (rawReference instanceof String) {
                String reference = (String) rawReference;
                Object target = resolveJSONPointer(root, reference);
                if (target != null && !resolving.contains(reference)) {
                    resolving.add(reference);
                    ResolveResult resolvedTarget = resolveSchemaValue(target, root, resolving);
                    resolving.remove(reference);
                    if (resolvedTarget.value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> targetMap =
                                (Map<String, Object>) resolvedTarget.value;
                        Map<String, Object> merged = new LinkedHashMap<String, Object>(targetMap);
                        for (Map.Entry<String, Object> entry : object.entrySet()) {
                            if (!"$ref".equals(entry.getKey())) {
                                merged.put(entry.getKey(), entry.getValue());
                            }
                        }
                        ResolveResult resolved = resolveSchemaValue(merged, root, resolving);
                        return new ResolveResult(
                                resolved.value,
                                resolvedTarget.unresolved || resolved.unresolved);
                    }
                }
                return mapWithoutReference(object, root, resolving);
            }

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            boolean unresolved = false;
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                ResolveResult resolved = resolveSchemaValue(entry.getValue(), root, resolving);
                result.put(entry.getKey(), resolved.value);
                unresolved = unresolved || resolved.unresolved;
            }
            return new ResolveResult(result, unresolved);
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<Object>();
            boolean unresolved = false;
            for (Object item : (List<?>) value) {
                ResolveResult resolved = resolveSchemaValue(item, root, resolving);
                result.add(resolved.value);
                unresolved = unresolved || resolved.unresolved;
            }
            return new ResolveResult(result, unresolved);
        }
        return new ResolveResult(value, false);
    }

    private static ResolveResult mapWithoutReference(
            Map<String, Object> value,
            Map<String, Object> root,
            Set<String> resolving) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            if ("$ref".equals(entry.getKey())) {
                continue;
            }
            ResolveResult resolved = resolveSchemaValue(entry.getValue(), root, resolving);
            result.put(entry.getKey(), resolved.value);
        }
        return new ResolveResult(result, true);
    }

    private static Object resolveJSONPointer(Map<String, Object> root, String reference) {
        if (!reference.startsWith("#/")) {
            return null;
        }
        Object current = root;
        String[] tokens = reference.substring(2).split("/", -1);
        for (String rawToken : tokens) {
            String token = rawToken.replace("~1", "/").replace("~0", "~");
            if (!(current instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) current;
            if (!object.containsKey(token)) {
                return null;
            }
            current = object.get(token);
        }
        return current;
    }

    private static ContentBlock contentBlock(McpContent content) {
        if (content == null) {
            throw new IllegalArgumentException("unsupported MCP content type null");
        }
        if ("text".equals(content.getType())) {
            return new ContentBlock("text", content.getText());
        }
        if ("image".equals(content.getType())) {
            if (!SUPPORTED_IMAGE_MIME_TYPES.contains(content.getMimeType())) {
                throw new IllegalArgumentException(
                        "unsupported image MIME type: " + content.getMimeType());
            }
            return base64Block("image", content.getMimeType(), content.getData());
        }
        if ("resource".equals(content.getType())) {
            return resourceBlock(content.getResource());
        }
        throw new IllegalArgumentException("unsupported MCP content type: " + content.getType());
    }

    private static ContentBlock resourceBlock(McpResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("embedded MCP resource has no content");
        }
        String mimeType = resource.getMimeType();
        if (SUPPORTED_IMAGE_MIME_TYPES.contains(mimeType)) {
            if (resource.getBlob() == null) {
                throw new IllegalArgumentException("image resource must contain blob data");
            }
            return base64Block("image", mimeType, resource.getBlob());
        }
        if ("application/pdf".equals(mimeType)) {
            if (resource.getBlob() == null) {
                throw new IllegalArgumentException("PDF resource must contain blob data");
            }
            return base64Block("document", mimeType, resource.getBlob());
        }
        if (mimeType == null || mimeType.isEmpty() || mimeType.startsWith("text/")) {
            String text = resource.getText();
            if (text == null && resource.getBlob() != null) {
                byte[] decoded = Base64.getDecoder().decode(resource.getBlob());
                text = new String(decoded, StandardCharsets.UTF_8);
            }
            ContentBlock block = new ContentBlock();
            block.setType("document");
            block.setSource(source("text", "text/plain", text == null ? "" : text));
            return block;
        }
        throw new IllegalArgumentException("unsupported resource MIME type: " + mimeType);
    }

    private static ContentBlock base64Block(String type, String mimeType, String data) {
        ContentBlock block = new ContentBlock();
        block.setType(type);
        block.setSource(source("base64", mimeType, data == null ? "" : data));
        return block;
    }

    private static Map<String, Object> source(String type, String mimeType, String data) {
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("type", type);
        source.put("media_type", mimeType);
        source.put("data", data);
        return source;
    }

    private static final class GenericMcpTool implements Tool {
        private final String name;
        private final McpClient client;

        private GenericMcpTool(String name, McpClient client) {
            this.name = name;
            this.client = client;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ToolResult execute(Object input, ToolContext context) {
            if (context != null && context.isCancelled()) {
                return ToolResult.error("mcp tool execution cancelled");
            }
            Map<String, Object> arguments;
            if (input == null) {
                arguments = Collections.emptyMap();
            } else if (input instanceof Map) {
                arguments = new LinkedHashMap<String, Object>((Map<String, Object>) input);
            } else {
                return ToolResult.error("mcp tool input must be an object");
            }
            try {
                return toolResult(client.callTool(name, arguments, context));
            } catch (Exception exception) {
                String message = exception.getMessage();
                return ToolResult.error("mcp tool " + name + ": "
                        + (message == null || message.isEmpty() ? exception.toString() : message));
            }
        }
    }

    private static final class SchemaConversion {
        private final CustomToolInputSchema schema;
        private final String constraints;

        private SchemaConversion(CustomToolInputSchema schema, String constraints) {
            this.schema = schema;
            this.constraints = constraints;
        }
    }

    private static final class ResolveResult {
        private final Object value;
        private final boolean unresolved;

        private ResolveResult(Object value, boolean unresolved) {
            this.value = value;
            this.unresolved = unresolved;
        }
    }
}
