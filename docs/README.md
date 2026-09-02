# Ark Runtime Java SDK documentation

This directory contains detailed usage and migration guidance for the Ark
Runtime Java SDK.

## Choose the right document

- [Usage guide](usage.md): dependency setup, regional
  clients, generated request models, streaming, and built-in tools.
- [Migration guide](migration.md): migrate from either legacy Volcengine or
  BytePlus Java SDK.
- [`../examples/volc`](../examples/volc): runnable Volcengine examples.
- [`../examples/byteplus`](../examples/byteplus): runnable BytePlus examples.

## Important usage rules

1. Use `ArkService.volc()` for CN or `ArkService.byteplus()` for BytePlus. Only
   client creation and regional model identifiers differ; request setup stays
   the same.
2. Read `ARK_API_KEY` from the environment. Never insert credentials into
   source, build files, fixtures, logs, or generated patches.
3. Build requests with classes under `com.volcengine.ark.runtime.models`.
   Respect generated union factories such as `ResponsesInput.ofString`.
4. Responses streams contain typed event subclasses. Handle the subclasses the
   application needs and tolerate additional event types.
5. MCP is supported in CN and BytePlus. Other hosted built-in tools shown in
   this repository are CN-only. Pass the matching `ark-beta-*` header to both
   streaming and non-streaming requests.
6. Call `shutdownExecutor()` when an application owns the service lifecycle.

## Minimal verification

```bash
mvn test
mvn checkstyle:check
```

Also run one streaming and one non-streaming request in the selected cloud.
Smoke-test each built-in tool separately to validate its entitlement and beta
header.
