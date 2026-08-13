package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.responses.DeleteResponseResponse;
import com.volcengine.ark.runtime.models.responses.ResponseIncludable;
import com.volcengine.ark.runtime.models.responses.ListInputItemsResponse;
import com.volcengine.ark.runtime.models.responses.Response;
import com.volcengine.ark.runtime.models.responses.ResponsesInput;
import com.volcengine.ark.runtime.models.responses.ResponsesRequest;
import com.volcengine.ark.runtime.models.responses.Thinking;
import com.volcengine.ark.runtime.models.responses.ThinkingMode;
import com.volcengine.ark.runtime.service.ArkService;
import com.volcengine.ark.runtime.models.responses.DeleteResponseRequest;
import com.volcengine.ark.runtime.models.responses.GetResponseRequest;
import com.volcengine.ark.runtime.models.responses.ListInputItemsRequest;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class ResponseOperationsExample {

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

        System.out.println("===== CreateResponse Example=====");

        // NOTE: The file-upload API has not been wired in the new SDK yet. The
        // request below uses the string variant of ResponsesInput; extend once the
        // file APIs land and multi-media input items can be attached.
        ResponsesRequest request = ResponsesRequest.builder()
                .model(modelName)

                .input(ResponsesInput.ofString("你好"))
                .thinking(Thinking.builder().type(ThinkingMode.DISABLED).build())
                .build();
        Response resp = service.createResponse(request);

        try {
            Thread.sleep(200); // the response object write is async, so need a latency here
        } catch (Throwable e) {
            // ignore
        }

        System.out.println("===== GetResponse Example=====");

        Response getResult = service.getResponse(
                GetResponseRequest.builder().responseId(resp.getId()).build()
        );
        System.out.println(getResult);

        // List Input Items
        System.out.println("===== List Input Items Example=====");

        ListInputItemsResponse listResult = service.listResponseInputItems(
                ListInputItemsRequest.builder().responseId(getResult.getId())
                        .include(Collections.singletonList(ResponseIncludable.MESSAGE_INPUT_IMAGE_IMAGE_URL))
                        .build()
        );

        System.out.println(listResult);

        System.out.println("===== DeleteResponse Example=====");

        DeleteResponseResponse deleteResult = service.deleteResponse(
                DeleteResponseRequest.builder().responseId(getResult.getId()).build()
        );

        System.out.println(deleteResult);

        // when response deleted, get again will throw exception
        try {
            service.getResponse(
                    GetResponseRequest.builder().responseId(getResult.getId()).build()
            );
        } catch (Exception e) {
            System.out.println("GetResponse after delete: " + e.getMessage());
        }

        service.shutdownExecutor();
    }
}
