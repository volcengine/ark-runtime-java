// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.volcengine.ark.runtime.models.agent.ToolItem;
import com.volcengine.ark.runtime.selfhosted.ContentBlock;
import com.volcengine.ark.runtime.selfhosted.Tool;
import com.volcengine.ark.runtime.selfhosted.ToolContext;
import com.volcengine.ark.runtime.selfhosted.ToolResult;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpToolsTest {
    @Test
    public void officialMapperSerializesProtocolValuesWithResolvedAnnotations() throws Exception {
        String value = McpJsonDefaults.getMapper().writeValueAsString(
                McpSchema.CallToolRequest.builder("echo")
                        .arguments(Collections.singletonMap("text", "hello"))
                        .build());

        assertTrue(value.contains("\"name\":\"echo\""));
        assertTrue(value.contains("\"text\":\"hello\""));
    }

    @Test
    public void customToolItemAdaptsSchemaToCurrentAgentContract() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("city", Collections.singletonMap("type", "string"));
        Map<String, Object> definitions = Collections.<String, Object>singletonMap(
                "city", Collections.singletonMap("minLength", 1));
        Map<String, Object> conditionalSchema = Collections.<String, Object>singletonMap(
                "if", Collections.singletonMap("required", Collections.singletonList("country")));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Collections.singletonList("city"));
        schema.put("additionalProperties", false);
        schema.put("$defs", definitions);
        schema.put("allOf", Collections.singletonList(conditionalSchema));
        schema.put("oneOf", Collections.singletonList(
                Collections.singletonMap("required", Collections.singletonList("city"))));
        schema.put("anyOf", Collections.singletonList(
                Collections.singletonMap("required", Collections.singletonList("country"))));
        schema.put("patternProperties", Collections.singletonMap("^x-", Collections.singletonMap("type", "string")));
        schema.put("dependentSchemas", Collections.singletonMap("country", conditionalSchema));
        schema.put("unevaluatedProperties", false);
        McpSchema.Tool tool = McpSchema.Tool.builder("weather", schema)
                .description("Get weather")
                .build();

        ToolItem item = McpTools.customToolItem(tool);

        assertEquals("custom", item.getType());
        assertEquals("weather", item.getName());
        assertTrue(item.getDescription().startsWith("Get weather"));
        assertEquals("object", item.getInputSchema().getType());
        assertEquals(properties, item.getInputSchema().getProperties());
        assertEquals(Collections.singletonList("city"), item.getInputSchema().getRequired());
        assertTrue(item.getDescription().contains("\"additionalProperties\":false"));
        assertFalse(item.getDescription().contains("\"$defs\""));
        assertTrue(item.getDescription().contains("\"allOf\""));
        assertTrue(item.getDescription().contains("\"oneOf\""));
        assertTrue(item.getDescription().contains("\"anyOf\""));
        assertTrue(item.getDescription().contains("\"patternProperties\""));
        assertTrue(item.getDescription().contains("\"dependentSchemas\""));
        assertTrue(item.getDescription().contains("\"unevaluatedProperties\":false"));
    }

    @Test
    public void mcpToolExecutesClientAndConvertsTextResult() {
        McpSchema.Tool definition = tool("weather");
        McpSyncClient client = mock(McpSyncClient.class);
        when(client.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(
                new McpSchema.CallToolResult(
                        Collections.singletonList(McpSchema.TextContent.builder("sunny").build()),
                        false,
                        null,
                        null));
        Tool tool = McpTools.mcpTool(definition, client);

        ToolResult result = tool.execute(
                Collections.singletonMap("city", "Beijing"), new ToolContext("."));

        assertFalse(result.isError());
        assertEquals("sunny", result.getContent().get(0).getText());
    }

    @Test
    public void toolResultPreservesImageAndDocumentSources() {
        String imageData = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
        String textData = Base64.getEncoder().encodeToString(
                "hello".getBytes(StandardCharsets.UTF_8));
        List<McpSchema.Content> content = List.of(
                McpSchema.ImageContent.builder(imageData, "image/png").build(),
                McpSchema.EmbeddedResource.builder(
                                McpSchema.BlobResourceContents.builder("file:///note.txt", textData)
                                        .mimeType("text/plain")
                                        .build())
                        .build());

        ToolResult result = McpTools.toolResult(
                new McpSchema.CallToolResult(content, false, null, null));

        assertFalse(result.isError());
        assertEquals("image", result.getContent().get(0).getType());
        assertEquals(imageData, source(result.getContent().get(0)).get("data"));
        assertEquals("document", result.getContent().get(1).getType());
        assertEquals("hello", source(result.getContent().get(1)).get("data"));
    }

    @Test
    public void unsupportedContentBecomesErrorResult() {
        McpSchema.Content audio = mock(McpSchema.AudioContent.class);

        ToolResult result = McpTools.toolResult(new McpSchema.CallToolResult(
                Collections.singletonList(audio), false, null, null));

        assertTrue(result.isError());
        assertTrue(result.getContent().get(0).getText().contains("audio"));
    }

    @Test
    public void structuredContentBecomesJsonText() {
        ToolResult result = McpTools.toolResult(new McpSchema.CallToolResult(
                Collections.emptyList(),
                false,
                Collections.singletonMap("ok", true),
                null));

        assertFalse(result.isError());
        assertEquals("{\"ok\":true}", result.getContent().get(0).getText());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> source(ContentBlock block) {
        return (Map<String, Object>) block.getSource();
    }

    private static McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder(
                        name,
                        Collections.<String, Object>singletonMap("type", "object"))
                .build();
    }
}
