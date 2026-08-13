package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.models.agent.Agent;
import com.volcengine.ark.runtime.models.agent.CreateAgentRequest;
import com.volcengine.ark.runtime.models.agent.DeleteAgentResponse;
import com.volcengine.ark.runtime.models.agent.ListAgentsResponse;
import com.volcengine.ark.runtime.models.agent.ModelConfig;
import com.volcengine.ark.runtime.models.agent.UpdateAgentRequest;
import com.volcengine.ark.runtime.service.ArkService;

/**
 * Managed Agents — Agent lifecycle example.
 *
 * <p>Runs against the outward /api/v3/agents endpoint. Exercises the smallest
 * useful CRUD sequence:
 *
 * <ul>
 *   <li>Create → Get → List → Update → ListVersions → Delete</li>
 * </ul>
 *
 * <p>Environment:
 * <pre>
 *   export ARK_API_KEY=...
 *   export ARK_MODEL_ID=doubao-seed-1-8-251228   # or whatever you have access to
 * </pre>
 */
public class AgentsLifecycleExample {

    public static void main(String[] args) {
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("set ARK_API_KEY");
        }
        String modelId = System.getenv().getOrDefault("ARK_MODEL_ID", "${YOUR_MODEL_ID}");

        ArkService service = ArkService.builder().apiKey(apiKey).build();

        // 1. Create
        String name = "example-agent-" + System.nanoTime();
        CreateAgentRequest createReq = new CreateAgentRequest();
        createReq.setName(name);
        ModelConfig model = new ModelConfig();
        model.setId(modelId);
        createReq.setModel(model);
        createReq.setDescription("created by ark-runtime-java example");
        Agent created = service.createAgent(createReq);
        System.out.printf("created:    id=%s version=%d name=%s%n",
            created.getId(), created.getVersion(), created.getName());

        try {
            // 2. Get
            Agent got = service.getAgent(created.getId());
            System.out.printf("get:        id=%s name=%s%n", got.getId(), got.getName());

            // 3. List — takes limit / page / created_at_gte / created_at_lte.
            ListAgentsResponse listed = service.listAgents(5, null, null, null);
            System.out.printf("list:       %d items, next_page=%s%n",
                listed.getData().size(), listed.getNextPage());

            // 4. Update — bumps version. Requires the previous version for
            //    optimistic concurrency control.
            UpdateAgentRequest updateReq = new UpdateAgentRequest();
            updateReq.setVersion(created.getVersion());
            updateReq.setDescription("updated by ark-runtime-java example");
            Agent updated = service.updateAgent(created.getId(), updateReq);
            System.out.printf("updated:    id=%s version=%d (was %d)%n",
                updated.getId(), updated.getVersion(), created.getVersion());

            // 5. List versions — should see at least v1 (create) + v2 (update).
            ListAgentsResponse versions = service.listAgentVersions(created.getId(), 10, null);
            System.out.printf("versions:   %d items%n", versions.getData().size());
        } finally {
            // 6. Delete
            DeleteAgentResponse deleted = service.deleteAgent(created.getId());
            System.out.printf("deleted:    id=%s%n", deleted.getId());
            service.shutdownExecutor();
        }
    }
}
