# Migrate from the legacy Java SDK

This guide covers:

- `com.volcengine:volcengine-java-sdk-ark-runtime`
- `com.byteplus:byteplus-java-sdk-v2-ark-runtime`

Both migrate to `com.volcengine:ark-runtime`. The new common Java package is
also `com.volcengine.ark.runtime` for BytePlus applications.

## 1. Migration order

Migrate one API flow at a time:

1. Choose the target cloud: Volcengine (CN) or BytePlus.
2. Replace the Ark Runtime dependency and select the regional service builder.
3. Update imports to the new common package and API-specific generated models.
4. Rebuild request unions with the new factories and generated enums.
5. Update response types, Chat deltas, and Responses event subclasses.
6. Add the required beta header to every built-in-tool request and remove any
   CN-only tool from a BytePlus target.
7. Compile and smoke-test that flow before migrating the next one.

Do not bulk-replace the old model package or event class names. The correct new
class depends on the request variant and the events the application consumes.

## 2. Dependency and client mapping

```xml
<dependency>
  <groupId>com.volcengine</groupId>
  <artifactId>ark-runtime</artifactId>
  <version>0.1.0</version>
</dependency>
```

| Legacy | New |
|---|---|
| `ArkService.builder()` | CN: `ArkService.volc()`; BP: `ArkService.byteplus()` |
| `new ArkService(apiKey)` | use regional builder, `.apiKey(apiKey).build()` |
| `com.byteplus.ark.runtime...` | `com.volcengine.ark.runtime...` |
| old `.model...` packages | new `.models...` API-specific classes |

Preserve dispatcher, connection pool, timeout, and API key calls after changing
the builder entry point.

## 3. Request-model mapping

The generated model layer changed substantially; do not bulk-replace `model`
with `models` and assume the result is correct.

Chat mappings:

| Legacy | New |
|---|---|
| `model.completion.chat.ChatMessage` | typed `models.chat.ChatCompletionRequest*Message` subtype |
| `ChatMessageRole.USER` | `ChatCompletionRequestMessageType.USER` |
| string message content | `ChatCompletionMessageContent.ofString(value)` |
| `ChatCompletionRequest` | `models.chat.ChatCompletionRequest` |

Responses mappings:

| Legacy | New |
|---|---|
| `CreateResponsesRequest` | `ResponsesRequest` |
| `ResponseObject` | `Response` |
| `ResponsesInput.builder().stringValue(v).build()` | `ResponsesInput.ofString(v)` |
| list input builder | `ResponsesInput.ofList(List<InputItem>)` |
| `ResponsesThinking` | `Thinking` |
| `ResponsesConstants` values | generated enums such as `ThinkingMode` and `MessageRole` |

For a list input, construct a concrete `InputItem` implementation, place it in
a `List<InputItem>`, then call `ResponsesInput.ofList`. For function results,
use `ItemFunctionToolCallOutput` and keep both the tool call ID and previous
response ID.

If the old application persists serialized request JSON, add a fixture test
that deserializes or rebuilds it with new models and compares the outgoing JSON
field by field.

## 4. Streaming mapping

The RxJava flow remains, but payload access changed.

Chat:

| Legacy | New |
|---|---|
| `choice.getChoices().get(0).getMessage().getContent()` | `chunk.getChoices().get(0).getDelta().getContent()` |

Responses:

| Legacy | New |
|---|---|
| `model.responses.event.StreamEvent` | `models.responses.ResponseStreamEvent` |
| old event subpackages | generated event classes directly in `models.responses` |
| `OutputTextDeltaEvent` | `ResponseTextDeltaEvent` |
| `ResponseCompletedEvent` | new `ResponseCompletedEvent` class and `Response` payload |

Use `instanceof` before casting. Capture function/MCP IDs from their typed
output-item event and response IDs from the completed event. Preserve
`doOnError` or an equivalent error path; `blockingForEach` returning normally is
the end of stream, not proof that every expected event was present.

## 5. Extra headers and regional behavior

Both ordinary and stream methods have header-map overloads:

```java
Map<String, String> headers = Collections.singletonMap("ark-beta-mcp", "true");
service.createResponse(request, headers);
service.streamResponse(request, headers);
```

MCP (`ark-beta-mcp`) is supported in both clouds. Web search
(`ark-beta-web-search`), knowledge search (`ark-beta-knowledge-search`), Doubao
App (`ark-beta-doubao-app`), and image process (`ark-beta-image-process`) are
CN-only. A BytePlus migration must stop for review if it finds one of those
tools.

## 6. Regional model IDs

Model names and endpoint IDs are cloud-specific. Prefer application
configuration, and update any legacy hard-coded default when changing clouds:

| API | Volcengine (CN) example | BytePlus example |
|---|---|---|
| Responses / Chat | `doubao-seed-2-1-pro-260628` | `seed-2-0-lite-260428` |
| Multimodal / sparse embeddings | `doubao-embedding-vision-251215` | `skylark-embedding-vision-251215` |
| Image generation | `doubao-seedream-5-0-pro-260628` | `dola-seedream-5-0-pro-260628` |
| Video generation | `doubao-seedance-2-0-fast-260128` | `dreamina-seedance-2-0-fast-260128` |

Use a model or endpoint ID provisioned for the target account if it differs
from these example defaults.

## 7. Validate the migration

1. Search for the legacy dependency, package names, model imports, and generic
   service builder; none should remain in migrated Ark Runtime code.
2. Run `mvn test` and `mvn checkstyle:check`.
3. Verify one non-streaming and one streaming Chat or Responses request.
4. Confirm stream text comes from deltas and typed Responses events.
5. Smoke-test every built-in tool with its required beta header.
6. Test CN and BytePlus independently if both are supported. Never reuse a
   regional key, model ID, endpoint ID, or service instance across clouds.
7. Confirm `shutdownExecutor()` runs when the owning application shuts down.

A migration is not complete merely because the project compiles. The live
stream and tool checks catch missing deltas, incorrect event casts, unsupported
regional tools, and omitted headers.
