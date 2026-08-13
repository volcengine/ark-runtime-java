package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.images.CreateImageGenerationRequest;
import com.volcengine.ark.runtime.models.images.ImageGenerationResponse;
import com.volcengine.ark.runtime.models.images.ImageGenerationStreamEventType;
import com.volcengine.ark.runtime.models.images.ResponseFormat;
import com.volcengine.ark.runtime.models.images.SequentialImageGenerationMode;
import com.volcengine.ark.runtime.models.images.SequentialImageGenerationOptions;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class ImageGenerationExample {

    /**
     * Authentication
     * 1. If you authorize your endpoint using an API key, set the API key to environment variable "ARK_API_KEY":
     *    String apiKey = System.getenv("ARK_API_KEY");
     *    ArkService service = ArkService.builder().apiKey(apiKey).build();
     * Note: API keys do not refresh — pick one with no expiration.
     */
    static String apiKey = System.getenv("ARK_API_KEY");
    static ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    static Dispatcher dispatcher = new Dispatcher();
    static ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).apiKey(apiKey).build();

    public static void main(String[] args) {
        String model = "${YOUR_ENDPOINT_ID}";

        System.out.println("\n----- [Seedream] Generate Images Request -----");
        CreateImageGenerationRequest request = new CreateImageGenerationRequest()
                .model(model)
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

        System.out.println("\n----- [Seededit] Generate Images Request (with input image) -----");
        request = new CreateImageGenerationRequest()
                .model(model)
                .prompt("把背景换成黄昏的沙漠")
                .image(Arrays.asList("${YOUR_IMAGE_URL_HERE}"))
                .responseFormat(ResponseFormat.URL)
                .seed(1234567890L)
                .watermark(true)
                .size("adaptive");
        response = service.generateImages(request);
        if (response.getError() != null) {
            System.err.println("Error: " + response.getError().getCode() + " — " + response.getError().getMessage());
        } else if (response.getData() != null && !response.getData().isEmpty()) {
            System.out.println("Edited Image URL: " + response.getData().get(0).getUrl());
        }

        System.out.println("\n----- [Seedream] Sequential Image Generation -----");
        SequentialImageGenerationOptions seqOpts = new SequentialImageGenerationOptions().maxImages(3);
        request = new CreateImageGenerationRequest()
                .model(model)
                .prompt("星球大战, 场面壮观, 需要描述3个连续场面")
                .responseFormat(ResponseFormat.URL)
                .seed(1234567890L)
                .watermark(true)
                .size("1024x1024")
                .sequentialImageGeneration(SequentialImageGenerationMode.AUTO)
                .sequentialImageGenerationOptions(seqOpts);
        response = service.generateImages(request);
        if (response.getError() != null) {
            System.err.println("Error: " + response.getError().getCode() + " — " + response.getError().getMessage());
        } else if (response.getData() != null) {
            for (int i = 0; i < response.getData().size(); i++) {
                System.out.printf("[%d] size=%s url=%s%n", i,
                        response.getData().get(i).getSize(),
                        response.getData().get(i).getUrl());
            }
        }

        System.out.println("\n----- [Seedream] Streaming Sequential Image Generation -----");
        SequentialImageGenerationOptions streamSeqOpts = new SequentialImageGenerationOptions().maxImages(2);
        CreateImageGenerationRequest streamRequest = new CreateImageGenerationRequest()
                .model(model)
                .prompt("星球大战 三个连续场面")
                .responseFormat(ResponseFormat.URL)
                .seed(1234567890L)
                .watermark(false)
                .size("1024x1024")
                .sequentialImageGeneration(SequentialImageGenerationMode.AUTO)
                .sequentialImageGenerationOptions(streamSeqOpts);
        service.streamGenerateImages(streamRequest)
                .doOnError(Throwable::printStackTrace)
                .blockingForEach(event -> {
                    if (event == null) return;
                    if (event.getType() == ImageGenerationStreamEventType.IMAGE_GENERATION_PARTIAL_FAILED) {
                        if (event.getError() != null) {
                            System.err.println("partial_failed: " + event.getError().getCode() + " — " + event.getError().getMessage());
                        }
                    } else if (event.getType() == ImageGenerationStreamEventType.IMAGE_GENERATION_PARTIAL_SUCCEEDED) {
                        if (event.getError() == null && event.getUrl() != null) {
                            System.out.printf("recv.Size=%s recv.Url=%s%n", event.getSize(), event.getUrl());
                        }
                    } else if (event.getType() == ImageGenerationStreamEventType.IMAGE_GENERATION_COMPLETED) {
                        if (event.getUsage() != null) {
                            System.out.println("recv.Usage=" + event.getUsage());
                        }
                    }
                });

        service.shutdownExecutor();
    }
}
