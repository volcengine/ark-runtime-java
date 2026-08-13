package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.chat.ChatCompletionContentPart;
import com.volcengine.ark.runtime.models.chat.ChatCompletionContentPartImage;
import com.volcengine.ark.runtime.models.chat.ChatCompletionContentPartImageImageUrl;
import com.volcengine.ark.runtime.models.chat.ChatCompletionContentPartText;
import com.volcengine.ark.runtime.models.chat.ChatCompletionContentPartType;
import com.volcengine.ark.runtime.models.chat.ChatCompletionMessageContent;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestMessage;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestMessageType;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestUserMessage;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ChatCompletionsVisionExample {

    /**
     * Authentication
     * 1.If you authorize your endpoint using an API key, you can set your api key to environment variable "ARK_API_KEY"
     * String apiKey = System.getenv("ARK_API_KEY");
     * ArkService service = ArkService.builder().apiKey(apiKey).build();
     */

    static String apiKey = System.getenv("ARK_API_KEY");
    static ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    static Dispatcher dispatcher = new Dispatcher();
    static ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).apiKey(apiKey).build();

    public static void main(String[] args) {
        System.out.println("----- image input -----");
        final List<ChatCompletionRequestMessage> messages = new ArrayList<>();
        final List<ChatCompletionContentPart> multiParts = new ArrayList<>();
        multiParts.add(ChatCompletionContentPartText.builder()
                .type(ChatCompletionContentPartType.TEXT)
                .text("这是哪里？")
                .build());
        multiParts.add(ChatCompletionContentPartImage.builder()
                .type(ChatCompletionContentPartType.IMAGE_URL)
                .imageUrl(ChatCompletionContentPartImageImageUrl.builder()
                        .url("https://ark-project.tos-cn-beijing.volces.com/images/view.jpeg")
                        .build())
                .build());
        messages.add(ChatCompletionRequestUserMessage.builder()
                .role(ChatCompletionRequestMessageType.USER)
                .content(ChatCompletionMessageContent.ofList(multiParts))
                .build());

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model("${YOUR_ENDPOINT_ID}")
                .messages(messages)
                .build();

        service.createChatCompletion(chatCompletionRequest).getChoices().forEach(
                choice -> System.out.println(choice.getMessage().getContent()));

        // shutdown service after all requests is finished
        service.shutdownExecutor();
    }
}
