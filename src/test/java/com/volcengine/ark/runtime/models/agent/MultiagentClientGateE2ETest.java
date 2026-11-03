// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.models.agent;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.volcengine.ark.runtime.service.ArkApi;
import com.volcengine.ark.runtime.service.ArkService;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Retrofit;

public class MultiagentClientGateE2ETest {
    private String token;
    private String baseUrl;
    private ArkService service;
    private ArkService legacyService;

    @Before
    public void setUp() {
        token = System.getenv("ARK_STG_TOKEN");
        Assume.assumeTrue("ARK_STG_TOKEN is required for Managed Agents E2E",
                token != null && !token.isEmpty());
        baseUrl = System.getenv("ARK_E2E_BASE_URL");
        Assume.assumeTrue("ARK_E2E_BASE_URL is required for Managed Agents E2E",
                baseUrl != null && !baseUrl.isEmpty());
        service = ArkService.builder().apiKey(token).baseUrl(baseUrl).retryTimes(0).build();
        legacyService = serviceWithUserAgent("ark-runtime-java/0.11.0");
    }

    @After
    public void tearDown() {
        if (legacyService != null) {
            legacyService.shutdownExecutor();
        }
        if (service != null) {
            service.shutdownExecutor();
        }
    }

    @Test
    public void currentClientSatisfiesGateAndLegacyClientIsRejected() {
        String modelId = System.getenv().getOrDefault(
                "ARK_E2E_MODEL_ID", "doubao-seed-1-6-250615");
        CreateAgentRequest create = new CreateAgentRequest()
                .name("sdk-gate-java-" + System.nanoTime())
                .model(new ModelConfig().id(modelId))
                .multiagent(dynamicConfig(Collections.singletonList(
                        new AgentRef().type(AgentRefType.SELF))));
        Agent created = service.createAgent(create);
        try {
            assertTrue(created.getMultiagent() != null);
            service.getAgent(created.getId());

            Agent updated = service.updateAgent(
                    created.getId(),
                    new UpdateAgentRequest()
                            .version(created.getVersion())
                            .multiagent(new MultiagentPatch()
                                    .type(MultiagentType.MULTIAGENT_20261001)
                                    .subagents(new MultiagentSubagentsConfig()
                                            .type(MultiagentFeatureType.ENABLED)
                                            .predefinedAgents(Collections.emptyList()))));
            assertTrue(updated.getVersion() > created.getVersion());

            ListAgentsResponse listed = service.listAgents(100, null, null, null);
            assertTrue(listed.getData().stream()
                    .anyMatch(item -> created.getId().equals(item.getId())));

            expectLegacyError(() -> legacyService.getAgent(created.getId()), "legacy get");
            expectLegacyError(
                    () -> legacyService.updateAgent(
                            created.getId(),
                            new UpdateAgentRequest()
                                    .version(updated.getVersion())
                                    .description("must be rejected")),
                    "legacy update");

            ListAgentsResponse legacyList = legacyService.listAgents(100, null, null, null);
            Agent legacyItem = legacyList.getData().stream()
                    .filter(item -> created.getId().equals(item.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("agent missing from legacy list"));
            assertNull(legacyItem.getMultiagent());
        } finally {
            service.deleteAgent(created.getId());
        }
    }

    private static MultiagentConfig dynamicConfig(List<AgentRef> members) {
        return new MultiagentConfig()
                .type(MultiagentType.MULTIAGENT_20261001)
                .subagents(new MultiagentSubagentsConfig()
                        .type(MultiagentFeatureType.ENABLED)
                        .predefinedAgents(members));
    }

    private ArkService serviceWithUserAgent(String userAgent) {
        OkHttpClient client = ArkService.defaultApiKeyClient(token, Duration.ofSeconds(60))
                .newBuilder()
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build()))
                .build();
        String retrofitBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        Retrofit retrofit = ArkService.defaultRetrofit(
                client, ArkService.defaultObjectMapper(), retrofitBaseUrl, null);
        return new ArkService(retrofit.create(ArkApi.class), client.dispatcher().executorService());
    }

    private static void expectLegacyError(Runnable action, String operation) {
        try {
            action.run();
            fail(operation + " unexpectedly succeeded");
        } catch (AssertionError error) {
            throw error;
        } catch (RuntimeException error) {
            assertTrue(operation + ": " + error.getMessage(),
                    error.getMessage() != null
                            && error.getMessage().contains("MULTIAGENT_UNSUPPORTED_BY_CLIENT"));
        }
    }
}
