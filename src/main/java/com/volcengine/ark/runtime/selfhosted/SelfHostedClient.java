// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.Const;
import com.volcengine.ark.runtime.interceptor.RetryInterceptor;
import com.volcengine.ark.runtime.models.environment.EnvironmentWorkPoll200Response;
import com.volcengine.ark.runtime.models.environment.HeartbeatWorkResponse;
import com.volcengine.ark.runtime.models.environment.StopWorkBody;
import com.volcengine.ark.runtime.models.environment.WorkItem;
import com.volcengine.ark.runtime.models.session.ManagedAgentsEventParams;
import com.volcengine.ark.runtime.models.session.SendSessionEventsRequest;
import com.volcengine.ark.runtime.models.skill.Skill;
import com.volcengine.ark.runtime.service.ArkApi;
import com.volcengine.ark.runtime.service.ArkBaseService;
import com.volcengine.ark.runtime.service.ArkService;
import io.reactivex.Single;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.HttpException;
import retrofit2.Response;
import retrofit2.Retrofit;

public class SelfHostedClient {
    public static final String DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    private static final String DEFAULT_SKILL_HUB_BASE_URL = "https://skills.volces.com/v1/skills";
    private static final String SKILL_TYPE_SKILL_HUB = "skill_hub";
    private static final int MAX_SKILL_HUB_METADATA_BYTES = 1 << 20;
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 15L;
    private static final long LIFECYCLE_TIMEOUT_SECONDS = 10L;

    private final ArkApi api;
    private final ArkApi streamApi;
    private final ArkApi heartbeatApi;
    private final ArkApi lifecycleApi;
    private final OkHttpClient httpClient;
    private final OkHttpClient externalHttpClient;
    private final ObjectMapper mapper;
    private final String skillHubBaseUrl;

    public SelfHostedClient(String apiKey) {
        this(new Builder().apiKey(apiKey));
    }

    private SelfHostedClient(Builder builder) {
        this.mapper = ArkService.defaultObjectMapper();
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : ArkService.defaultApiKeyClient(builder.apiKey, builder.timeout);
        OkHttpClient.Builder externalClientBuilder = this.httpClient.newBuilder();
        externalClientBuilder.interceptors().clear();
        externalClientBuilder.networkInterceptors().clear();
        this.externalHttpClient = externalClientBuilder.build();
        this.skillHubBaseUrl = trimTrailingSlash(builder.skillHubBaseUrl);
        Retrofit retrofit = ArkService.defaultRetrofit(this.httpClient, this.mapper, normalizeBaseUrl(builder.baseUrl), null);
        this.api = retrofit.create(ArkApi.class);
        OkHttpClient streamClient = this.httpClient.newBuilder()
                .callTimeout(0L, TimeUnit.MILLISECONDS)
                .build();
        Retrofit streamRetrofit = ArkService.defaultRetrofit(
                streamClient, this.mapper, normalizeBaseUrl(builder.baseUrl), null);
        this.streamApi = streamRetrofit.create(ArkApi.class);
        OkHttpClient.Builder heartbeatClientBuilder = this.httpClient.newBuilder()
                .retryOnConnectionFailure(false)
                .connectTimeout(HEARTBEAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(HEARTBEAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(HEARTBEAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(HEARTBEAT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        heartbeatClientBuilder.interceptors().removeIf(interceptor -> interceptor instanceof RetryInterceptor);
        Retrofit heartbeatRetrofit = ArkService.defaultRetrofit(
                heartbeatClientBuilder.build(), this.mapper, normalizeBaseUrl(builder.baseUrl), null);
        this.heartbeatApi = heartbeatRetrofit.create(ArkApi.class);
        OkHttpClient.Builder lifecycleClientBuilder = this.httpClient.newBuilder()
                .retryOnConnectionFailure(false)
                .connectTimeout(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        lifecycleClientBuilder.interceptors().removeIf(interceptor -> interceptor instanceof RetryInterceptor);
        Retrofit lifecycleRetrofit = ArkService.defaultRetrofit(
                lifecycleClientBuilder.build(), this.mapper, normalizeBaseUrl(builder.baseUrl), null);
        this.lifecycleApi = lifecycleRetrofit.create(ArkApi.class);
    }

    public WorkItem pollWork(String environmentId, String workerId, int blockMs, int reclaimOlderThanMs) {
        require(environmentId, "environmentId");
        Integer block = blockMs > 0 ? blockMs : null;
        Integer reclaim = reclaimOlderThanMs > 0 ? reclaimOlderThanMs : null;
        EnvironmentWorkPoll200Response response =
                execute(api.pollEnvironmentWork(environmentId, block, reclaim, workerHeader(workerId)));
        if (response == null || response.getId() == null || response.getId().isEmpty()) {
            return null;
        }
        return mapper.convertValue(response, WorkItem.class);
    }

    public void ackWork(String environmentId, String workId, String workerId) {
        require(environmentId, "environmentId");
        require(workId, "workId");
        execute(lifecycleApi.ackEnvironmentWork(environmentId, workId, workerHeader(workerId)));
    }

    public HeartbeatWorkResponse heartbeatWork(
            String environmentId, String workId, String expectedLastHeartbeat, int desiredTTLSeconds) {
        require(environmentId, "environmentId");
        require(workId, "workId");
        String expected = expectedLastHeartbeat == null || expectedLastHeartbeat.isEmpty()
                ? SelfHostedConstants.EXPECTED_LAST_HEARTBEAT_NO_HEARTBEAT
                : expectedLastHeartbeat;
        Integer ttl = desiredTTLSeconds > 0 ? desiredTTLSeconds : null;
        return execute(heartbeatApi.heartbeatEnvironmentWork(
                environmentId, workId, expected, ttl, Collections.<String, String>emptyMap()));
    }

    public void stopWork(String environmentId, String workId, boolean force) {
        require(environmentId, "environmentId");
        require(workId, "workId");
        StopWorkBody body = new StopWorkBody();
        if (force) {
            body.setForce(Boolean.TRUE);
        }
        execute(lifecycleApi.stopEnvironmentWork(
                environmentId, workId, body, Collections.<String, String>emptyMap()));
    }

    public SessionSnapshot getSession(String sessionId) {
        require(sessionId, "sessionId");
        return SessionSnapshot.fromMap(toMap(execute(
                api.getSession(sessionId, Collections.<String, String>emptyMap()))));
    }

    public ListEventsResponse listEvents(
            String sessionId, String createdAtGt, String page, int limit, String order, List<String> types) {
        require(sessionId, "sessionId");
        Integer effectiveLimit = limit > 0 ? limit : null;
        String effectiveOrder = order == null || order.isEmpty() ? null : order;
        String effectivePage = page == null || page.isEmpty() ? null : page;
        String effectiveCreatedAtGt = createdAtGt == null || createdAtGt.isEmpty() ? null : createdAtGt;
        com.volcengine.ark.runtime.models.session.ListSessionEventsResponse response = execute(api.listSessionEvents(
                sessionId,
                effectiveCreatedAtGt,
                null,
                null,
                null,
                effectiveLimit,
                effectiveOrder,
                effectivePage,
                types == null || types.isEmpty() ? null : types,
                Collections.<String, String>emptyMap()));
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("data", response == null ? null : response.getData());
        raw.put("next_page", response == null ? null : response.getNextPage());
        return ListEventsResponse.fromMap(raw);
    }

    public void sendEvent(String sessionId, Event event) {
        require(sessionId, "sessionId");
        if (event == null) {
            throw new IllegalArgumentException("event is required");
        }
        ManagedAgentsEventParams eventParams =
                mapper.convertValue(event.toMap(), ManagedAgentsEventParams.class);
        SendSessionEventsRequest body =
                new SendSessionEventsRequest().events(Collections.singletonList(eventParams));
        execute(api.sendSessionEvents(sessionId, body, Collections.<String, String>emptyMap()));
    }

    public SkillRef resolveSkill(SkillRef ref) {
        if (ref == null) {
            throw new IllegalArgumentException("skill is required");
        }
        String skillId = ref.idValue().trim();
        require(skillId, "skillId");
        Skill metadata = execute(api.getSkill(skillId, Collections.<String, String>emptyMap()));
        if (metadata == null || metadata.getName() == null || metadata.getName().trim().isEmpty()) {
            throw new IllegalStateException("skill name is empty: " + skillId);
        }
        return ref.withResolvedMetadata(metadata.getName().trim(), metadata.getLatestVersion());
    }

    public Call<ResponseBody> streamEvents(String sessionId) {
        require(sessionId, "sessionId");
        return streamApi.streamSessionEvents(sessionId, Collections.<String, String>emptyMap());
    }

    public EventStream openEventStream(String sessionId) {
        require(sessionId, "sessionId");
        Call<ResponseBody> call = streamApi.streamSessionEvents(sessionId, Collections.<String, String>emptyMap());
        try {
            return new EventStream(call, call.execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SkillContent openSkill(String sessionId, SkillRef skill) {
        if (skill == null) {
            throw new IllegalArgumentException("skill is required");
        }
        if (skill.getDownloadUrl() != null && !skill.getDownloadUrl().isEmpty()) {
            return openSignedSkillURL(skill.getDownloadUrl());
        }
        String skillId = skill.idValue();
        require(skillId, "skillId");
        require(skill.getVersion(), "version");
        if (SKILL_TYPE_SKILL_HUB.equalsIgnoreCase(skill.getType().trim())) {
            String slug = lookupSkillHubSlug(skillId);
            return openSignedSkillURL(skillHubDownloadUrl(slug, skill.getVersion()));
        }
        try {
            Response<ResponseBody> response = api.openSkillContent(
                    skillId,
                    skill.getVersion(),
                    Collections.<String, String>emptyMap()).execute();
            return toSkillContent(response, skillId + "-" + skill.getVersion());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SkillContent openSignedSkillURL(String downloadUrl) {
        Request request = new Request.Builder().url(downloadUrl).get().build();
        okhttp3.Response response = null;
        boolean bodyTransferred = false;
        try {
            response = externalHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                String message = response.body() == null ? response.message() : response.body().string();
                throw new WorkerAPIException(response.code(), message, header(response.headers(), Const.SERVER_REQUEST_HEADER));
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new WorkerAPIException(response.code(), "empty skill content", header(response.headers(), Const.SERVER_REQUEST_HEADER));
            }
            String fileName = URI.create(downloadUrl).getPath();
            int slash = fileName.lastIndexOf('/');
            if (slash >= 0) {
                fileName = fileName.substring(slash + 1);
            }
            SkillContent content = new SkillContent(
                    body.byteStream(),
                    body.contentLength(),
                    fileName,
                    body.contentType() == null ? "" : body.contentType().toString());
            bodyTransferred = true;
            return content;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (response != null && !bodyTransferred) {
                response.close();
            }
        }
    }

    private String lookupSkillHubSlug(String skillId) {
        HttpUrl base = requireHttpUrl(skillHubBaseUrl, "skillHubBaseUrl");
        HttpUrl metadataUrl = base.newBuilder().addQueryParameter("skillIds", skillId).build();
        Request request = new Request.Builder().url(metadataUrl).get().build();
        try (okhttp3.Response response = externalHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw externalHTTPError(response);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new WorkerAPIException(response.code(), "empty skill hub metadata", skillRequestId(response));
            }
            byte[] metadata = readBounded(body.byteStream(), MAX_SKILL_HUB_METADATA_BYTES);
            Map<String, Object> payload = mapper.readValue(
                    metadata, new TypeReference<Map<String, Object>>() {});
            Object skills = payload.get("Skills");
            if (skills instanceof List) {
                for (Object value : (List<?>) skills) {
                    if (!(value instanceof Map)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> candidate = (Map<String, Object>) value;
                    if (!skillId.equals(stringValue(candidate.get("Id")).trim())) {
                        continue;
                    }
                    String slug = trimSlashes(stringValue(candidate.get("Slug")));
                    if (slug.isEmpty()) {
                        throw new WorkerAPIException(500, "skill hub slug is empty: " + skillId, "");
                    }
                    return slug;
                }
            }
            throw new WorkerAPIException(404, "skill hub skill not found: " + skillId, "");
        } catch (IOException error) {
            throw new RuntimeException("lookup skill hub metadata", error);
        }
    }

    private String skillHubDownloadUrl(String slug, String version) {
        HttpUrl.Builder builder = requireHttpUrl(skillHubBaseUrl, "skillHubBaseUrl").newBuilder();
        builder.addPathSegment("download");
        for (String segment : slug.split("/")) {
            String value = segment.trim();
            if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
                throw new IllegalArgumentException("invalid skill hub slug: " + slug);
            }
            builder.addPathSegment(value);
        }
        return builder.addQueryParameter("version", version).build().toString();
    }

    private WorkerAPIException externalHTTPError(okhttp3.Response response) throws IOException {
        String message = response.body() == null ? response.message() : response.body().string();
        return new WorkerAPIException(response.code(), message, skillRequestId(response));
    }

    private static String skillRequestId(okhttp3.Response response) {
        String value = response.header("X-Skill-Request-Id");
        return value == null || value.isEmpty() ? header(response.headers(), Const.SERVER_REQUEST_HEADER) : value;
    }

    private static HttpUrl requireHttpUrl(String value, String name) {
        HttpUrl url = HttpUrl.parse(value);
        if (url == null) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return url;
    }

    private static String trimSlashes(String value) {
        String out = value == null ? "" : value.trim();
        while (out.startsWith("/")) {
            out = out.substring(1);
        }
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static byte[] readBounded(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 65536));
        byte[] buffer = new byte[65536];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > limit) {
                throw new IOException("skill hub metadata response is too large");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private SkillContent toSkillContent(Response<ResponseBody> response, String fallbackName) throws IOException {
        if (!response.isSuccessful()) {
            String message = response.errorBody() == null ? response.message() : response.errorBody().string();
            throw new WorkerAPIException(response.code(), message, requestId(response));
        }
        ResponseBody body = response.body();
        if (body == null) {
            throw new WorkerAPIException(response.code(), "empty skill content", requestId(response));
        }
        return new SkillContent(
                body.byteStream(),
                body.contentLength(),
                fallbackName,
                body.contentType() == null ? "" : body.contentType().toString());
    }

    private <T> T execute(Single<T> call) {
        try {
            return call.blockingGet();
        } catch (RuntimeException e) {
            throw toAPIException(e);
        }
    }

    private RuntimeException toAPIException(RuntimeException e) {
        Throwable cause = e instanceof HttpException ? e : e.getCause();
        if (cause instanceof HttpException) {
            HttpException http = (HttpException) cause;
            String message = http.message();
            try {
                if (http.response() != null && http.response().errorBody() != null) {
                    message = http.response().errorBody().string();
                }
            } catch (IOException ignored) {
            }
            String requestId = http.response() == null ? "" : requestId(http.response());
            return new WorkerAPIException(http.code(), message, requestId);
        }
        return e;
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        return mapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
    }

    private Map<String, String> workerHeader(String workerId) {
        if (workerId == null || workerId.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(SelfHostedConstants.WORKER_ID_HEADER, workerId);
        return headers;
    }

    private static void require(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isEmpty() ? DEFAULT_BASE_URL : baseUrl;
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/api/v3")) {
            value = value.substring(0, value.length() - "/api/v3".length());
        }
        return value + "/";
    }

    private static String trimTrailingSlash(String value) {
        String out = value == null || value.isEmpty() ? DEFAULT_SKILL_HUB_BASE_URL : value;
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static String requestId(Response<?> response) {
        return response.headers().get(Const.SERVER_REQUEST_HEADER) == null
                ? ""
                : response.headers().get(Const.SERVER_REQUEST_HEADER);
    }

    private static String header(Headers headers, String name) {
        return headers.get(name) == null ? "" : headers.get(name);
    }

    public static class Builder {
        private String apiKey = System.getenv("ARK_API_KEY");
        private String baseUrl = DEFAULT_BASE_URL;
        private String skillHubBaseUrl = DEFAULT_SKILL_HUB_BASE_URL;
        private Duration timeout = ArkBaseService.DEFAULT_TIMEOUT;
        private OkHttpClient httpClient;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder skillHubBaseUrl(String skillHubBaseUrl) {
            this.skillHubBaseUrl = skillHubBaseUrl;
            return this;
        }

        public Builder httpClient(OkHttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public SelfHostedClient build() {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalArgumentException("apiKey is required");
            }
            return new SelfHostedClient(this);
        }
    }
}
