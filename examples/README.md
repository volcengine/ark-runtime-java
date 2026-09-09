# Examples

Runnable examples for the `ark-runtime-java` SDK. Set `ARK_API_KEY` and, for most examples, `ARK_MODEL` to a model ID available in your account.

```bash
export ARK_API_KEY=...
export ARK_MODEL=...
mvn -q -DskipTests install
mvn -q -f examples/volc/pom.xml compile exec:java \
  -Dexec.mainClass=com.volcengine.ark.runtime.examples.volc.CreateResponseExample
```

Run `mvn -q -DskipTests install` at the repository root first so the example modules can resolve the `ark-runtime` jar from your local Maven cache. Replace `volc` with `byteplus` in the path and main-class package to run the BytePlus version.

All service-calling examples are grouped by cloud:

- `com.volcengine.ark.runtime.examples.volc` uses `ArkService.volc()` and Volcengine China model IDs.
- `com.volcengine.ark.runtime.examples.byteplus` uses `ArkService.byteplus()` and BytePlus model IDs.

`com.volcengine.ark.runtime.examples.volc.SelfHostedWorkerExample` demonstrates the Managed-Agents self-hosted worker poll/handle loop and uses the client's production default `https://ark.cn-beijing.volces.com/api/v3`.

The paired multimodal and sparse embedding examples default to `doubao-embedding-vision-251215` / `skylark-embedding-vision-251215`. The paired image examples default to `doubao-seedream-5-0-pro-260628` / `dola-seedream-5-0-pro-260628`. The paired video-generation examples default to `doubao-seedance-2-0-fast-260128` / `dreamina-seedance-2-0-fast-260128`.

MCP is available in both clouds and its calls explicitly send `ark-beta-mcp: true`. Other built-in tools are CN-only: Knowledge Search sends `ark-beta-knowledge-search: true`, and Doubao App sends `ark-beta-doubao-app: true`.

[`self_hosted_mcp_worker/`](./self_hosted_mcp_worker) demonstrates how to discover tools from a local stdio MCP server, convert them into Agent custom tool declarations, and execute them through the self-hosted worker.
