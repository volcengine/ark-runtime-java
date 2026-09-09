// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.models.agent.ToolItem;
import com.volcengine.ark.runtime.selfhosted.ContentBlock;
import com.volcengine.ark.runtime.selfhosted.Tool;
import com.volcengine.ark.runtime.selfhosted.ToolContext;
import com.volcengine.ark.runtime.selfhosted.ToolResult;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpToolsTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void customToolItemAdaptsSchemaToCurrentAgentContract() throws Exception {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Collections.singletonMap(
                "order_id", Collections.singletonMap("$ref", "#/$defs/order_id")));
        schema.put("required", Collections.singletonList("order_id"));
        schema.put("additionalProperties", false);
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        Map<String, Object> orderID = new LinkedHashMap<String, Object>();
        orderID.put("type", "string");
        orderID.put("minLength", 1);
        schema.put("$defs", Collections.singletonMap(
                "order_id", orderID));

        ToolItem item = McpTools.customToolItem(new McpToolDefinition(
                "lookup_order", "Lookup an order", schema));
        String raw = OBJECT_MAPPER.writeValueAsString(item.getInputSchema());
        Assert.assertFalse(raw, raw.contains("\"additionalProperties\""));
        Assert.assertFalse(raw, raw.contains("\"$defs\""));
        Assert.assertFalse(raw, raw.contains("\"$ref\""));
        Assert.assertTrue(raw, raw.contains("\"minLength\":1"));
        Assert.assertTrue(item.getDescription(), item.getDescription().startsWith("Lookup an order"));
        Assert.assertTrue(item.getDescription(), item.getDescription().contains("\"additionalProperties\":false"));
        Assert.assertFalse(item.getDescription(), item.getDescription().contains("\"$defs\""));
        Assert.assertFalse(item.getDescription(), item.getDescription().contains("\"$schema\""));
    }

    @Test
    public void customToolItemRetainsReferencedDefinitions() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("allOf", Collections.singletonList(
                Collections.singletonMap("$ref", "#/$defs/constraint")));
        schema.put("$defs", Collections.singletonMap(
                "constraint", Collections.singletonMap("additionalProperties", false)));

        ToolItem item = McpTools.customToolItem(new McpToolDefinition("lookup", "", schema));

        Assert.assertTrue(item.getDescription(), item.getDescription().contains("\"$defs\""));
        Assert.assertTrue(
                item.getDescription(),
                item.getDescription().contains("\"$ref\":\"#/$defs/constraint\""));
    }

    @Test
    public void customToolItemDescribesUnresolvedReferences() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Collections.singletonMap(
                "order", Collections.singletonMap("$ref", "https://example.com/order.json")));

        ToolItem item = McpTools.customToolItem(new McpToolDefinition("lookup", "", schema));

        Assert.assertTrue(
                item.getDescription(),
                item.getDescription().contains("\"$ref\":\"https://example.com/order.json\""));
        Assert.assertEquals(
                Collections.emptyMap(),
                item.getInputSchema().getProperties().get("order"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void customToolItemRejectsOversizedCompatibilityDescription() {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        McpTools.customToolItem(new McpToolDefinition(
                "large", String.join("", Collections.nCopies(10_000, "a")), schema));
    }

    @Test
    public void mcpToolCallsGenericClient() {
        final Map<String, Object> observed = new LinkedHashMap<String, Object>();
        McpClient client = new McpClient() {
            @Override
            public McpCallToolResult callTool(
                    String name,
                    Map<String, Object> arguments,
                    ToolContext context) {
                observed.put("name", name);
                observed.put("arguments", arguments);
                return new McpCallToolResult(
                        Collections.singletonList(new McpContent(
                                "text", "echo: hello", null, null, null)),
                        null,
                        false);
            }
        };
        Tool tool = McpTools.mcpTool(
                new McpToolDefinition("echo", "", null),
                client);
        ToolResult result = tool.execute(
                Collections.<String, Object>singletonMap("text", "hello"),
                new ToolContext("."));
        Assert.assertFalse(result.isError());
        Assert.assertEquals("echo: hello", result.getContent().get(0).getText());
        Assert.assertEquals("echo", observed.get("name"));
    }

    @Test
    public void convertsRichContentAndStructuredFallback() {
        List<McpContent> content = Arrays.asList(
                new McpContent("image", null, "image/png", "aW1hZ2U=", null),
                new McpContent("resource", null, null, null, new McpResource(
                        "file:///result.txt", "text/plain", "resource text", null)));
        ToolResult rich = McpTools.toolResult(new McpCallToolResult(content, null, false));
        Assert.assertFalse(rich.isError());
        @SuppressWarnings("unchecked")
        Map<String, Object> image = (Map<String, Object>) rich.getContent().get(0).getSource();
        Assert.assertEquals("aW1hZ2U=", image.get("data"));

        ToolResult structured = McpTools.toolResult(new McpCallToolResult(
                Collections.<McpContent>emptyList(),
                Collections.singletonMap("status", "ok"),
                false));
        ContentBlock block = structured.getContent().get(0);
        Assert.assertEquals("{\"status\":\"ok\"}", block.getText());
    }

    @Test
    public void normalizesTextResourceMimeType() {
        McpContent content = new McpContent("resource", null, null, null, new McpResource(
                "file:///result.html", "text/html", "<p>hello</p>", null));
        ToolResult result = McpTools.toolResult(new McpCallToolResult(
                Collections.singletonList(content), null, false));

        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) result.getContent().get(0).getSource();
        Assert.assertEquals("text/plain", source.get("media_type"));
    }

    @Test
    public void convertsEmptyErrorResultWithMessage() {
        ToolResult result = McpTools.toolResult(new McpCallToolResult(
                Collections.<McpContent>emptyList(), null, true));

        Assert.assertTrue(result.isError());
        Assert.assertEquals(1, result.getContent().size());
        Assert.assertEquals("tool returned an error", result.getContent().get(0).getText());
    }

    @Test
    public void conversionErrorDoesNotExposeResourceURI() {
        String secretURI = "https://example.com/file?signature=secret";
        McpResource resource = new McpResource(secretURI, "image/png", null, null);

        ToolResult result = McpTools.toolResult(new McpCallToolResult(
                Collections.singletonList(new McpContent(
                        "resource", null, null, null, resource)),
                null,
                false));

        Assert.assertTrue(result.isError());
        Assert.assertFalse(result.getContent().get(0).getText().contains(secretURI));
        Assert.assertFalse(result.getContent().get(0).getText().contains("secret"));
    }

    @Test
    public void resolvesJSONPointerWithTrailingEmptyToken() throws Exception {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Collections.singletonMap(
                "value", Collections.singletonMap("$ref", "#/$defs/")));
        schema.put("$defs", Collections.singletonMap(
                "", Collections.singletonMap("type", "string")));

        ToolItem item = McpTools.customToolItem(new McpToolDefinition("lookup", "", schema));
        String raw = OBJECT_MAPPER.writeValueAsString(item.getInputSchema());

        Assert.assertTrue(raw, raw.contains("\"type\":\"string\""));
        Assert.assertFalse(raw, raw.contains("\"$ref\""));
    }
}
