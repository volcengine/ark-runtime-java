# Usage guide

Use this guide when creating or changing a Java application with Ark Runtime.
Complete request shapes live in the regional example projects.

## 1. Add the dependency

```xml
<dependency>
  <groupId>com.volcengine</groupId>
  <artifactId>ark-runtime</artifactId>
  <version>0.1.0</version>
</dependency>
```

Set `ARK_API_KEY` outside source control and let the application accept its
model or endpoint ID through configuration.

## 2. Select the cloud

Only the builder entry point changes:

```java
// Volcengine (CN)
ArkService service = ArkService.volc()
        .apiKey(System.getenv("ARK_API_KEY"))
        .build();

// BytePlus
ArkService service = ArkService.byteplus()
        .apiKey(System.getenv("ARK_API_KEY"))
        .build();
```

The remainder of connection-pool, dispatcher, timeout, request, and response
setup is identical.

Use a model available in the selected cloud. Current examples use:

| API | Volcengine (CN) | BytePlus |
|---|---|---|
| Responses / Chat | `doubao-seed-2-1-pro-260628` | `seed-2-0-lite-260428` |
| Multimodal / sparse embeddings | `doubao-embedding-vision-251215` | `skylark-embedding-vision-251215` |
| Image generation | `doubao-seedream-5-0-pro-260628` | `dola-seedream-5-0-pro-260628` |
| Video generation | `doubao-seedance-2-0-fast-260128` | `dreamina-seedance-2-0-fast-260128` |

User configuration overrides example defaults.

BytePlus currently has no model for the text-only `/embeddings` endpoint, so
its examples use `/embeddings/multimodal` instead.

## 3. Build requests with generated models

All public model classes are under `com.volcengine.ark.runtime.models`. Use
builders for objects and the generated factory for a union value.

Simple Responses input:

```java
ResponsesRequest request = ResponsesRequest.builder()
        .model(model)
        .input(ResponsesInput.ofString("Explain LLMs in one sentence."))
        .build();
Response response = service.createResponse(request);
```

List-valued Responses input:

```java
ResponsesInput input = ResponsesInput.ofList(
        Collections.<InputItem>singletonList(
                ItemEasyMessage.builder()
                        .role(MessageRole.USER)
                        .content(MessageContent.ofString("Hello"))
                        .build()));
```

Chat uses explicit message types:

```java
List<ChatCompletionRequestMessage> messages = new ArrayList<>();
messages.add(ChatCompletionRequestUserMessage.builder()
        .role(ChatCompletionRequestMessageType.USER)
        .content(ChatCompletionMessageContent.ofString("Hello"))
        .build());

ChatCompletionRequest request = ChatCompletionRequest.builder()
        .model(model)
        .messages(messages)
        .build();
```

Do not use maps for typed request fields merely to avoid generated union
classes. Maps are appropriate only where the public model intentionally accepts
arbitrary JSON, such as a function parameter schema.

## 4. Handle streams

Chat streaming yields completion chunks. Text is on the delta, not the full
message:

```java
service.streamChatCompletion(request)
        .doOnError(Throwable::printStackTrace)
        .blockingForEach(chunk -> {
            if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
                return;
            }
            String text = chunk.getChoices().get(0).getDelta().getContent();
            if (text != null) {
                System.out.print(text);
            }
        });
```

Responses streaming yields `ResponseStreamEvent` subclasses:

```java
service.streamResponse(request).blockingForEach(event -> {
    if (event instanceof ResponseTextDeltaEvent) {
        System.out.print(((ResponseTextDeltaEvent) event).getDelta());
    } else if (event instanceof ResponseCompletedEvent) {
        String responseId = ((ResponseCompletedEvent) event).getResponse().getId();
        // Store only if a later turn needs it.
    }
});
```

Text delta, reasoning, output-item, function-call, MCP, completed, and error
events have different payloads. Never cast before checking the subtype. Add no
catch-all failure for an unknown subtype; new event types should be safely
ignored unless the application needs them.

## 5. Built-in tools and headers

Use the overload that accepts headers:

```java
Map<String, String> headers = new HashMap<>();
headers.put("ark-beta-mcp", "true");

Response response = service.createResponse(request, headers);
service.streamResponse(request, headers).blockingForEach(/* handler */);
```

| Tool | Cloud | Required header |
|---|---|---|
| MCP | CN and BytePlus | `ark-beta-mcp: true` |
| Web search | CN only | `ark-beta-web-search: true` |
| Knowledge search | CN only | `ark-beta-knowledge-search: true` |
| Doubao App | CN only | `ark-beta-doubao-app: true` |
| Image process | CN only | `ark-beta-image-process: true` |

Do not move a CN-only built-in tool into BytePlus code. Application-defined
function calling is separate from hosted built-in tools.

## 6. Navigate the examples

Start in [`examples/volc`](../examples/volc) or
[`examples/byteplus`](../examples/byteplus). Both regional projects include
Chat and Responses streaming and non-streaming flows, plus the APIs available
in that cloud. The CN tree additionally contains CN-only built-in-tool examples.

Choose the class by intent:

| Intent | Example class |
|---|---|
| Chat stream/non-stream | `ChatCompletionsExample` |
| Chat reasoning, vision, structured output, function calling | matching `ChatCompletions*Example` |
| Responses and typed stream events | `CreateResponseExample` |
| Response retrieval/input operations | `ResponseOperationsExample` |
| Text embeddings (Volcengine only) | `EmbeddingsExample` |
| Sparse or multimodal embeddings | `SparseEmbeddingsExample`, `MultiModalEmbeddingsExample` |
| Image generation | `ImageGenerationExample` |
| Video generation | `ContentGenerationTaskExample` |
| Files | `FileUploadExample` |
| Agents, sessions, memory stores, environments | matching lifecycle example |
| Token counting | `TokenizationExample` |

Use built-in-tool examples only in the clouds where they appear.

## 7. Completion checklist

- The only Ark Runtime dependency is `com.volcengine:ark-runtime`.
- The service uses one explicit regional builder.
- Imports use `com.volcengine.ark.runtime.models`, including BytePlus code.
- Request union factories and typed messages are preserved.
- Stream handlers check event/chunk types before accessing payloads.
- Built-in tool requests carry the right header and respect cloud support.
- Service shutdown is owned and called once.
- Tests and checkstyle pass.
