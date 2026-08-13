package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.content_generation.ContentGenerationTask;
import com.volcengine.ark.runtime.models.content_generation.ContentItem;
import com.volcengine.ark.runtime.models.content_generation.ContentType;
import com.volcengine.ark.runtime.models.content_generation.CreateContentGenerationTaskRequest;
import com.volcengine.ark.runtime.models.content_generation.CreateContentGenerationTaskResponse;
import com.volcengine.ark.runtime.models.content_generation.ImageURL;
import com.volcengine.ark.runtime.models.content_generation.ListContentGenerationTasksResponse;
import com.volcengine.ark.runtime.models.content_generation.TaskStatus;
import com.volcengine.ark.runtime.service.ArkService;
import com.volcengine.ark.runtime.models.content_generation.ListContentGenerationTasksRequest;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ContentGenerationTaskExample {

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
        String model = "${MODEL EP_ID HERE}";

        System.out.println("\n----- CREATE Task Request -----");
        List<ContentItem> contents = new ArrayList<>();

        // Text content
        contents.add(new ContentItem()
                .type(ContentType.TEXT)
                .text("制作一段展示美丽自然风光的视频，包括山川、河流、森林和天空，充满平和与宁静的氛围，适合用于冥想或放松场景。 --ratio 1:1"));

        // Image URL content
        contents.add(new ContentItem()
                .type(ContentType.IMAGE_URL)
                .imageUrl(new ImageURL().url("${IMAGE URL HERE}"))
                // .role("first_frame")
        );

        CreateContentGenerationTaskRequest createRequest = new CreateContentGenerationTaskRequest()
                .model(model)
                .content(contents)
                .serviceTier("default")
                .executionExpiresAfter(3600L);
                // .callbackUrl("YOUR CALLBACK URL");

        CreateContentGenerationTaskResponse createResult = service.createContentGenerationTask(createRequest);
        System.out.println(createResult);

        System.out.println("\n----- GET Task Request -----");
        ContentGenerationTask getResult = service.getContentGenerationTask(createResult.getId());
        System.out.println(getResult);
        System.out.println("ServiceTier: " + getResult.getServiceTier());
        System.out.println("ExecutionExpiresAfter: " + getResult.getExecutionExpiresAfter());

        System.out.println("\n----- LIST Task Request -----");
        ListContentGenerationTasksRequest listRequest = new ListContentGenerationTasksRequest()
                .pageNum(1)
                .pageSize(10)
                .filterStatus(TaskStatus.RUNNING)
                .filterModel(model)
                .filterServiceTier("default");
                // .filterTaskIds(java.util.Arrays.asList(createResult.getId()));

        ListContentGenerationTasksResponse listResponse = service.listContentGenerationTasks(listRequest);
        System.out.println(listResponse);
        if (listResponse.getItems() != null && !listResponse.getItems().isEmpty()) {
            ContentGenerationTask item = listResponse.getItems().get(0);
            System.out.println("List Item ServiceTier: " + item.getServiceTier());
            System.out.println("List Item ExecutionExpiresAfter: " + item.getExecutionExpiresAfter());
        }

        System.out.println("\n----- DELETE Task Request -----");
        try {
            service.deleteContentGenerationTask(getResult.getId());
            System.out.println("deleted: " + getResult.getId());
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        service.shutdownExecutor();
    }
}
