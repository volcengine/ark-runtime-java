// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.volcengine.ark.runtime.models.session.AgentWithUpgrades;
import com.volcengine.ark.runtime.models.session.CreateSessionUpgradeRequest;
import com.volcengine.ark.runtime.models.session.Session;
import io.reactivex.Single;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import retrofit2.http.POST;
import retrofit2.http.Path;

public class ArkServiceSessionUpgradeTest {
    @Test
    public void upgradeSessionPassesRequestAndReturnsSession() {
        CapturingArkApi handler = new CapturingArkApi();
        ArkService service = new ArkService(api(handler));
        CreateSessionUpgradeRequest request = request();

        Session out = service.upgradeSession("sess-1", request);

        assertEquals("sess-1", out.getId());
        assertEquals("sess-1", handler.args[0]);
        assertSame(request, handler.args[1]);
        assertTrue(((Map<?, ?>) handler.args[2]).isEmpty());
    }

    @Test
    public void upgradeSessionApiKeepsPostContract() throws Exception {
        Method method = ArkApi.class.getMethod(
            "upgradeSession",
            String.class,
            CreateSessionUpgradeRequest.class,
            Map.class
        );

        assertEquals("/api/v3/sessions/{sessionId}/upgrades", method.getAnnotation(POST.class).value());
        assertEquals("sessionId", findAnnotation(method.getParameterAnnotations()[0], Path.class).value());
    }

    @Test
    public void upgradeSessionPreservesExplicitEmptyArrays() throws Exception {
        String json = ArkService.defaultObjectMapper().writeValueAsString(request());

        assertTrue(json.contains("\"tools\":[]"));
        assertTrue(json.contains("\"vault_ids\":[]"));
        assertFalse(json.contains("initial_events"));
    }

    private static CreateSessionUpgradeRequest request() {
        AgentWithUpgrades agent = AgentWithUpgrades.builder()
            .type(AgentWithUpgrades.TypeEnum.AGENT_WITH_UPGRADES)
            .id("agent-1")
            .tools(Collections.emptyList())
            .build();
        return CreateSessionUpgradeRequest.builder()
            .agent(agent)
            .vaultIds(Collections.emptyList())
            .build();
    }

    private static ArkApi api(CapturingArkApi handler) {
        return (ArkApi) Proxy.newProxyInstance(
            ArkApi.class.getClassLoader(),
            new Class<?>[] {ArkApi.class},
            handler
        );
    }

    private static <T extends Annotation> T findAnnotation(Annotation[] annotations, Class<T> type) {
        for (Annotation annotation : annotations) {
            if (type.isInstance(annotation)) {
                return type.cast(annotation);
            }
        }
        throw new AssertionError("missing annotation " + type.getSimpleName());
    }

    private static final class CapturingArkApi implements InvocationHandler {
        private Object[] args;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            if (!"upgradeSession".equals(method.getName())) {
                throw new AssertionError("unexpected API call: " + method.getName());
            }
            this.args = args;
            return Single.just(new Session().id("sess-1"));
        }

        private Object objectMethod(Object proxy, Method method, Object[] args) {
            if ("toString".equals(method.getName())) {
                return "capturing ArkApi proxy";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            throw new AssertionError("unexpected Object method: " + method.getName());
        }
    }
}
