// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.examples.selfhostedmcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Minimal stdio MCP server used by {@link SelfHostedMcpWorkerExample}. */
public final class McpEchoServer {
    private McpEchoServer() {
    }

    /** Starts a single echo tool over stdio. */
    public static void main(String[] args) throws InterruptedException {
        Map<String, Object> textProperty = new LinkedHashMap<>();
        textProperty.put("type", "string");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("text", textProperty);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", Collections.singletonList("text"));

        StdioServerTransportProvider transport =
                new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("ark-self-hosted-mcp-example", "1.0.0")
                .toolCall(
                        McpSchema.Tool.builder("mcp_echo", schema)
                                .description("Echo text through the local MCP server.")
                                .build(),
                        (exchange, request) -> McpSchema.CallToolResult.builder()
                                .addTextContent("MCP echo: " + request.arguments().get("text"))
                                .build())
                .build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "mcp-echo-server-shutdown"));
        new CountDownLatch(1).await();
    }
}
