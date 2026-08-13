package com.volcengine.ark.runtime.examples;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.models.chat.ChatCompletionMessageContent;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestMessage;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestMessageType;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestUserMessage;
import com.volcengine.ark.runtime.models.chat.ChatCompletionResponseFormat;
import com.volcengine.ark.runtime.models.chat.ChatCompletionResponseFormatJsonSchema;
import com.volcengine.ark.runtime.models.chat.ResponseFormatType;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ChatCompletionsStructuredOutputsExample {

    /**
     * Authentication
     * 1.If you authorize your endpoint using an API key, you can set your api key to environment variable "ARK_API_KEY"
     */

    static String apiKey = System.getenv("ARK_API_KEY");
    static ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    static Dispatcher dispatcher = new Dispatcher();
    static ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).apiKey(apiKey).build();

    public static void main(String[] args) throws JsonProcessingException {
        System.out.println("\n----- standard request -----");
        final List<ChatCompletionRequestMessage> messages = new ArrayList<>();
        messages.add(ChatCompletionRequestUserMessage.builder()
                .role(ChatCompletionRequestMessageType.USER)
                .content(ChatCompletionMessageContent.ofString("Describe the ENIAC: who built it and what year."))
                .build());

        // The schema can be loaded from any source; here we build it inline.
        String schemaJson = "{" +
                "\"type\":\"object\"," +
                "\"properties\":{" +
                "  \"name\":{\"type\":\"string\"}," +
                "  \"year_built\":{\"type\":\"integer\"}," +
                "  \"organization\":{\"type\":\"string\"}" +
                "}," +
                "\"required\":[\"name\",\"year_built\",\"organization\"]," +
                "\"additionalProperties\":false" +
                "}";
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> schema = mapper.readValue(schemaJson, new TypeReference<Map<String, Object>>() {});

        final ChatCompletionResponseFormat responseFormat = ChatCompletionResponseFormat.builder()
                .type(ResponseFormatType.JSON_SCHEMA)
                .jsonSchema(ChatCompletionResponseFormatJsonSchema.builder()
                        .name("historical_computer")
                        .description("Notable information about a historical computer")
                        .schema(schema)
                        .strict(true)
                        .build())
                .build();

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("${YOUR_ENDPOINT_ID}")
                .messages(messages)
                .responseFormat(responseFormat)
                .build();

        service.createChatCompletion(chatCompletionRequest).getChoices().forEach(
                choice -> System.out.println(choice.getMessage().getContent())
        );

        // shutdown service after all requests is finished
        service.shutdownExecutor();
    }
}
