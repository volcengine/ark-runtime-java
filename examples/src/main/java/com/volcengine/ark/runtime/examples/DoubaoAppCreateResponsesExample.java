package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.responses.DoubaoAppCallBlock;
import com.volcengine.ark.runtime.models.responses.DoubaoAppCallBlockOutputText;
import com.volcengine.ark.runtime.models.responses.DoubaoAppCallBlockReasoningSearch;
import com.volcengine.ark.runtime.models.responses.DoubaoAppCallBlockReasoningText;
import com.volcengine.ark.runtime.models.responses.DoubaoAppCallBlockSearch;
import com.volcengine.ark.runtime.models.responses.DoubaoAppFeature;
import com.volcengine.ark.runtime.models.responses.DoubaoAppFeatureItem;
import com.volcengine.ark.runtime.models.responses.DoubaoAppFeatureMode;
import com.volcengine.ark.runtime.models.responses.DoubaoAppTool;
import com.volcengine.ark.runtime.models.responses.ItemDoubaoAppCall;
import com.volcengine.ark.runtime.models.responses.OutputItem;
import com.volcengine.ark.runtime.models.responses.Response;
import com.volcengine.ark.runtime.models.responses.ResponseDoubaoAppCallOutputTextDeltaEvent;
import com.volcengine.ark.runtime.models.responses.ResponseDoubaoAppCallReasoningSearchCompletedEvent;
import com.volcengine.ark.runtime.models.responses.ResponseDoubaoAppCallReasoningSearchSearchingEvent;
import com.volcengine.ark.runtime.models.responses.ResponseDoubaoAppCallReasoningTextDeltaEvent;
import com.volcengine.ark.runtime.models.responses.ResponseDoubaoAppCallSearchCompletedEvent;
import com.volcengine.ark.runtime.models.responses.ResponseDoubaoAppCallSearchSearchingEvent;
import com.volcengine.ark.runtime.models.responses.ResponseStreamEvent;
import com.volcengine.ark.runtime.models.responses.ResponsesInput;
import com.volcengine.ark.runtime.models.responses.ResponsesRequest;
import com.volcengine.ark.runtime.models.responses.Tool;
import com.volcengine.ark.runtime.models.responses.UserLocation;
import com.volcengine.ark.runtime.service.ArkService;
import io.reactivex.Flowable;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DoubaoAppCreateResponsesExample {

    private static final String modelName = "doubao-seed-2-1-pro-260628";

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

        Map<String, String> headers = new HashMap<>();
        // add this header to enable the beta feature
        headers.put("ark-beta-doubao-app", "true");


        System.out.println("\n----- Example of NormalChat-----");

        List<Tool> chatTools = Arrays.<Tool>asList(
                DoubaoAppTool.builder()
                        .feature(
                                DoubaoAppFeature.builder().chat(
                                        DoubaoAppFeatureItem.builder()
                                                .type(DoubaoAppFeatureMode.ENABLED)
                                                .roleDescription("你是豆包，你会用俏皮的语言回答用户的问题")
                                                .build()
                                ).build()
                        ).build()
        );

        ResponsesRequest request = ResponsesRequest.builder()
                .model(modelName)

                .tools(chatTools)
                .input(ResponsesInput.ofString("你好，介绍一下你自己"))
                .build();

        Response response1;

        try {
            response1 = service.createResponse(request, headers);
            printResponseObject(response1);
        } catch (Exception e) {
            System.err.println("Create Response Error " + e.getMessage());
            return;
        }

        System.out.println("\n----- Example of Deep chat -----");

        List<Tool> deepChatTools = Arrays.<Tool>asList(
                DoubaoAppTool.builder()
                        .feature(
                                DoubaoAppFeature.builder().deepChat(
                                        DoubaoAppFeatureItem.builder()
                                                .type(DoubaoAppFeatureMode.ENABLED)
                                                .roleDescription("你是豆包，你会用俏皮的语言回答用户的问题")
                                                .build()
                                ).build()
                        ).build()
        );

        ResponsesRequest request2 = ResponsesRequest.builder()
                .model(modelName)

                .tools(deepChatTools)
                .input(ResponsesInput.ofString("你好，介绍一下你自己"))
                .build();


        try {
            Flowable<ResponseStreamEvent> streamResponse2 = service.streamResponse(request2, headers);
            streamResponse2.blockingForEach(
                    DoubaoAppCreateResponsesExample::printStreamEvent
            );
        } catch (Exception e) {
            System.err.println("Create Response Error " + e.getMessage());
            return;
        }

        System.out.println("\n----- Example of Ai Search -----");

        List<Tool> aiSearchTools = Arrays.<Tool>asList(
                DoubaoAppTool.builder()
                        .feature(
                                DoubaoAppFeature.builder().aiSearch(
                                        DoubaoAppFeatureItem.builder()
                                                .type(DoubaoAppFeatureMode.ENABLED)
                                                .roleDescription("你是豆包，你会用俏皮的语言回答用户的问题")
                                                .build()
                                ).build()
                        ).userLocation(UserLocation.builder()
                                .type(UserLocation.TypeEnum.fromValue("approximate"))
                                .city("北京")
                                .build())
                        .build()
        );

        ResponsesRequest request3 = ResponsesRequest.builder()
                .model(modelName)

                .tools(aiSearchTools)
                .input(ResponsesInput.ofString("你好，介绍一下你自己"))
                .build();


        try {
            Flowable<ResponseStreamEvent> streamResponse3 = service.streamResponse(request3, headers);
            streamResponse3.blockingForEach(
                    DoubaoAppCreateResponsesExample::printStreamEvent
            );
        } catch (Exception e) {
            System.err.println("Create Response Error " + e.getMessage());
            return;
        }

        System.out.println("\n----- Example of Reasoning Search -----");

        List<Tool> reasoningSearchTools = Arrays.<Tool>asList(
                DoubaoAppTool.builder()
                        .feature(
                                DoubaoAppFeature.builder().reasoningSearch(
                                        DoubaoAppFeatureItem.builder()
                                                .type(DoubaoAppFeatureMode.ENABLED)
                                                .roleDescription("你是豆包，你会用俏皮的语言回答用户的问题")
                                                .build()
                                ).build()
                        ).userLocation(UserLocation.builder()
                                .type(UserLocation.TypeEnum.fromValue("approximate"))
                                .city("北京")
                                .build())
                        .build()
        );

        ResponsesRequest request4 = ResponsesRequest.builder()
                .model(modelName)

                .tools(reasoningSearchTools)
                .input(ResponsesInput.ofString("你好，介绍一下你自己"))
                .build();


        try {
            Flowable<ResponseStreamEvent> streamResponse4 = service.streamResponse(request4, headers);
            streamResponse4.blockingForEach(
                    DoubaoAppCreateResponsesExample::printStreamEvent
            );
        } catch (Exception e) {
            System.err.println("Create Response Error " + e.getMessage());
            return;
        }


        service.shutdownExecutor();
    }

    private static void printStreamEvent(ResponseStreamEvent event) {
        if (event instanceof ResponseDoubaoAppCallOutputTextDeltaEvent) {
            System.out.print(((ResponseDoubaoAppCallOutputTextDeltaEvent) event).getDelta());
        }
        if (event instanceof ResponseDoubaoAppCallReasoningTextDeltaEvent) {
            System.out.print(((ResponseDoubaoAppCallReasoningTextDeltaEvent) event).getDelta());
        }
        if (event instanceof ResponseDoubaoAppCallSearchSearchingEvent) {
            System.out.print(((ResponseDoubaoAppCallSearchSearchingEvent) event).getSearchingState());
        }
        if (event instanceof ResponseDoubaoAppCallReasoningSearchSearchingEvent) {
            System.out.print(((ResponseDoubaoAppCallReasoningSearchSearchingEvent) event).getSearchingState());
        }
        if (event instanceof ResponseDoubaoAppCallSearchCompletedEvent) {
            System.out.print(((ResponseDoubaoAppCallSearchCompletedEvent) event).getResults());
        }
        if (event instanceof ResponseDoubaoAppCallReasoningSearchCompletedEvent) {
            System.out.print(((ResponseDoubaoAppCallReasoningSearchCompletedEvent) event).getResults());
        }
    }

    private static void printResponseObject(Response responseObject) {
        System.out.println("Response ID: " + responseObject.getId());
        System.out.println("Status: " + responseObject.getStatus());
        if (responseObject.getOutput() != null && !responseObject.getOutput().isEmpty()) {
            for (OutputItem outputItem : responseObject.getOutput()) {
                if (outputItem instanceof ItemDoubaoAppCall) {
                    ItemDoubaoAppCall itemDoubaoAppCall = (ItemDoubaoAppCall) outputItem;
                    System.out.println("Doubao Blocks:");
                    if (itemDoubaoAppCall.getBlocks() != null) {
                        for (DoubaoAppCallBlock block : itemDoubaoAppCall.getBlocks()) {
                            if (block instanceof DoubaoAppCallBlockOutputText) {
                                System.out.println("Output Text: " + ((DoubaoAppCallBlockOutputText) block).getText());
                            } else if (block instanceof DoubaoAppCallBlockReasoningText) {
                                System.out.println("Reasoning Text: " + ((DoubaoAppCallBlockReasoningText) block).getReasoningText());
                            } else if (block instanceof DoubaoAppCallBlockSearch) {
                                System.out.println("Search Result: " + ((DoubaoAppCallBlockSearch) block).getResults());
                            } else if (block instanceof DoubaoAppCallBlockReasoningSearch) {
                                System.out.println("Reasoning Search Result: " + ((DoubaoAppCallBlockReasoningSearch) block).getResults());
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
