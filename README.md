# Ark Runtime Java SDK

The official Java library for accessing ModelArk on Volcengine and BytePlus. It provides typed request and response models, synchronous and streaming helpers, authentication, and automatic retries for Java 8+ applications.

## Installation

### Maven

```xml
<dependency>
    <groupId>com.volcengine</groupId>
    <artifactId>ark-runtime</artifactId>
    <version>0.4.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.volcengine:ark-runtime:0.4.0'
```

## Choose Volcengine or BytePlus

Set `ARK_API_KEY`, then choose the builder for the service you use. The builder configures the correct base URL and region; request construction and all subsequent SDK calls are the same.

### Volcengine (China)

```java
ArkService service = ArkService.volc()
        .apiKey(System.getenv("ARK_API_KEY"))
        .build();
```

### BytePlus (BP)

```java
ArkService service = ArkService.byteplus()
        .apiKey(System.getenv("ARK_API_KEY"))
        .build();
```

Use a model ID available in the corresponding Volcengine or BytePlus account. Model IDs can differ between the two services; the examples use `doubao-seed-2-1-pro-260628` for Volcengine and `seed-2-0-lite-260428` for BytePlus. Override either default with `ARK_MODEL`.

## Quick start

### Responses API

The Responses API is the primary interface for generating text with Ark
models.

```java
import com.volcengine.ark.runtime.service.ArkService;
import com.volcengine.ark.runtime.models.responses.*;

ArkService service = ArkService.volc()
        .apiKey(System.getenv("ARK_API_KEY"))
        .build();

ResponsesRequest request = ResponsesRequest.builder()
        .model(System.getenv("ARK_MODEL"))
        .input(ResponsesInput.ofString("Explain Java generics in two sentences."))
        .build();

Response response = service.createResponse(request);
System.out.println(response.getOutput());

service.shutdownExecutor();
```

Set `ARK_MODEL` to a model ID from your account before running the example.

## Usage

### Chat Completions

```java
import com.volcengine.ark.runtime.models.chat.*;

ChatCompletionRequest request = ChatCompletionRequest.builder()
        .model(System.getenv("ARK_MODEL"))
        .messages(Arrays.asList(
                ChatCompletionRequestUserMessage.builder()
                        .role(ChatCompletionRequestMessageType.USER)
                        .content(ChatCompletionMessageContent.ofString(
                                "What is the capital of France?"))
                        .build()))
        .build();

ChatCompletionResponse completion = service.createChatCompletion(request);
System.out.println(completion.getChoices().get(0).getMessage().getContent());
```

### Embeddings

```java
import com.volcengine.ark.runtime.models.embedding.*;

EmbeddingRequest request = EmbeddingRequest.builder()
        .model("doubao-embedding-text-240715")
        .input(Arrays.asList("Hello world", "Goodbye world"))
        .build();

EmbeddingResponse response = service.createEmbedding(request);
System.out.println(response.getData().get(0).getEmbedding());
```

## Streaming

Streaming methods return an RxJava `Flowable` that emits events as they
arrive. The SDK automatically sets `stream=true` when you call a streaming
method -- you do not need to set it on the request builder.

### Streaming Responses

```java
import io.reactivex.Flowable;
import com.volcengine.ark.runtime.models.responses.*;

ResponsesRequest request = ResponsesRequest.builder()
        .model(System.getenv("ARK_MODEL"))
        .input(ResponsesInput.ofString("Write a haiku about Java."))
        .build();

Flowable<ResponseStreamEvent> stream = service.streamResponse(request);

stream.blockingForEach(event -> {
    if (event instanceof ResponseTextDeltaEvent) {
        ResponseTextDeltaEvent delta = (ResponseTextDeltaEvent) event;
        System.out.print(delta.getDelta());
    }
});
```

### Streaming Chat Completions

```java
Flowable<ChatCompletionChunk> stream = service.streamChatCompletion(request);

stream.blockingForEach(chunk -> {
    if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
        String content = chunk.getChoices().get(0).getDelta().getContent();
        if (content != null) {
            System.out.print(content);
        }
    }
});
```

## Function Calling

Build tools with `FunctionTool.builder()` and pass them in the request:

```java
import com.volcengine.ark.runtime.models.responses.*;
import java.util.*;

Map<String, Object> city = new HashMap<>();
city.put("type", "string");
city.put("description", "City name");

Map<String, Object> properties = new HashMap<>();
properties.put("city", city);

Map<String, Object> parameters = new HashMap<>();
parameters.put("type", "object");
parameters.put("properties", properties);
parameters.put("required", Collections.singletonList("city"));

FunctionTool weatherTool = FunctionTool.builder()
        .name("get_weather")
        .description("Get the current weather for a city")
        .parameters(parameters)
        .build();

ResponsesRequest request = ResponsesRequest.builder()
        .model(System.getenv("ARK_MODEL"))
        .input(ResponsesInput.ofString("What is the weather in Beijing?"))
        .tools(Collections.singletonList(Tool.ofFunction(weatherTool)))
        .build();

Response response = service.createResponse(request);
// Inspect response.getOutput() for function_call items
```

## Error Handling

API errors are thrown as `ArkException` (or its subclasses). You can catch
and inspect the HTTP status code and error body:

```java
try {
    Response response = service.createResponse(request);
} catch (ArkHttpException e) {
    System.err.println("HTTP " + e.statusCode + ": " + e.getMessage());
} catch (ArkException e) {
    System.err.println("Error: " + e.getMessage());
}
```

## Examples

For detailed usage guidance and legacy migration, see
[`docs/README.md`](docs/README.md) and
[`docs/migration.md`](docs/migration.md).

Runnable single-file programs are available in the
[examples/](./examples) directory:

- **[Volcengine China examples](./examples/volc)** -- Chat, Responses, images, video generation, embeddings, files, tokenization, batch APIs, and resource APIs using `ArkService.volc()`
- **[BytePlus examples](./examples/byteplus)** -- supported counterparts using `ArkService.byteplus()` and BytePlus model IDs

MCP is demonstrated in both clouds with `ark-beta-mcp: true`. Other built-in-tool examples are CN-only and explicitly send their required beta headers.

## Requirements

- Java 8 or later
- Maven 3.6+ (for building from source)

### Runtime dependencies

The SDK uses OkHttp, Retrofit, Jackson, and RxJava internally. These are
declared as transitive dependencies and pulled in automatically by your
build tool.

## Shutdown

`ArkService` manages an internal thread pool for streaming. When you are
done using the client, shut it down to release resources:

```java
service.shutdownExecutor();
```

## License

This project is licensed under the Apache License 2.0. See [LICENSE](./LICENSE).
For third-party open-source software notices, see
[THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md).
