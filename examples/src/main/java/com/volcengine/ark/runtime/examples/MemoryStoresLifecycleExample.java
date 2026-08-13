package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.memory.CreateMemoryRequest;
import com.volcengine.ark.runtime.models.memory.CreateMemoryStoreRequest;
import com.volcengine.ark.runtime.models.memory.ListMemoriesResponse;
import com.volcengine.ark.runtime.models.memory.Memory;
import com.volcengine.ark.runtime.models.memory.MemoryStore;
import com.volcengine.ark.runtime.models.memory.UpdateMemoryRequest;
import com.volcengine.ark.runtime.service.ArkService;

/**
 * Managed Agents — MemoryStore + Memory lifecycle example.
 *
 * <p>A MemoryStore is a namespace of Memory documents keyed by path. Covers
 * the full CRUD on both levels — creates a store, creates a memory in it,
 * gets/lists/updates that memory (SHA256 bumps), and cleans up.
 *
 * <p>Environment:
 * <pre>
 *   export ARK_API_KEY=...
 * </pre>
 */
public class MemoryStoresLifecycleExample {

    public static void main(String[] args) {
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("set ARK_API_KEY");
        }

        ArkService service = ArkService.builder().apiKey(apiKey).build();

        // 1. Create a memory store.
        CreateMemoryStoreRequest storeReq = new CreateMemoryStoreRequest();
        storeReq.setName("example-store-" + System.nanoTime());
        MemoryStore store = service.createMemoryStore(storeReq);
        System.out.printf("store:      id=%s name=%s%n", store.getId(), store.getName());

        Memory mem = null;
        try {
            // 2. Create a memory doc inside it.
            CreateMemoryRequest memReq = new CreateMemoryRequest();
            memReq.setPath("/example/note-" + System.nanoTime() + ".md");
            memReq.setContent("hello from ark-runtime-java example");
            mem = service.createMemory(store.getId(), memReq);
            System.out.printf("memory:     id=%s path=%s sha256=%s%n",
                mem.getId(), mem.getPath(), mem.getContentSha256());

            // 3. Get + list.
            Memory got = service.getMemory(store.getId(), mem.getId());
            System.out.printf("get:        id=%s path=%s%n", got.getId(), got.getPath());

            ListMemoriesResponse listed = service.listMemories(
                store.getId(), null, null, null, 10, null);
            System.out.printf("list:       %d items in store%n", listed.getData().size());

            // 4. Update — the SHA256 should change after new content.
            UpdateMemoryRequest updReq = new UpdateMemoryRequest();
            updReq.setContent("updated content");
            service.updateMemory(store.getId(), mem.getId(), updReq);
            Memory got2 = service.getMemory(store.getId(), mem.getId());
            System.out.printf("updated:    id=%s new_sha256=%s (was %s)%n",
                got2.getId(), got2.getContentSha256(), mem.getContentSha256());

            // 5. Delete memory (store cleaned up in `finally`).
            service.deleteMemory(store.getId(), mem.getId());
            System.out.printf("memory:     deleted id=%s%n", mem.getId());
            mem = null;
        } finally {
            try {
                service.deleteMemoryStore(store.getId());
                System.out.printf("store:      deleted id=%s%n", store.getId());
            } catch (Exception e) {
                System.err.printf("cleanup deleteMemoryStore(%s): %s%n", store.getId(), e);
            }
            service.shutdownExecutor();
        }
    }
}
