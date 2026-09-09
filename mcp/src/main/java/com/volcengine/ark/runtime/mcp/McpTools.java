// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.mcp;

import com.volcengine.ark.runtime.models.agent.ToolItem;
import com.volcengine.ark.runtime.selfhosted.Tool;
import com.volcengine.ark.runtime.selfhosted.ToolContext;
import com.volcengine.ark.runtime.selfhosted.ToolResult;
import com.volcengine.ark.runtime.selfhosted.mcp.McpCallToolResult;
import com.volcengine.ark.runtime.selfhosted.mcp.McpClient;
import com.volcengine.ark.runtime.selfhosted.mcp.McpContent;
import com.volcengine.ark.runtime.selfhosted.mcp.McpResource;
import com.volcengine.ark.runtime.selfhosted.mcp.McpToolDefinition;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adapts the official MCP Java SDK to the Ark self-hosted MCP contract. */
public final class McpTools {
    private McpTools() {
    }

    /** Converts an official MCP tool definition into an Ark Agent declaration. */
    public static ToolItem customToolItem(McpSchema.Tool tool) {
        return com.volcengine.ark.runtime.selfhosted.mcp.McpTools.customToolItem(
                toolDefinition(tool));
    }

    /** Converts official MCP tool definitions into Ark Agent declarations. */
    public static List<ToolItem> customToolItems(List<McpSchema.Tool> tools) {
        return com.volcengine.ark.runtime.selfhosted.mcp.McpTools.customToolItems(
                toolDefinitions(tools));
    }

    /** Wraps an official MCP tool as a self-hosted worker custom tool. */
    public static Tool mcpTool(McpSchema.Tool tool, McpSyncClient client) {
        return com.volcengine.ark.runtime.selfhosted.mcp.McpTools.mcpTool(
                toolDefinition(tool),
                new OfficialMcpClient(client));
    }

    /** Wraps official MCP tools for EnvironmentWorkerOptions.customTools. */
    public static Map<String, Tool> mcpTools(List<McpSchema.Tool> tools, McpSyncClient client) {
        return com.volcengine.ark.runtime.selfhosted.mcp.McpTools.mcpTools(
                toolDefinitions(tools),
                new OfficialMcpClient(client));
    }

    /** Converts an official MCP result into the result posted by the worker. */
    public static ToolResult toolResult(McpSchema.CallToolResult result) {
        return com.volcengine.ark.runtime.selfhosted.mcp.McpTools.toolResult(
                callToolResult(result));
    }

    private static List<McpToolDefinition> toolDefinitions(List<McpSchema.Tool> tools) {
        if (tools == null) {
            return Collections.emptyList();
        }
        List<McpToolDefinition> definitions = new ArrayList<McpToolDefinition>(tools.size());
        for (McpSchema.Tool tool : tools) {
            definitions.add(toolDefinition(tool));
        }
        return definitions;
    }

    private static McpToolDefinition toolDefinition(McpSchema.Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("mcp tool is required");
        }
        return new McpToolDefinition(
                tool.name(),
                tool.description(),
                inputSchema(tool.inputSchema()));
    }

    private static Map<String, Object> inputSchema(Map<String, Object> schema) {
        if (schema == null) {
            return Collections.<String, Object>singletonMap("type", "object");
        }
        return new LinkedHashMap<String, Object>(schema);
    }

    private static McpCallToolResult callToolResult(McpSchema.CallToolResult result) {
        if (result == null) {
            return null;
        }
        List<McpContent> content = new ArrayList<McpContent>();
        if (result.content() != null) {
            for (McpSchema.Content item : result.content()) {
                content.add(content(item));
            }
        }
        return new McpCallToolResult(
                content,
                result.structuredContent(),
                Boolean.TRUE.equals(result.isError()));
    }

    private static McpContent content(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent) {
            return new McpContent(
                    "text", ((McpSchema.TextContent) content).text(), null, null, null);
        }
        if (content instanceof McpSchema.ImageContent) {
            McpSchema.ImageContent image = (McpSchema.ImageContent) content;
            return new McpContent("image", null, image.mimeType(), image.data(), null);
        }
        if (content instanceof McpSchema.EmbeddedResource) {
            return new McpContent(
                    "resource",
                    null,
                    null,
                    null,
                    resource(((McpSchema.EmbeddedResource) content).resource()));
        }
        if (content instanceof McpSchema.AudioContent) {
            return new McpContent("audio", null, null, null, null);
        }
        if (content instanceof McpSchema.ResourceLink) {
            return new McpContent("resource_link", null, null, null, null);
        }
        return new McpContent(content == null ? "null" : content.type(), null, null, null, null);
    }

    private static McpResource resource(McpSchema.ResourceContents resource) {
        if (resource == null) {
            return null;
        }
        String text = null;
        String blob = null;
        if (resource instanceof McpSchema.TextResourceContents) {
            text = ((McpSchema.TextResourceContents) resource).text();
        } else if (resource instanceof McpSchema.BlobResourceContents) {
            blob = ((McpSchema.BlobResourceContents) resource).blob();
        }
        return new McpResource(resource.uri(), resource.mimeType(), text, blob);
    }

    private static final class OfficialMcpClient implements McpClient {
        private final McpSyncClient client;

        private OfficialMcpClient(McpSyncClient client) {
            if (client == null) {
                throw new IllegalArgumentException("mcp client is required");
            }
            this.client = client;
        }

        @Override
        public McpCallToolResult callTool(
                String name,
                Map<String, Object> arguments,
                ToolContext context) {
            if (context != null && context.isCancelled()) {
                throw new IllegalStateException("execution cancelled");
            }
            return McpTools.callToolResult(
                    client.callTool(McpSchema.CallToolRequest.builder(name)
                            .arguments(arguments)
                            .build()));
        }
    }
}
