# Ark Runtime Java SDK

The Ark Runtime Java SDK provides convenient access to the Volcengine Ark
REST API from Java 8+ applications. It includes typed request/response
models for every API endpoint, synchronous and streaming helpers, and
automatic retry logic.

## Installation

### Maven

```xml
<dependency>
    <groupId>com.volcengine</groupId>
    <artifactId>ark-runtime</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.volcengine:ark-runtime:0.1.0'
```

## Usage

### Authentication

The SDK reads the `ARK_API_KEY` environment variable by default. You can
also pass the key explicitly via the builder:

```java
ArkService service = ArkService.builder()
        .apiKey(System.getenv("ARK_API_KEY"))
        .build();
```

### Responses API

The Responses API is the primary interface for generating text with Ark
models.

```java
import com.volcengine.ark.runtime.ArkService;
import com.volcengine.ark.runtime.models.responses.*;

ArkService service = ArkService.builder()
        .apiKey(System.getenv("ARK_API_KEY"))
        .build();

ResponsesRequest request = ResponsesRequest.builder()
        .model("doubao-seed-2-1-pro-260628")
        .input(ResponsesInput.ofString("Explain Java generics in two sentences."))
        .build();

Response response = service.createResponse(request);
System.out.println(response.getOutput());

service.shutdownExecutor();
```

### Chat Completions

```java
import com.volcengine.ark.runtime.models.chat.*;

ChatCompletionRequest request = ChatCompletionRequest.builder()
        .model("doubao-seed-2-1-pro-260628")
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
import com.volcengine.ark.runtime.models.responses.events.*;

ResponsesRequest request = ResponsesRequest.builder()
        .model("doubao-seed-2-1-pro-260628")
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

FunctionTool weatherTool = FunctionTool.builder()
        .name("get_weather")
        .description("Get the current weather for a city")
        .parameters(Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of("type", "string", "description", "City name")),
                "required", List.of("city")))
        .build();

ResponsesRequest request = ResponsesRequest.builder()
        .model("doubao-seed-2-1-pro-260628")
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

## API Reference

| API                    | Method                                                                     |
|------------------------|----------------------------------------------------------------------------|
| Responses              | `service.createResponse()` / `service.streamResponse()`                    |
| Chat Completions       | `service.createChatCompletion()` / `service.streamChatCompletion()`        |
| Embeddings             | `service.createEmbedding()`                                                |
| Multimodal Embeddings  | `service.createMultiModalEmbedding()`                                      |
| Content Generation     | `service.createContentGenerationTask()`                                    |
| Images                 | `service.createImageGeneration()`                                          |
| Files                  | `service.createFile()` / `service.listFiles()` / `service.deleteFile()`    |
| Tokenization           | `service.createTokenization()`                                             |

## Examples

Runnable single-file programs are available in the
[examples/](./examples) directory:

- **Responses** -- `CreateResponseExample`, `ResponseOperationsExample`
- **Chat Completions** -- `ChatCompletionsExample`, `ChatCompletionsFunctionCallExample`, `ChatCompletionsVisionExample`
- **Embeddings** -- `EmbeddingsExample`, `MultiModalEmbeddingsExample`, `SparseEmbeddingsExample`
- **Content Generation** -- `ContentGenerationTaskExample`
- **Images** -- `ImageGenerationExample`
- **Files** -- `FileUploadExample`, `FileVideoResponsesExample`
- **Tokenization** -- `TokenizationExample`
- **Batch** -- `BatchChatCompletionsExample`

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

Apache License 2.0.
