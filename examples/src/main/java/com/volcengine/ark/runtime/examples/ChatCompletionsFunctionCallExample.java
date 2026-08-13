package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.chat.ChatCompletionMessageContent;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestMessage;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestMessageType;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestUserMessage;
import com.volcengine.ark.runtime.models.chat.ChatCompletionTool;
import com.volcengine.ark.runtime.models.chat.FunctionObject;
import com.volcengine.ark.runtime.models.chat.ToolType;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ChatCompletionsFunctionCallExample {

    /**
     * Authentication
     * 1.If you authorize your endpoint using an API key, you can set your api key to environment variable "ARK_API_KEY"
     */

    static String apiKey = System.getenv("ARK_API_KEY");
    static ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    static Dispatcher dispatcher = new Dispatcher();
    static ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).apiKey(apiKey).build();

    public static void main(String[] args) {
        System.out.println("\n----- function call request -----");
        final List<ChatCompletionRequestMessage> messages = new ArrayList<>();
        messages.add(ChatCompletionRequestUserMessage.builder()
                .role(ChatCompletionRequestMessageType.USER)
                .content(ChatCompletionMessageContent.ofString("What's the weather like in Boston today?"))
                .build());

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        Map<String, String> location = new HashMap<>();
        location.put("type", "string");
        location.put("description", "The city and state, e.g. San Francisco, CA");
        properties.put("location", location);
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("location"));

        FunctionObject weatherFn = FunctionObject.builder()
                .name("get_current_weather")
                .description("Get the current weather in a given location")
                .parameters(parameters)
                .build();

        final List<ChatCompletionTool> tools = Arrays.asList(
                ChatCompletionTool.builder()
                        .type(ToolType.FUNCTION)
                        .function(weatherFn)
                        .build()
        );

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("${YOUR_ENDPOINT_ID}")
                .messages(messages)
                .tools(tools)
                .build();

        service.createChatCompletion(chatCompletionRequest).getChoices().forEach(System.out::println);

        service.streamChatCompletion(chatCompletionRequest)
                .doOnError(Throwable::printStackTrace)
                .blockingForEach(System.out::println);

        // shutdown service after all requests is finished
        service.shutdownExecutor();
    }
}
