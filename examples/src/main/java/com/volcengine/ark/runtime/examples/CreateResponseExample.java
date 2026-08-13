package com.volcengine.ark.runtime.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.models.responses.CacheMode;
import com.volcengine.ark.runtime.models.responses.FunctionTool;
import com.volcengine.ark.runtime.models.responses.ItemFunctionToolCall;
import com.volcengine.ark.runtime.models.responses.ItemOutputMessage;
import com.volcengine.ark.runtime.models.responses.ItemReasoning;
import com.volcengine.ark.runtime.models.responses.McpApprovalMode;
import com.volcengine.ark.runtime.models.responses.McpRequireApproval;
import com.volcengine.ark.runtime.models.responses.McpTool;
import com.volcengine.ark.runtime.models.responses.OutputContentItem;
import com.volcengine.ark.runtime.models.responses.OutputContentItemText;
import com.volcengine.ark.runtime.models.responses.OutputItem;
import com.volcengine.ark.runtime.models.responses.ReasoningSummaryPart;
import com.volcengine.ark.runtime.models.responses.Response;
import com.volcengine.ark.runtime.models.responses.ResponseCaching;
import com.volcengine.ark.runtime.models.responses.ResponseCompletedEvent;
import com.volcengine.ark.runtime.models.responses.ResponseFunctionCallArgumentsDoneEvent;
import com.volcengine.ark.runtime.models.responses.ResponseOutputItemAddedEvent;
import com.volcengine.ark.runtime.models.responses.ResponseOutputItemDoneEvent;
import com.volcengine.ark.runtime.models.responses.ResponseReasoningSummaryTextDeltaEvent;
import com.volcengine.ark.runtime.models.responses.ResponseStreamEvent;
import com.volcengine.ark.runtime.models.responses.ResponseTextConfig;
import com.volcengine.ark.runtime.models.responses.ResponseTextDeltaEvent;
import com.volcengine.ark.runtime.models.responses.ResponseTextDoneEvent;
import com.volcengine.ark.runtime.models.responses.ResponsesInput;
import com.volcengine.ark.runtime.models.responses.ResponsesRequest;
import com.volcengine.ark.runtime.models.responses.TextFormat;
import com.volcengine.ark.runtime.models.responses.TextFormatType;
import com.volcengine.ark.runtime.models.responses.Thinking;
import com.volcengine.ark.runtime.models.responses.ThinkingMode;
import com.volcengine.ark.runtime.models.responses.Tool;
import com.volcengine.ark.runtime.service.ArkService;
import io.reactivex.Flowable;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CreateResponseExample {

    private static final String modelName = "doubao-seed-2-1-pro-260628";
    private static final String testSchema = "{\"type\":\"object\",\"properties\":{\"steps\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"explanation\":{\"type\":\"string\"},\"output\":{\"type\":\"string\"}},\"required\":[\"explanation\",\"output\"],\"additionalProperties\":false}},\"final_answer\":{\"type\":\"string\"}},\"required\":[\"steps\",\"final_answer\"],\"additionalProperties\":false}";

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
        ArkService service = ArkService.builder().dispatcher(dispatcher).timeout(Duration.ofHours(1)).connectionPool(connectionPool).apiKey(apiKey).build();


        System.out.println("\n----- [Standard Usage] Request 1-----");

        ResponsesRequest request = ResponsesRequest.builder()
                .model(modelName)

                .instructions("请使用中文与我沟通")
                .input(ResponsesInput.ofString("你好"))
                .thinking(Thinking.builder().type(ThinkingMode.ENABLED).build())
                .build();

        Response response1;

        try {
            response1 = service.createResponse(request);
            printResponseObject(response1);
        } catch (Exception e) {
            System.err.println("Create Response 1 Error " + e.getMessage());
            return;
        }

        System.out.println("\n----- [With Previous Response] Request 2-----");

        // NOTICE: the response store is async, so need a latency here
        try {
            Thread.sleep(200);
        } catch (Throwable e) {
            // ignore
        }

        ResponsesRequest request2 = ResponsesRequest.builder()
                .model(modelName)

                .previousResponseId(response1.getId()) // with previous response id
                .instructions("请使用中文与我沟通")
                .input(ResponsesInput.ofString("继续"))
                .thinking(Thinking.builder().type(ThinkingMode.DISABLED).build())
                .build();

        Response response2;

        try {
            response2 = service.createResponse(request2);
            printResponseObject(response2);
        } catch (Exception e) {
            System.err.println("Create Response 2 Error: " + e.getMessage());
        }

        System.out.println("\n----- [With MultiMedia Input] Request 3-----");

        // Multi-media input uses the union ResponsesInput list form. We build a
        // list of InputItem variants (ItemEasyMessage here with text content).
        ResponsesRequest request3 = ResponsesRequest.builder()
                .model(modelName)

                .input(ResponsesInput.ofList(Collections.<com.volcengine.ark.runtime.models.responses.InputItem>singletonList(
                        com.volcengine.ark.runtime.models.responses.ItemEasyMessage.builder()
                                .role(com.volcengine.ark.runtime.models.responses.MessageRole.USER)
                                .content(com.volcengine.ark.runtime.models.responses.MessageContent.ofString("介绍一下你自己"))
                                .build())))
                .thinking(Thinking.builder().type(ThinkingMode.ENABLED).build())
                .build();

        Response response3;

        try {
            response3 = service.createResponse(request3);
            printResponseObject(response3);
        } catch (Exception e) {
            System.err.println("Create Response 3 Error: " + e.getMessage());
        }


        System.out.println("\n----- [Stream Request] Request 4-----");

        ResponsesRequest request4 = ResponsesRequest.builder()
                .model(modelName)

                .instructions("请使用中文与我沟通")
                .input(ResponsesInput.ofString("你好"))
                .thinking(Thinking.builder().type(ThinkingMode.ENABLED).build())
                .build();

        try {
            service.streamResponse(request4)
                    .doOnError(Throwable::printStackTrace)
                    .blockingForEach(
                            CreateResponseExample::printStreamEvent
                    );
        } catch (Exception e) {
            System.err.println("Create Response 4 Error " + e.getMessage());
        }

        System.out.println("\n----- [Stream FunctionCall Request] Request 5-----");

        try {
            Map<String, Object> weatherParams = new HashMap<>();
            weatherParams.putAll(new ObjectMapper().readValue(
                    "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"integer\",\"description\":\"first number to add\"},\"b\":{\"type\":\"integer\",\"description\":\"second number to add\"}},\"required\":[\"a\",\"b\"]}",
                    Map.class));
            Tool weatherTool = FunctionTool.builder()
                    .name("sum")
                    .description("add two integers and get sum")
                    .parameters(weatherParams)
                    .build();

            // Query is shaped to trigger the `sum` tool: ask the model to add
            // two specific integers so it has a clear reason to invoke the
            // function rather than answer in plain text.
            ResponsesRequest fcRequest = ResponsesRequest.builder()
                    .model(modelName)
    
                    .input(ResponsesInput.ofString("请用 sum 工具帮我计算 1 + 2 等于多少"))
                    .thinking(Thinking.builder().type(ThinkingMode.ENABLED).build())
                    .tools(Collections.singletonList(weatherTool))
                    .build();

            AtomicReference<String> fcResponseId = new AtomicReference<>();
            AtomicReference<String> fcCallbackId = new AtomicReference<>();

            Flowable<ResponseStreamEvent> fcResponse = service.streamResponse(fcRequest);
            fcResponse.doOnError(Throwable::printStackTrace)
                    .blockingForEach(
                            event -> {
                                printStreamEvent(event);
                                if (event instanceof ResponseOutputItemDoneEvent) {
                                    if (((ResponseOutputItemDoneEvent) event).getItem() instanceof ItemFunctionToolCall) {
                                        fcCallbackId.set(((ItemFunctionToolCall) ((ResponseOutputItemDoneEvent) event).getItem()).getCallId());
                                    }
                                }
                                if (event instanceof ResponseCompletedEvent) {
                                    fcResponseId.set(((ResponseCompletedEvent) event).getResponse().getId());
                                }
                            }
                    );

            // The tool-call output flow uses the union ResponsesInput list form
            // to attach the function-call output back into the conversation.
            ResponsesRequest fcOutputRequest = ResponsesRequest.builder()
                    .model(modelName)
    
                    .previousResponseId(fcResponseId.get()) // use the context before
                    .input(ResponsesInput.ofList(Collections.<com.volcengine.ark.runtime.models.responses.InputItem>singletonList(
                            com.volcengine.ark.runtime.models.responses.ItemFunctionToolCallOutput.builder()
                                    .callId(fcCallbackId.get())
                                    .output(com.volcengine.ark.runtime.models.responses.MessageContent.ofString("3"))
                                    .build())))
                    .thinking(Thinking.builder().type(ThinkingMode.ENABLED).build())
                    .tools(Collections.singletonList(weatherTool))
                    .build();

            System.out.println(new ObjectMapper().writeValueAsString(fcOutputRequest));

            System.out.println("=== fc final response ===");

            service.streamResponse(fcOutputRequest)
                    .doOnError(Throwable::printStackTrace)
                    .blockingForEach(
                            CreateResponseExample::printStreamEvent
                    );

        } catch (Exception e) {
            System.err.println("Create Response 4 Error " + e.getMessage());
        }

        System.out.println("\n----- [Stream Request with MCP] Request 5-----");

        ResponsesRequest request5 = ResponsesRequest.builder()
                .model(modelName)

                .tools(Collections.singletonList(McpTool.builder()
                        .serverLabel("deepwiki-test")
                        .serverUrl("https://mcp.deepwiki.com/mcp")
                        .requireApproval(McpRequireApproval.ofMcpApprovalMode(McpApprovalMode.NEVER))
                        .build()))
                .input(ResponsesInput.ofString("你好"))
                .thinking(Thinking.builder().type(ThinkingMode.ENABLED).build())
                .build();

        try {
            service.streamResponse(request5)
                    .doOnError(Throwable::printStackTrace)
                    .blockingForEach(
                            CreateResponseExample::printStreamEvent
                    );
        } catch (Exception e) {
            System.err.println("Create Response 5 Error " + e.getMessage());
        }

        System.out.println("\n----- [Request with Caching] Request 7-----");
        StringBuilder longPromptBuilder = new StringBuilder("你是豆包，你必须用4个字回答我的问题");
        for (int i = 0; i < 1000; i++) {
            longPromptBuilder.append("你是豆包，你必须用4个字回答我的问题");
        }
        String longPrompt = longPromptBuilder.toString();
        ResponsesRequest request7 = ResponsesRequest.builder()
                .model(modelName)

                .caching(ResponseCaching.builder().type(CacheMode.ENABLED).prefix(true).build())
                .input(ResponsesInput.ofString(longPrompt))
                .thinking(Thinking.builder().type(ThinkingMode.DISABLED).build())
                .build();

        String cacheResponseId = null;

        try {
            Response cachePrefix = service.createResponse(request7);
            System.out.println("=== cache prefix response ===");
            printResponseObject(cachePrefix);
            cacheResponseId = cachePrefix.getId();
        } catch (Exception e) {
            System.err.println("Create Response 7 Error " + e.getMessage());
        }

        ResponsesRequest cachedRequest = ResponsesRequest.builder()
                .model(modelName)

                .previousResponseId(cacheResponseId)
                .input(ResponsesInput.ofString("你好"))
                .thinking(Thinking.builder().type(ThinkingMode.DISABLED).build())
                .build();

        try {
            Response cacheHitResponse = service.createResponse(cachedRequest);
            System.out.println("=== cache hit response ===");
            printResponseObject(cacheHitResponse);
        } catch (Exception e) {
            System.err.println("Create Cached Response Error " + e.getMessage());
        }

        try {
            System.out.println("\n----- [Request with JsonSchema] Request 8-----");
            Map<String, Object> schema = new ObjectMapper().readValue(testSchema, Map.class);
            ResponsesRequest request8 = ResponsesRequest.builder()
                    .input(ResponsesInput.ofString("你好"))
                    .model(modelName)
    
                    .text(ResponseTextConfig.builder().format(TextFormat.builder()
                            .type(TextFormatType.fromValue("json_schema"))
                            .name("math_reasoning")
                            .schema(schema)
                            .build()).build())
                    .build();

            Response response8 = service.createResponse(request8);
            System.out.println("=== response 8 ===");
            printResponseObject(response8);
        } catch (Exception e) {
            System.err.println("Create Response 8 Error " + e.getMessage());
        }


        service.shutdownExecutor();
    }

    private static void printStreamEvent(ResponseStreamEvent event) {
        if (event instanceof ResponseReasoningSummaryTextDeltaEvent) {
            System.out.print(((ResponseReasoningSummaryTextDeltaEvent) event).getDelta());
        }
        if (event instanceof ResponseOutputItemAddedEvent) {
            System.out.println("OutputItem " + (((ResponseOutputItemAddedEvent) event).getItem().getType()) + " Start: ");
        }
        if (event instanceof ResponseTextDeltaEvent) {
            System.out.print(((ResponseTextDeltaEvent) event).getDelta());
        }
        if (event instanceof ResponseTextDoneEvent) {
            System.out.print("\nOutputText End.\n");
        }
        if (event instanceof ResponseOutputItemDoneEvent) {
            System.out.println("\nOutputItem " + ((ResponseOutputItemDoneEvent) event).getItem().getType() + " End.\n");
        }
        if (event instanceof ResponseFunctionCallArgumentsDoneEvent) {
            System.out.println("FunctionCall Arguments: " + ((ResponseFunctionCallArgumentsDoneEvent) event).getArguments());
        }
        if (event instanceof ResponseCompletedEvent) {
            System.out.println("Response Completed. Usage = " + ((ResponseCompletedEvent) event).getResponse().getUsage());
        }
    }

    private static void printResponseObject(Response responseObject) {
        System.out.println("Response ID: " + responseObject.getId());
        System.out.println("Status: " + responseObject.getStatus());
        if (responseObject.getOutput() != null && !responseObject.getOutput().isEmpty()) {
            for (OutputItem outputItem : responseObject.getOutput()) {
                if (outputItem instanceof ItemReasoning) {
                    ItemReasoning itemReasoning = (ItemReasoning) outputItem;
                    System.out.println("Reasoning Summary:");
                    if (itemReasoning.getSummary() != null) {
                        for (ReasoningSummaryPart summaryPart : itemReasoning.getSummary()) {
                            System.out.println(summaryPart.getText());
                        }
                    }
                }
                if (outputItem instanceof ItemOutputMessage) {
                    ItemOutputMessage itemOutputMessage = (ItemOutputMessage) outputItem;
                    System.out.println("Output Message:");
                    if (itemOutputMessage.getContent() != null) {
                        for (OutputContentItem item : itemOutputMessage.getContent()) {
                            if (item instanceof OutputContentItemText) {
                                OutputContentItemText textItem = (OutputContentItemText) item;
                                System.out.println(textItem.getText());
                            }
                        }
                    }
                }
            }
        }
        if (responseObject.getUsage() != null) {
            System.out.println("Usage: " + responseObject.getUsage());
        }
    }
}
