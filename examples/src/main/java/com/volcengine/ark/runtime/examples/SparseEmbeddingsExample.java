package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.multimodal_embedding.EmbeddingInput;
import com.volcengine.ark.runtime.models.multimodal_embedding.EmbeddingInputType;
import com.volcengine.ark.runtime.models.multimodal_embedding.MultiModalEmbeddingRequest;
import com.volcengine.ark.runtime.models.multimodal_embedding.MultiModalEmbeddingResponse;
import com.volcengine.ark.runtime.models.multimodal_embedding.SparseEmbeddingConfig;
import com.volcengine.ark.runtime.models.multimodal_embedding.SparseEmbeddingMode;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SparseEmbeddingsExample {

    /**
     * Authentication
     * 1.If you authorize your endpoint using an API key, you can set your api key to environment variable "ARK_API_KEY"
     * String apiKey = System.getenv("ARK_API_KEY");
     * ArkService service = ArkService.builder().apiKey(apiKey).build();
     * Note: If you use an API key, this API key will not be refreshed.
     * To prevent the API from expiring and failing after some time, choose an API key with no expiration date.
     * <p>
     * 2.If you authorize your endpoint with Volcengine Identity and Access Management（IAM), set your api key to environment variable "VOLC_ACCESSKEY", "VOLC_SECRETKEY"
     * To get your ak&sk, please refer to this document(https://www.volcengine.com/docs/6291/65568)
     * For more information，please check this document（https://www.volcengine.com/docs/82379/1263279）
     */

    static String apiKey = System.getenv("ARK_API_KEY");
    static ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    static Dispatcher dispatcher = new Dispatcher();
    static ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).apiKey(apiKey).build();

    public static void main(String[] args) {
        System.out.println("\n----- sparse embeddings request -----");

        List<EmbeddingInput> inputs = new ArrayList<>();
        inputs.add(EmbeddingInput.builder()
                .type(EmbeddingInputType.TEXT)
                .text("花椰菜又称菜花、花菜，是一种常见的蔬菜。")
                .build());

        MultiModalEmbeddingRequest request = MultiModalEmbeddingRequest.builder()
                .model("doubao-embedding-vision-250615")
                .input(inputs)
                .sparseEmbedding(SparseEmbeddingConfig.builder()
                        .type(SparseEmbeddingMode.ENABLED)
                        .build())
                .build();

        MultiModalEmbeddingResponse res = service.createMultiModalEmbeddings(request);
        System.out.println(res.getData().getSparseEmbedding());

        // shutdown service after all requests is finished
        service.shutdownExecutor();
    }
}
