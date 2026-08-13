package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.chat.ChatCompletionMessageContent;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestMessage;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestMessageType;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestSystemMessage;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequestUserMessage;
import com.volcengine.ark.runtime.models.chat.ChatCompletionResponse;
import com.volcengine.ark.runtime.service.ArkService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Demonstrates the parallel synchronous batch inference endpoint
 * (/api/v3/batch/chat/completions). Unlike the standard batch-job flow, each
 * call is a regular request; the SDK just gates on the per-model
 * {@code Retry-After} response header via {@link
 * com.volcengine.ark.runtime.interceptor.BatchInterceptor}.
 *
 * <p>Streaming is not supported on the batch endpoint — passing
 * {@code stream=true} to {@code createBatchChatCompletion} will throw
 * {@link com.volcengine.ark.runtime.exception.ArkException}.</p>
 */
public class BatchChatCompletionsExample {

    static String apiKey = System.getenv("ARK_API_KEY");
    static ArkService service = ArkService.builder().apiKey(apiKey).build();

    public static void main(String[] args) throws Exception {
        System.out.println("\n----- batch chat completion: parallel fan-out -----");

        List<String> prompts = Arrays.asList(
                "常见的十字花科植物有哪些？",
                "推荐几道家常菜",
                "用一句话介绍字节跳动",
                "春天适合去哪里旅游？"
        );

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(prompts.size(), 8));
        try {
            List<CompletableFuture<ChatCompletionResponse>> futures = new ArrayList<>();
            for (String prompt : prompts) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    List<ChatCompletionRequestMessage> messages = new ArrayList<>();
                    messages.add(ChatCompletionRequestSystemMessage.builder()
                            .role(ChatCompletionRequestMessageType.SYSTEM)
                            .content(ChatCompletionMessageContent.ofString(
                                    "你是豆包，是由字节跳动开发的 AI 人工智能助手"))
                            .build());
                    messages.add(ChatCompletionRequestUserMessage.builder()
                            .role(ChatCompletionRequestMessageType.USER)
                            .content(ChatCompletionMessageContent.ofString(prompt))
                            .build());

                    ChatCompletionRequest req = ChatCompletionRequest.builder()
                            .model("${YOUR_ENDPOINT_ID}")
                            .messages(messages)
                            .build();

                    return service.createBatchChatCompletion(req);
                }, pool));
            }

            for (int i = 0; i < futures.size(); i++) {
                ChatCompletionResponse result = futures.get(i).get();
                System.out.println("\nprompt[" + i + "]: " + prompts.get(i));
                result.getChoices().forEach(choice ->
                        System.out.println("  -> " + choice.getMessage().getContent()));
            }
        } finally {
            pool.shutdown();
            service.shutdownExecutor();
        }
    }
}
