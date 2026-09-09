# Ark Runtime MCP helpers

See the runnable
[`self_hosted_mcp_worker`](../examples/self_hosted_mcp_worker) example for a
minimal stdio MCP server connected to an Ark self-hosted worker.

This optional Java 17 module uses the official MCP Java SDK 2.0 and converts its tools into:

- Ark Agent `custom` tool declarations; and
- runnable self-hosted worker tools.

The MCP client remains inside the customer worker. Managed Agent receives only the tool
definition, each tool call, and the returned result; it does not connect to the MCP server.

The adapter wraps an already connected `McpSyncClient`, so applications may use stdio or
another transport supported by their MCP client. Keep one client open for the worker lifetime.
That client is reused across all Managed Agents Sessions handled by the worker; calls do not
automatically include a Managed Agents `session_id` or `work_id`, and Session idle/deletion is
not an MCP lifecycle notification. Use stateless tools or implement explicit tenant/session
isolation in the MCP server.

```java
List<McpSchema.Tool> definitions = new ArrayList<>();
String cursor = null;
do {
    McpSchema.ListToolsResult page = cursor == null
            ? mcpClient.listTools()
            : mcpClient.listTools(cursor);
    if (page.tools() != null) {
        definitions.addAll(page.tools());
    }
    cursor = page.nextCursor();
} while (cursor != null && !cursor.isEmpty());

List<ToolItem> agentTools = McpTools.customToolItems(definitions);
Map<String, Tool> workerTools = McpTools.mcpTools(definitions, mcpClient);
```

Use `agentTools` when creating or updating the Agent, and pass `workerTools` through
`new EnvironmentWorker.Options().customTools(workerTools)`. Keep the initialized
`McpSyncClient` open for the entire worker lifetime.

The core `ark-runtime` artifact remains compatible with Java 8. This optional module requires
Java 17 because the official MCP Java SDK requires Java 17.

Use the same release version for the core SDK and this adapter:

```xml
<dependency>
    <groupId>com.volcengine</groupId>
    <artifactId>ark-runtime-mcp</artifactId>
    <version>0.7.0</version>
</dependency>
```

The core Java 8 artifact also exposes the protocol-independent
`com.volcengine.ark.runtime.selfhosted.mcp.McpClient` interface. Applications may implement
that interface directly when they use another MCP transport.

The adapter requires the Jackson 2.20 release line or newer. Applications that manage Jackson
versions should pin `jackson-annotations`, `jackson-core`, and `jackson-databind` to a
compatible release line in their `dependencyManagement`; forcing older versions can cause
runtime linkage errors.

When preparing a release, update both the adapter project version and the
`ark-runtime.version` property in `mcp/pom.xml` to match the core release tag.

Managed Agents currently accepts the top-level JSON Schema fields `type`, `properties`, and
`required`. The helper keeps those fields structured, inlines local `$defs` and `definitions`
references used by properties, and appends other top-level constraints as compact JSON to the
tool description. The MCP server remains the authoritative validator when the worker executes
the call. Agent tool descriptions, including appended constraints, must fit within 10,000
characters.

## Tool result support

The worker preserves MCP `isError` and supports text, `image/jpeg`, `image/png`, `image/gif`,
and `image/webp` image blocks. Embedded resources may contain the same image MIME types,
`application/pdf`, or text whose MIME type is absent, empty, or starts with `text/`. When a
result has no content blocks but has `structuredContent`, the helper serializes it as compact
JSON text.

Audio, resource links, unknown content types, and other resource MIME types become an error
result. If a result mixes supported and unsupported blocks, the whole converted result is an
error; the supported blocks are not returned separately.

## Operational and security notes

- Fetch every `tools/list` page. Use the exact same selected tool definitions for the Agent
  declaration and worker registry. Managed Agents currently accepts at most eight custom tools
  per Agent, so explicitly select a stable subset when the MCP server exposes more.
- Tool discovery happens at worker startup. When the MCP server changes its tools, update the
  Agent while it is idle and restart the worker.
- Tool names must match `[a-zA-Z0-9_-]{1,128}`. Avoid names that collide with built-in Agent
  tools, and add your own prefixes when multiple MCP servers expose the same name.
- Managed Agents permission policies do not apply to custom tools. The worker executes each
  matching call, so implement approval, authorization, and operation allowlists in the MCP
  server or a wrapper tool.
- Client-side MCP servers run with the worker's OS, filesystem, and network permissions;
  Managed Agents does not put them in a separate sandbox. Run them with least privilege and a
  minimal environment. Do not pass `ARK_API_KEY` to an MCP subprocess; use separate
  MCP-specific credentials.
- Only wrap MCP servers you trust. Tool names, descriptions, inputs, and results enter the model
  context and must be treated as untrusted content.
- Configure MCP transport or client timeouts. The worker tool timeout remains the final upper
  bound, but a shorter MCP timeout gives clearer failures.
