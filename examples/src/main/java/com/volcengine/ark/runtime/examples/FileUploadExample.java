package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.file.FileCreateRequest;
import com.volcengine.ark.runtime.models.file.FileDeleted;
import com.volcengine.ark.runtime.models.file.FileListRequest;
import com.volcengine.ark.runtime.models.file.FileListResponse;
import com.volcengine.ark.runtime.models.file.FileObject;
import com.volcengine.ark.runtime.models.file.Purpose;
import com.volcengine.ark.runtime.service.ArkService;

import java.io.File;

public class FileUploadExample {

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

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: FileUploadExample <path>");
            System.exit(1);
        }
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("set ARK_API_KEY");
            System.exit(1);
        }

        ArkService service = ArkService.builder().apiKey(apiKey).build();

        // Upload a file
        FileCreateRequest request = FileCreateRequest.builder()
                .purpose(Purpose.USER_DATA)
                .build();
        FileObject uploaded = service.uploadFile(request, new File(args[0]));
        System.out.println("uploaded: id=" + uploaded.getId() + " status=" + uploaded.getStatus());

        // Wait for processing to complete
        FileObject ready = service.waitForFileProcessing(uploaded.getId());
        System.out.println("ready: status=" + ready.getStatus());

        // List files
        FileListResponse list = service.listFiles(FileListRequest.builder().limit(5L).build());
        System.out.println("listed " + list.getData().size() + " files; has_more=" + list.getHasMore());

        // Delete the uploaded file
        FileDeleted deleted = service.deleteFile(uploaded.getId());
        System.out.println("deleted: id=" + deleted.getId() + " ok=" + deleted.getDeleted());

        service.shutdownExecutor();
    }
}
