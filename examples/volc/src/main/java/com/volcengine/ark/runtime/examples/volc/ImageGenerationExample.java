package com.volcengine.ark.runtime.examples.volc;

import com.volcengine.ark.runtime.models.images.CreateImageGenerationRequest;
import com.volcengine.ark.runtime.models.images.ImageGenerationResponse;
import com.volcengine.ark.runtime.models.images.ResponseFormat;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.concurrent.TimeUnit;

public class ImageGenerationExample {

    /**
     * Authentication
     * 1. If you authorize your endpoint using an API key, set the API key to environment variable "ARK_API_KEY":
     *    String apiKey = System.getenv("ARK_API_KEY");
     *    ArkService service = ArkService.volc().apiKey(apiKey).build();
     * Note: API keys do not refresh — pick one with no expiration.
     */
    static String apiKey = System.getenv("ARK_API_KEY");
    static ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    static Dispatcher dispatcher = new Dispatcher();
    static ArkService service = ArkService.volc().dispatcher(dispatcher).connectionPool(connectionPool).apiKey(apiKey).build();

    public static void main(String[] args) {
        String seedreamModel = System.getenv().getOrDefault("SEEDREAM_MODEL", "doubao-seedream-5-0-pro-260628");

        System.out.println("\n----- [Seedream] Generate Images Request -----");
        CreateImageGenerationRequest request = new CreateImageGenerationRequest()
                .model(seedreamModel)
                .prompt("龙与地下城女骑士背景是起伏的平原，目光从镜头转向平原")
                .responseFormat(ResponseFormat.URL)
                .seed(1234567890L)
                .watermark(true)
                .size("1024x1024");

        ImageGenerationResponse response = service.generateImages(request);
        if (response.getError() != null) {
            System.err.println("Error: " + response.getError().getCode() + " — " + response.getError().getMessage());
        } else if (response.getData() != null && !response.getData().isEmpty()) {
            System.out.println("Image URL: " + response.getData().get(0).getUrl());
        }
        service.shutdownExecutor();
    }
}
