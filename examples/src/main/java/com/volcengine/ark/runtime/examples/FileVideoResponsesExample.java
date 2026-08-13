package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.file.FileCreateRequest;
import com.volcengine.ark.runtime.models.file.FileObject;
import com.volcengine.ark.runtime.models.file.PreprocessConfigs;
import com.volcengine.ark.runtime.models.file.Purpose;
import com.volcengine.ark.runtime.models.file.Status;
import com.volcengine.ark.runtime.models.file.Video;
import com.volcengine.ark.runtime.models.responses.ContentItem;
import com.volcengine.ark.runtime.models.responses.ContentItemText;
import com.volcengine.ark.runtime.models.responses.ContentItemType;
import com.volcengine.ark.runtime.models.responses.ContentItemVideo;
import com.volcengine.ark.runtime.models.responses.InputItem;
import com.volcengine.ark.runtime.models.responses.ItemEasyMessage;
import com.volcengine.ark.runtime.models.responses.MessageContent;
import com.volcengine.ark.runtime.models.responses.MessageRole;
import com.volcengine.ark.runtime.models.responses.ResponseCreatedEvent;
import com.volcengine.ark.runtime.models.responses.ResponseStreamEvent;
import com.volcengine.ark.runtime.models.responses.ResponseTextDeltaEvent;
import com.volcengine.ark.runtime.models.responses.ResponseTextDoneEvent;
import com.volcengine.ark.runtime.models.responses.ResponsesInput;
import com.volcengine.ark.runtime.models.responses.ResponsesRequest;
import com.volcengine.ark.runtime.service.ArkService;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Upload a video, wait for preprocessing, then run a 2-turn Responses session
 * referencing the uploaded file_id.
 *
 * Demonstrates:
 *   - service.uploadFile(FileCreateRequest, java.io.File) with PreprocessConfigs (video.fps = 0.3)
 *   - service.waitForFileProcessing(fileId) for the active/failed terminal state
 *   - ContentItemVideo as input with file_id (no video_url) inside a streaming
 *     Responses call, plus previousResponseId for multi-turn
 */
public class FileVideoResponsesExample {

    private static final String MODEL = "doubao-seed-2-1-pro-260628";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: FileVideoResponsesExample <video-path>");
            System.exit(1);
        }
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("set ARK_API_KEY");
            System.exit(1);
        }

        ArkService service = ArkService.builder().apiKey(apiKey).build();

        File video = new File(args[0]);
        System.out.println("Uploading " + video.getAbsolutePath());
        FileCreateRequest request = FileCreateRequest.builder()
                .purpose(Purpose.USER_DATA)
                .preprocessConfigs(PreprocessConfigs.builder()
                        .video(Video.builder().fps(0.3).build())
                        .build())
                .build();
        FileObject uploaded = service.uploadFile(request, video);
        System.out.println("  uploaded id=" + uploaded.getId() + " status=" + uploaded.getStatus());

        FileObject ready = service.waitForFileProcessing(uploaded.getId());
        System.out.println("  processed status=" + ready.getStatus());
        if (!Status.ACTIVE.equals(ready.getStatus())) {
            System.err.println("file " + ready.getId() + " did not become active: status=" + ready.getStatus());
            service.shutdownExecutor();
            System.exit(1);
        }

        // ----- Turn 1: video + text input, streaming -----
        System.out.println("\nTurn 1: ask the model to analyze the video frame-by-frame");

        ContentItemVideo videoPart = new ContentItemVideo();
        videoPart.setType(ContentItemType.INPUT_VIDEO);
        videoPart.setFileId(ready.getId());

        ContentItemText textPart = new ContentItemText();
        textPart.setType(ContentItemType.INPUT_TEXT);
        textPart.setText("请逐帧分析视频内容");

        ItemEasyMessage userMessage = new ItemEasyMessage();
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(MessageContent.ofList(Arrays.<ContentItem>asList(videoPart, textPart)));

        ResponsesRequest req1 = ResponsesRequest.builder()
                .model(MODEL)
                .input(ResponsesInput.ofList(Collections.<InputItem>singletonList(userMessage)))

                .store(true)
                .build();

        AtomicReference<String> responseId = new AtomicReference<>("");
        service.streamResponse(req1)
                .doOnNext(event -> {
                    printEvent(event);
                    if (event instanceof ResponseCreatedEvent) {
                        responseId.set(((ResponseCreatedEvent) event).getResponse().getId());
                    }
                })
                .blockingSubscribe();

        // Response store is async; brief pause before referencing it
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ----- Turn 2: follow-up via previousResponseId -----
        System.out.println("\nTurn 2: follow-up referencing prior turn's response");
        ResponsesRequest req2 = ResponsesRequest.builder()
                .model(MODEL)
                .input(ResponsesInput.ofString("上一轮对话里视频里的内容是"))
                .previousResponseId(responseId.get())

                .store(true)
                .build();

        service.streamResponse(req2)
                .doOnNext(FileVideoResponsesExample::printEvent)
                .blockingSubscribe();

        service.shutdownExecutor();
    }

    private static void printEvent(ResponseStreamEvent event) {
        if (event instanceof ResponseTextDeltaEvent) {
            String delta = ((ResponseTextDeltaEvent) event).getDelta();
            if (delta != null) {
                System.out.print(delta);
            }
        } else if (event instanceof ResponseTextDoneEvent) {
            String text = ((ResponseTextDoneEvent) event).getText();
            System.out.println("\n[done] " + (text != null ? text : ""));
        }
    }
}
