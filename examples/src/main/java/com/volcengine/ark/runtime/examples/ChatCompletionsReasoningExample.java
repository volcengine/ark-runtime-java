package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.chat.ChatCompletionMessageContent;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestMessage;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestMessageType;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestUserMessage;
import com.volcengine.ark.runtime.models.chat.Thinking;
import com.volcengine.ark.runtime.models.chat.ThinkingMode;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ChatCompletionsReasoningExample {

    /**
     * Authentication
     * 1.If you authorize your endpoint using an API key, you can set your api key to environment variable "ARK_API_KEY"
     */

    static String apiKey = System.getenv("ARK_API_KEY");
    static ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    static Dispatcher dispatcher = new Dispatcher();
    static ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).apiKey(apiKey).build();

    public static void main(String[] args) {
        System.out.println("\n----- streaming request -----");
        final List<ChatCompletionRequestMessage> streamMessages = new ArrayList<>();
        streamMessages.add(ChatCompletionRequestUserMessage.builder()
                .role(ChatCompletionRequestMessageType.USER)
                .content(ChatCompletionMessageContent.ofString("How many Rs are there in the word 'strawberry'?"))
                .build());

        ChatCompletionRequest streamChatCompletionRequest = ChatCompletionRequest.builder()
                .model("${YOUR_ENDPOINT_ID}")
                .messages(streamMessages)
                .thinking(Thinking.builder().type(ThinkingMode.ENABLED).build())
                .build();

        service.streamChatCompletion(streamChatCompletionRequest)
                .doOnError(Throwable::printStackTrace)
                .blockingForEach(
                        chunk -> {
                            if (chunk.getChoices() == null || chunk.getChoices().isEmpty()) {
                                return;
                            }
                            String reasoning = chunk.getChoices().get(0).getDelta().getReasoningContent();
                            String content = chunk.getChoices().get(0).getDelta().getContent();
                            if (reasoning != null && !reasoning.isEmpty()) {
                                System.out.print(reasoning);
                            } else if (content != null) {
                                System.out.print(content);
                            }
                        }
                );

        System.out.println("\n----- standard request -----");
        final List<ChatCompletionRequestMessage> messages = new ArrayList<>();
        messages.add(ChatCompletionRequestUserMessage.builder()
                .role(ChatCompletionRequestMessageType.USER)
                .content(ChatCompletionMessageContent.ofString("How many Rs are there in the word 'strawberry'?"))
                .build());

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("${YOUR_ENDPOINT_ID}")
                .messages(messages)
                .thinking(Thinking.builder().type(ThinkingMode.ENABLED).build())
                .build();

        service.createChatCompletion(chatCompletionRequest).getChoices().forEach(
                choice -> {
                    System.out.println(choice.getMessage().getReasoningContent());
                    System.out.println(choice.getMessage().getContent());
                }
        );

        // shutdown service after all requests is finished
        service.shutdownExecutor();
    }
}
