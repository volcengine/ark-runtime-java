// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.volcengine.ark.runtime.models.skill.SkillVersion;
import io.reactivex.Single;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.Buffer;
import org.junit.Test;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

public class ArkServiceSkillTest {
    @Test
    public void createSkillVersionPassesMultipartPartsAndReturnsSkillVersion() throws Exception {
        CapturingArkApi handler = new CapturingArkApi();
        ArkService service = new ArkService(api(handler));
        MultipartBody.Part files = filePart();

        SkillVersion out = service.createSkillVersion("skill-1", files, "Readiness Skill v2");

        assertEquals("skillver-2", out.getId());
        assertEquals(SkillVersion.TypeEnum.SKILL_VERSION, out.getType());
        assertEquals("skill-1", out.getSkillId());
        assertEquals("2", out.getVersion());
        assertEquals("Readiness Skill v2", out.getDisplayTitle());
        assertEquals("ok", out.getDescription());
        assertEquals("2026-09-14T10:11:12Z", out.getCreatedAt());

        assertEquals("skill-1", handler.args[0]);
        assertSame(files, handler.args[1]);
        assertEquals("Readiness Skill v2", requestBodyToString((RequestBody) handler.args[2]));
        assertTrue(((Map<?, ?>) handler.args[3]).isEmpty());
    }

    @Test
    public void createSkillVersionOmitsDisplayTitleWhenNull() {
        CapturingArkApi handler = new CapturingArkApi();
        ArkService service = new ArkService(api(handler));

        service.createSkillVersion("skill-1", filePart(), null);

        assertNull(handler.args[2]);
    }

    @Test
    public void createSkillVersionRejectsMissingSkillId() {
        ArkService service = new ArkService(api(new CapturingArkApi()));

        try {
            service.createSkillVersion(null, filePart(), "title");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException err) {
            assertEquals("skillId is required", err.getMessage());
        }

        try {
            service.createSkillVersion("", filePart(), "title");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException err) {
            assertEquals("skillId is required", err.getMessage());
        }
    }

    @Test
    public void createSkillVersionApiKeepsMultipartContract() throws Exception {
        Method method = ArkApi.class.getMethod(
            "createSkillVersion",
            String.class,
            MultipartBody.Part.class,
            RequestBody.class,
            Map.class
        );

        assertEquals("/api/v3/skills/{skillId}/versions", method.getAnnotation(POST.class).value());

        Annotation[][] annotations = method.getParameterAnnotations();
        assertEquals("skillId", findAnnotation(annotations[0], Path.class).value());
        assertEquals("display_title", findAnnotation(annotations[2], Part.class).value());
    }

    private static ArkApi api(CapturingArkApi handler) {
        return (ArkApi) Proxy.newProxyInstance(
            ArkApi.class.getClassLoader(),
            new Class<?>[] {ArkApi.class},
            handler
        );
    }

    private static MultipartBody.Part filePart() {
        RequestBody body = RequestBody.create(
            MediaType.parse("application/zip"),
            "zip-bytes".getBytes(StandardCharsets.UTF_8)
        );
        return MultipartBody.Part.createFormData("files", "skill.zip", body);
    }

    private static String requestBodyToString(RequestBody body) throws IOException {
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        return buffer.readUtf8();
    }

    private static <T extends Annotation> T findAnnotation(Annotation[] annotations, Class<T> type) {
        for (Annotation annotation : annotations) {
            if (type.isInstance(annotation)) {
                return type.cast(annotation);
            }
        }
        throw new AssertionError("missing annotation " + type.getSimpleName());
    }

    private static SkillVersion skillVersion() {
        return SkillVersion.builder()
            .id("skillver-2")
            .type(SkillVersion.TypeEnum.SKILL_VERSION)
            .skillId("skill-1")
            .version("2")
            .displayTitle("Readiness Skill v2")
            .description("ok")
            .createdAt("2026-09-14T10:11:12Z")
            .build();
    }

    private static final class CapturingArkApi implements InvocationHandler {
        private Object[] args;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, args);
            }
            if (!"createSkillVersion".equals(method.getName())) {
                throw new AssertionError("unexpected API call: " + method.getName());
            }
            this.args = args;
            return Single.just(skillVersion());
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
