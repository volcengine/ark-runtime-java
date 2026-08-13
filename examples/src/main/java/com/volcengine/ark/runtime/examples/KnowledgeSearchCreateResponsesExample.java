package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.responses.Annotation;
import com.volcengine.ark.runtime.models.responses.ItemFunctionKnowledgeSearch;
import com.volcengine.ark.runtime.models.responses.ItemOutputMessage;
import com.volcengine.ark.runtime.models.responses.KnowledgeSearchTool;
import com.volcengine.ark.runtime.models.responses.OutputContentItem;
import com.volcengine.ark.runtime.models.responses.OutputContentItemText;
import com.volcengine.ark.runtime.models.responses.OutputItem;
import com.volcengine.ark.runtime.models.responses.Response;
import com.volcengine.ark.runtime.models.responses.ResponseCompletedEvent;
import com.volcengine.ark.runtime.models.responses.ResponseKnowledgeSearchCallCompletedEvent;
import com.volcengine.ark.runtime.models.responses.ResponseKnowledgeSearchCallFailedEvent;
import com.volcengine.ark.runtime.models.responses.ResponseKnowledgeSearchCallInProgressEvent;
import com.volcengine.ark.runtime.models.responses.ResponseKnowledgeSearchCallSearchingEvent;
import com.volcengine.ark.runtime.models.responses.ResponseOutputItemAddedEvent;
import com.volcengine.ark.runtime.models.responses.ResponseOutputItemDoneEvent;
import com.volcengine.ark.runtime.models.responses.ResponseReasoningSummaryTextDeltaEvent;
import com.volcengine.ark.runtime.models.responses.ResponseStreamEvent;
import com.volcengine.ark.runtime.models.responses.ResponseTextDeltaEvent;
import com.volcengine.ark.runtime.models.responses.ResponseTextDoneEvent;
import com.volcengine.ark.runtime.models.responses.ResponsesInput;
import com.volcengine.ark.runtime.models.responses.ResponsesRequest;
import com.volcengine.ark.runtime.models.responses.Thinking;
import com.volcengine.ark.runtime.models.responses.ThinkingMode;
import com.volcengine.ark.runtime.models.responses.Tool;
import com.volcengine.ark.runtime.service.ArkService;
import io.reactivex.Flowable;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

public class KnowledgeSearchCreateResponsesExample {

    private static final String MODEL_NAME = "your-model-name";
    private static final String KNOWLEDGE_RESOURCE_ID = "kb-xxxxxxxxxxxxxx";

    public static void main(String[] args) {
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null) {
            System.out.println("ARK_API_KEY environment variable not set");
            return;
        }

        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(5000);
        dispatcher.setMaxRequestsPerHost(5000);
        ArkService service = ArkService.builder()
                .dispatcher(dispatcher)
                .timeout(Duration.ofHours(1))
                .connectionPool(connectionPool)
                .apiKey(apiKey)
                .build();

        Map<String, String> headers = new HashMap<>();
        headers.put("ark-beta-knowledge-search", "true");

        System.out.println("\n----- Example of Knowledge Search -----");

        List<Tool> tools = Arrays.<Tool>asList(
                KnowledgeSearchTool.builder()
                        .knowledgeResourceId(KNOWLEDGE_RESOURCE_ID)
                        .description("Your knowledge base description")
                        .limit(3L)
                        .denseWeight(0.5)
                        .build()
        );

        ResponsesRequest request = ResponsesRequest.builder()
                .model(MODEL_NAME)

                .maxToolCalls(1L)
                .thinking(Thinking.builder().type(ThinkingMode.DISABLED).build())
                .tools(tools)
                .input(ResponsesInput.ofString("请介绍一下知识库"))
                .build();

        Response response;
        try {
            response = service.createResponse(request, headers);
            printResponseObject(response);
        } catch (Exception e) {
            System.err.println("Create Response Error: " + e.getMessage());
            return;
        }

        System.out.println("\n----- Example of Knowledge Search (Streaming) -----");

        List<Tool> streamTools = Arrays.<Tool>asList(
                KnowledgeSearchTool.builder()
                        .knowledgeResourceId(KNOWLEDGE_RESOURCE_ID)
                        .description("Your knowledge base description")
                        .limit(5L)
                        .build()
        );

        ResponsesRequest streamRequest = ResponsesRequest.builder()
                .model(MODEL_NAME)

                .tools(streamTools)
                .input(ResponsesInput.ofString("请介绍一下知识库"))
                .build();

        try {
            Flowable<ResponseStreamEvent> streamResponse = service.streamResponse(streamRequest, headers);
            streamResponse.blockingForEach(KnowledgeSearchCreateResponsesExample::printStreamEvent);
        } catch (Exception e) {
            System.err.println("Create Response Error: " + e.getMessage());
            return;
        }

        service.shutdownExecutor();
    }

    private static void printStreamEvent(ResponseStreamEvent event) {
        if (event instanceof ResponseKnowledgeSearchCallInProgressEvent) {
            System.out.println("[KnowledgeSearch] In progress, itemId: " + ((ResponseKnowledgeSearchCallInProgressEvent) event).getItemId());
        }
        if (event instanceof ResponseKnowledgeSearchCallSearchingEvent) {
            System.out.println("[KnowledgeSearch] Searching, itemId: " + ((ResponseKnowledgeSearchCallSearchingEvent) event).getItemId());
        }
        if (event instanceof ResponseKnowledgeSearchCallCompletedEvent) {
            System.out.println("[KnowledgeSearch] Completed, itemId: " + ((ResponseKnowledgeSearchCallCompletedEvent) event).getItemId());
        }
        if (event instanceof ResponseKnowledgeSearchCallFailedEvent) {
            System.out.println("[KnowledgeSearch] Failed, itemId: " + ((ResponseKnowledgeSearchCallFailedEvent) event).getItemId());
        }
        if (event instanceof ResponseReasoningSummaryTextDeltaEvent) {
            System.out.print(((ResponseReasoningSummaryTextDeltaEvent) event).getDelta());
        }
        if (event instanceof ResponseOutputItemAddedEvent) {
            System.out.println("[OutputItem] Added: " + ((ResponseOutputItemAddedEvent) event).getItem().getType());
        }
        if (event instanceof ResponseTextDeltaEvent) {
            System.out.print(((ResponseTextDeltaEvent) event).getDelta());
        }
        if (event instanceof ResponseTextDoneEvent) {
            System.out.println("\n[Text] End");
        }
        if (event instanceof ResponseOutputItemDoneEvent) {
            System.out.println("\n[OutputItem] Done: " + ((ResponseOutputItemDoneEvent) event).getItem().getType());
        }
        if (event instanceof ResponseCompletedEvent) {
            System.out.println("[Response] Completed. Usage: " + ((ResponseCompletedEvent) event).getResponse().getUsage());
        }
    }

    private static void printResponseObject(Response responseObject) {
        System.out.println("Response ID: " + responseObject.getId());
        System.out.println("Status: " + responseObject.getStatus());

        if (responseObject.getOutput() != null && !responseObject.getOutput().isEmpty()) {
            for (OutputItem outputItem : responseObject.getOutput()) {
                if (outputItem instanceof ItemOutputMessage) {
                    ItemOutputMessage messageItem = (ItemOutputMessage) outputItem;
                    System.out.println("Message role: " + messageItem.getRole());
                    if (messageItem.getContent() != null) {
                        for (OutputContentItem content : messageItem.getContent()) {
                            if (content instanceof OutputContentItemText) {
                                OutputContentItemText textContent = (OutputContentItemText) content;
                                System.out.println("Output text: " + textContent.getText());
                                if (textContent.getAnnotations() != null && !textContent.getAnnotations().isEmpty()) {
                                    System.out.println("Annotations count: " + textContent.getAnnotations().size());
                                    for (Annotation annotation : textContent.getAnnotations()) {
                                        // The generated Annotation interface only exposes getType(); the
                                        // concrete fields (doc_id, url, ...) live on DocCitation / UrlCitation.
                                        System.out.println("  - Annotation type: " + annotation.getType());
                                    }
                                }
                            }
                        }
                    }
                } else if (outputItem instanceof ItemFunctionKnowledgeSearch) {
                    ItemFunctionKnowledgeSearch knowledgeSearchItem = (ItemFunctionKnowledgeSearch) outputItem;
                    System.out.println("Knowledge search item:");
                    System.out.println("  - queries: " + knowledgeSearchItem.getQueries());
                    System.out.println("  - knowledgeResourceId: " + knowledgeSearchItem.getKnowledgeResourceId());
                    System.out.println("  - status: " + knowledgeSearchItem.getStatus());
                }
            }
        }

        if (responseObject.getUsage() != null) {
            System.out.println("Usage: " + responseObject.getUsage());
        }
    }
}
