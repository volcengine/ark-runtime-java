package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.environment.CreateEnvironmentRequest;
import com.volcengine.ark.runtime.models.environment.DeleteEnvironmentResponse;
import com.volcengine.ark.runtime.models.environment.EnvConfig;
import com.volcengine.ark.runtime.models.environment.EnvConfigType;
import com.volcengine.ark.runtime.models.environment.Environment;
import com.volcengine.ark.runtime.models.environment.ListEnvironmentsResponse;
import com.volcengine.ark.runtime.models.environment.NetworkingConfig;
import com.volcengine.ark.runtime.models.environment.NetworkingType;
import com.volcengine.ark.runtime.models.environment.UpdateEnvironmentRequest;
import com.volcengine.ark.runtime.service.ArkService;

/**
 * Managed Agents — Environment lifecycle example.
 *
 * <p>An Environment is the sandbox (network + filesystem policy) an Agent runs
 * inside during a Session. This example uses the cloud environment with
 * unrestricted networking; production usage will typically restrict either.
 *
 * <p>Environment:
 * <pre>
 *   export ARK_API_KEY=...
 * </pre>
 */
public class EnvironmentsLifecycleExample {

    public static void main(String[] args) {
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("set ARK_API_KEY");
        }

        ArkService service = ArkService.builder().apiKey(apiKey).build();

        // 1. Create — cloud + unrestricted network.
        CreateEnvironmentRequest createReq = new CreateEnvironmentRequest();
        createReq.setName("example-env-" + System.nanoTime());
        EnvConfig cfg = new EnvConfig();
        cfg.setType(EnvConfigType.CLOUD);
        NetworkingConfig net = new NetworkingConfig();
        net.setType(NetworkingType.UNRESTRICTED);
        cfg.setNetworking(net);
        createReq.setConfig(cfg);
        Environment created = service.createEnvironment(createReq);
        System.out.printf("created:    id=%s name=%s%n", created.getId(), created.getName());

        try {
            // 2. Get
            Environment got = service.getEnvironment(created.getId());
            System.out.printf("get:        id=%s name=%s type=%s%n",
                got.getId(), got.getName(), got.getType());

            // 3. List
            ListEnvironmentsResponse listed = service.listEnvironments(5, null);
            System.out.printf("list:       %d items, next_page=%s%n",
                listed.getData().size(), listed.getNextPage());

            // 4. Update — attach a description.
            UpdateEnvironmentRequest updateReq = new UpdateEnvironmentRequest();
            updateReq.setDescription("updated by ark-runtime-java example");
            Environment updated = service.updateEnvironment(created.getId(), updateReq);
            System.out.printf("updated:    id=%s description=%s%n",
                updated.getId(), updated.getDescription());
        } finally {
            // 5. Delete
            DeleteEnvironmentResponse deleted = service.deleteEnvironment(created.getId());
            System.out.printf("deleted:    id=%s%n", deleted.getId());
            service.shutdownExecutor();
        }
    }
}
