// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.examples.selfhostedmcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.mcp.McpTools;
import com.volcengine.ark.runtime.models.agent.ToolItem;
import com.volcengine.ark.runtime.selfhosted.EnvironmentWorker;
import com.volcengine.ark.runtime.selfhosted.SelfHostedClient;
import com.volcengine.ark.runtime.service.ArkService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs a self-hosted worker whose custom tools execute through a local MCP server. */
public final class SelfHostedMcpWorkerExample {
    private SelfHostedMcpWorkerExample() {
    }

    /** Connects to MCP, prints Agent declarations, and starts the worker. */
    public static void main(String[] args) throws Exception {
        String apiKey = requiredEnv("ARK_API_KEY");
        String environmentID = requiredEnv("MA_ENVIRONMENT_ID");
        List<String> command = mcpCommand(args);
        if (command.isEmpty()) {
            throw new IllegalArgumentException(
                    "MCP server command is required; see examples/self_hosted_mcp_worker/README.md");
        }

        Map<String, String> serverEnvironment = new LinkedHashMap<>(System.getenv());
        serverEnvironment.remove("ARK_API_KEY");
        ServerParameters parameters = ServerParameters.builder(command.get(0))
                .args(command.subList(1, command.size()))
                .env(serverEnvironment)
                .build();
        StdioClientTransport transport = new StdioClientTransport(
                parameters, McpJsonDefaults.getMapper());

        try (McpSyncClient mcpClient = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder(
                                "ark-self-hosted-worker-example", "1.0.0")
                        .build())
                .build()) {
            mcpClient.initialize();
            List<McpSchema.Tool> tools = listAllTools(mcpClient);
            ObjectMapper mapper = ArkService.defaultObjectMapper();
            for (ToolItem declaration : McpTools.customToolItems(tools)) {
                System.out.println("Agent custom tool: " + mapper.writeValueAsString(declaration));
            }
            System.out.flush();

            SelfHostedClient.Builder clientBuilder = new SelfHostedClient.Builder().apiKey(apiKey);
            String baseURL = System.getenv("ARK_BASE_URL");
            if (baseURL != null && !baseURL.isEmpty()) {
                clientBuilder.baseUrl(baseURL);
            }
            EnvironmentWorker worker = new EnvironmentWorker(
                    clientBuilder.build(),
                    new EnvironmentWorker.Options()
                            .environmentId(environmentID)
                            .workdir(".")
                            .customTools(McpTools.mcpTools(tools, mcpClient)));
            Runtime.getRuntime().addShutdownHook(
                    new Thread(worker::close, "ark-self-hosted-mcp-worker-shutdown"));
            try {
                worker.run();
            } finally {
                worker.close();
            }
        }
    }

    private static List<McpSchema.Tool> listAllTools(McpSyncClient client) {
        List<McpSchema.Tool> tools = new ArrayList<>();
        String cursor = null;
        do {
            McpSchema.ListToolsResult page = cursor == null
                    ? client.listTools()
                    : client.listTools(cursor);
            if (page.tools() != null) {
                tools.addAll(page.tools());
            }
            cursor = page.nextCursor();
        } while (cursor != null && !cursor.isEmpty());
        return tools;
    }

    private static List<String> mcpCommand(String[] args) {
        List<String> values = new ArrayList<>(Arrays.asList(args));
        if (!values.isEmpty() && "--".equals(values.get(0))) {
            values.remove(0);
        }
        return values;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
