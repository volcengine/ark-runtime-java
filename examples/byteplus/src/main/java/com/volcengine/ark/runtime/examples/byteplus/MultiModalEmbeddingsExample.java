package com.volcengine.ark.runtime.examples.byteplus;

import com.volcengine.ark.runtime.models.multimodal_embedding.EmbeddingInput;
import com.volcengine.ark.runtime.models.multimodal_embedding.EmbeddingInputType;
import com.volcengine.ark.runtime.models.multimodal_embedding.ImageURL;
import com.volcengine.ark.runtime.models.multimodal_embedding.MultiModalEmbeddingRequest;
import com.volcengine.ark.runtime.models.multimodal_embedding.MultiModalEmbeddingResponse;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MultiModalEmbeddingsExample {

    /**
     * Authentication
     * 1.If you authorize your endpoint using an API key, you can set your api key to environment variable "ARK_API_KEY"
     * String apiKey = System.getenv("ARK_API_KEY");
     * ArkService service = ArkService.byteplus().apiKey(apiKey).build();
     * Note: If you use an API key, this API key will not be refreshed.
     * To prevent the API from expiring and failing after some time, choose an API key with no expiration date.
     * <p>
     * 2.If you authorize your endpoint with BytePlus Identity and Access Management (IAM),
     * set your access key and secret key in "BYTEPLUS_ACCESSKEY" and "BYTEPLUS_SECRETKEY".
     */

    static String apiKey = System.getenv("ARK_API_KEY");
    static ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    static Dispatcher dispatcher = new Dispatcher();
    static ArkService service = ArkService.byteplus().dispatcher(dispatcher).connectionPool(connectionPool).apiKey(apiKey).build();

    public static void main(String[] args) {
        System.out.println("\n----- multimodal embeddings request -----");

        List<EmbeddingInput> inputs = new ArrayList<>();
        inputs.add(EmbeddingInput.builder()
                .type(EmbeddingInputType.TEXT)
                .text("把图中的蓝天换成白云")
                .build());
        inputs.add(EmbeddingInput.builder()
                .type(EmbeddingInputType.IMAGE_URL)
                .imageUrl(ImageURL.builder()
                        .url("https://ark-project.tos-cn-beijing.volces.com/images/view.jpeg")
                        .build())
                .build());

        MultiModalEmbeddingRequest multiModalEmbeddingRequest = MultiModalEmbeddingRequest.builder()
                .model("skylark-embedding-vision-251215")
                .input(inputs)
                .build();

        MultiModalEmbeddingResponse res = service.createMultiModalEmbeddings(multiModalEmbeddingRequest);
        System.out.println(res);

        // shutdown service after all requests is finished
        service.shutdownExecutor();
    }
}
