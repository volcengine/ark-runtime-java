// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.service;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.volcengine.ark.runtime.Const;
import com.volcengine.ark.runtime.exception.ArkAPIError;
import com.volcengine.ark.runtime.exception.ArkException;
import com.volcengine.ark.runtime.exception.ArkHttpException;
import com.volcengine.ark.runtime.interceptor.*;
import com.volcengine.ark.runtime.models.agent.Agent;
import com.volcengine.ark.runtime.models.agent.CreateAgentRequest;
import com.volcengine.ark.runtime.models.agent.DeleteAgentResponse;
import com.volcengine.ark.runtime.models.agent.ListAgentsResponse;
import com.volcengine.ark.runtime.models.agent.UpdateAgentRequest;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.models.chat.ChatCompletionResponse;
import com.volcengine.ark.runtime.models.chat.ChatCompletionStreamResponse;
import com.volcengine.ark.runtime.models.content_generation.ContentGenerationTask;
import com.volcengine.ark.runtime.models.content_generation.CreateContentGenerationTaskRequest;
import com.volcengine.ark.runtime.models.content_generation.CreateContentGenerationTaskResponse;
import com.volcengine.ark.runtime.models.content_generation.ListContentGenerationTasksRequest;
import com.volcengine.ark.runtime.models.content_generation.ListContentGenerationTasksResponse;
import com.volcengine.ark.runtime.models.embedding.EmbeddingRequest;
import com.volcengine.ark.runtime.models.embedding.EmbeddingResponse;
import com.volcengine.ark.runtime.models.environment.CreateEnvironmentRequest;
import com.volcengine.ark.runtime.models.environment.DeleteEnvironmentResponse;
import com.volcengine.ark.runtime.models.environment.Environment;
import com.volcengine.ark.runtime.models.environment.ListEnvironmentsResponse;
import com.volcengine.ark.runtime.models.environment.UpdateEnvironmentRequest;
import com.volcengine.ark.runtime.models.file.FileCreateRequest;
import com.volcengine.ark.runtime.models.file.FileDeleted;
import com.volcengine.ark.runtime.models.file.FileListRequest;
import com.volcengine.ark.runtime.models.file.FileListResponse;
import com.volcengine.ark.runtime.models.file.FileObject;
import com.volcengine.ark.runtime.models.images.CreateImageGenerationRequest;
import com.volcengine.ark.runtime.models.images.ImageGenerationResponse;
import com.volcengine.ark.runtime.models.images.ImageGenerationStreamEvent;
import com.volcengine.ark.runtime.models.memory.CreateMemoryRequest;
import com.volcengine.ark.runtime.models.memory.CreateMemoryStoreRequest;
import com.volcengine.ark.runtime.models.memory.DeleteMemoryResponse;
import com.volcengine.ark.runtime.models.memory.DeleteMemoryStoreResponse;
import com.volcengine.ark.runtime.models.memory.ListMemoriesResponse;
import com.volcengine.ark.runtime.models.memory.ListMemoryStoresResponse;
import com.volcengine.ark.runtime.models.memory.Memory;
import com.volcengine.ark.runtime.models.memory.MemoryStore;
import com.volcengine.ark.runtime.models.memory.UpdateMemoryRequest;
import com.volcengine.ark.runtime.models.memory.UpdateMemoryStoreRequest;
import com.volcengine.ark.runtime.models.multimodal_embedding.MultiModalEmbeddingRequest;
import com.volcengine.ark.runtime.models.multimodal_embedding.MultiModalEmbeddingResponse;
import com.volcengine.ark.runtime.models.responses.DeleteResponseRequest;
import com.volcengine.ark.runtime.models.responses.DeleteResponseResponse;
import com.volcengine.ark.runtime.models.responses.GetResponseRequest;
import com.volcengine.ark.runtime.models.responses.ListInputItemsRequest;
import com.volcengine.ark.runtime.models.responses.ListInputItemsResponse;
import com.volcengine.ark.runtime.models.responses.Response;
import com.volcengine.ark.runtime.models.responses.ResponseIncludable;
import com.volcengine.ark.runtime.models.responses.ResponseStreamEvent;
import com.volcengine.ark.runtime.models.responses.ResponsesRequest;
import com.volcengine.ark.runtime.models.session.CreateSessionRequest;
import com.volcengine.ark.runtime.models.session.CreateSessionResourceRequest;
import com.volcengine.ark.runtime.models.session.DeleteSessionResponse;
import com.volcengine.ark.runtime.models.session.ListSessionEventsResponse;
import com.volcengine.ark.runtime.models.session.ListSessionResourcesResponse;
import com.volcengine.ark.runtime.models.session.ListSessionThreadsResponse;
import com.volcengine.ark.runtime.models.session.ListSessionsResponse;
import com.volcengine.ark.runtime.models.session.SendSessionEventsRequest;
import com.volcengine.ark.runtime.models.session.SendSessionEventsResponse;
import com.volcengine.ark.runtime.models.session.Session;
import com.volcengine.ark.runtime.models.session.SessionResource;
import com.volcengine.ark.runtime.models.session.SessionThread;
import com.volcengine.ark.runtime.models.session.UpdateSessionRequest;
import com.volcengine.ark.runtime.models.skill.Skill;
import com.volcengine.ark.runtime.models.tokenization.TokenizationRequest;
import com.volcengine.ark.runtime.models.tokenization.TokenizationResponse;
import com.volcengine.ark.runtime.models.vault.CreateCredentialRequest;
import com.volcengine.ark.runtime.models.vault.CreateVaultRequest;
import com.volcengine.ark.runtime.models.vault.Credential;
import com.volcengine.ark.runtime.models.vault.CredentialValidation;
import com.volcengine.ark.runtime.models.vault.DeleteCredentialResponse;
import com.volcengine.ark.runtime.models.vault.DeleteVaultResponse;
import com.volcengine.ark.runtime.models.vault.ListCredentialsResponse;
import com.volcengine.ark.runtime.models.vault.ListVaultsResponse;
import com.volcengine.ark.runtime.models.vault.UpdateCredentialRequest;
import com.volcengine.ark.runtime.models.vault.UpdateVaultRequest;
import com.volcengine.ark.runtime.models.vault.Vault;
import com.volcengine.ark.runtime.utils.ResponseBodyCallback;
import com.volcengine.ark.runtime.utils.SSE;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import java.io.IOException;
import java.net.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import retrofit2.Call;
import retrofit2.HttpException;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class ArkService extends ArkBaseService implements ArkBaseServiceImpl {
    private static final ObjectMapper mapper = defaultObjectMapper();
    private final ArkApi api;
    private final ExecutorService executorService;

    /**
     * Default dispatcher cap for batch fan-out. Mirrors the value used by the
     * legacy Go SDK so a small parallel client can issue many in-flight
     * /api/v3/batch/* requests without queueing inside OkHttp.
     */
    public static final int DEFAULT_BATCH_MAX_REQUESTS = 3000;

    public ArkService(final String apiKey) {
        this(apiKey, DEFAULT_TIMEOUT);
    }

    public ArkService(final String apiKey, final Duration timeout) {
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient client = defaultApiKeyClient(apiKey, timeout);
        Retrofit retrofit = defaultRetrofit(client, mapper, BASE_URL, null);

        this.api = retrofit.create(ArkApi.class);
        this.executorService = client.dispatcher().executorService();
    }

    public ArkService(final String ak, final String sk) {
        this(ak, sk, DEFAULT_TIMEOUT);
    }

    public ArkService(final String ak, final String sk, final Duration timeout) {
        ObjectMapper mapper = defaultObjectMapper();
        OkHttpClient client = defaultResourceStsClient(ak, sk, timeout, BASE_REGION);
        Retrofit retrofit = defaultRetrofit(client, mapper, BASE_URL, null);

        this.api = retrofit.create(ArkApi.class);
        this.executorService = client.dispatcher().executorService();
    }

    public ArkService(final ArkApi api) {
        this.api = api;
        this.executorService = null;
    }

    public ArkService(final ArkApi api, final ExecutorService executorService) {
        this.api = api;
        this.executorService = executorService;
    }

    public static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        return mapper;
    }

    public static OkHttpClient defaultApiKeyClient(String apiKey, Duration timeout) {
        return new OkHttpClient.Builder()
                .addInterceptor(new AuthenticationInterceptor(apiKey))
                .addInterceptor(new RequestIdInterceptor())
                .addInterceptor(new RetryInterceptor(DEFAULT_RETRY_TIMES))
                .addInterceptor(new BatchInterceptor())
                .dispatcher(defaultBatchDispatcher())
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public static OkHttpClient defaultResourceStsClient(String ak, String sk, Duration timeout, String region) {
        return new OkHttpClient.Builder()
                .addInterceptor(new ArkResourceStsAuthenticationInterceptor(ak, sk, region))
                .addInterceptor(new RequestIdInterceptor())
                .addInterceptor(new RetryInterceptor(DEFAULT_RETRY_TIMES))
                .addInterceptor(new BatchInterceptor())
                .dispatcher(defaultBatchDispatcher())
                .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
                .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Dispatcher tuned for batch fan-out: lifts the default OkHttp caps so
     * large parallel batches don't get serialized inside the client.
     */
    public static Dispatcher defaultBatchDispatcher() {
        Dispatcher d = new Dispatcher();
        d.setMaxRequests(DEFAULT_BATCH_MAX_REQUESTS);
        d.setMaxRequestsPerHost(DEFAULT_BATCH_MAX_REQUESTS);
        return d;
    }

    public static Retrofit defaultRetrofit(OkHttpClient client, ObjectMapper mapper, String baseUrl, Executor callbackExecutor) {
        Retrofit.Builder builder = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create());

        if (callbackExecutor != null) {
            // to avoid NPE
            builder.callbackExecutor(callbackExecutor);
        }

        return builder.build();
    }

    public static <T> T execute(Single<T> apiCall) {
        try {
            T resp = apiCall.blockingGet();
            return resp;
        } catch (HttpException e) {
            String requestId = "";
            try {
                Headers headers = e.response().raw().request().headers();
                requestId = headers.get(Const.CLIENT_REQUEST_HEADER);
            } catch (Exception ignored) {
            }

            try {
                if (e.response() == null || e.response().errorBody() == null) {
                    throw e;
                }
                String errorBody = e.response().errorBody().string();

                ArkAPIError error = mapper.readValue(errorBody, ArkAPIError.class);
                throw new ArkHttpException(error, e, e.code(), requestId);
            } catch (IOException ex) {
                throw e;
            }
        }
    }

    public static void execute(Completable apiCall) {
        try {
            apiCall.blockingAwait();
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpException) {
                HttpException he = (HttpException) cause;
                String requestId = "";
                try {
                    Headers headers = he.response().raw().request().headers();
                    requestId = headers.get(Const.CLIENT_REQUEST_HEADER);
                } catch (Exception ignored) {
                }
                try {
                    if (he.response() == null || he.response().errorBody() == null) {
                        throw he;
                    }
                    String errorBody = he.response().errorBody().string();
                    ArkAPIError error = mapper.readValue(errorBody, ArkAPIError.class);
                    throw new ArkHttpException(error, he, he.code(), requestId);
                } catch (IOException ioe) {
                    throw new RuntimeException(he);
                }
            }
            throw e;
        }
    }

    public static Flowable<SSE> stream(Call<ResponseBody> apiCall) {
        return stream(apiCall, false);
    }

    public static Flowable<SSE> stream(Call<ResponseBody> apiCall, boolean emitDone) {
        return Flowable.create(emitter -> apiCall.enqueue(new ResponseBodyCallback(emitter, emitDone)), BackpressureStrategy.BUFFER);
    }

    public static <T> Flowable<T> stream(Call<ResponseBody> apiCall, Class<T> cl) {
        return stream(apiCall).map(sse -> mapper.readValue(sse.getData(), cl));
    }

    public ChatCompletionResponse createChatCompletion(ChatCompletionRequest request) {
        return createChatCompletion(request, new HashMap<>());
    }

    public ChatCompletionResponse createChatCompletion(ChatCompletionRequest request, Map<String, String> customHeaders) {
        return execute(api.createChatCompletion(request, request.getModel(), customHeaders));
    }

    public Flowable<ChatCompletionStreamResponse> streamChatCompletion(ChatCompletionRequest request) {
        return streamChatCompletion(request, new HashMap<>());
    }

    public Flowable<ChatCompletionStreamResponse> streamChatCompletion(ChatCompletionRequest request, Map<String, String> customHeaders) {
        request.setStream(true);
        return stream(api.streamChatCompletion(request, request.getModel(), customHeaders), ChatCompletionStreamResponse.class);
    }

    public ChatCompletionResponse createBatchChatCompletion(ChatCompletionRequest request) {
        return createBatchChatCompletion(request, new HashMap<>());
    }

    public ChatCompletionResponse createBatchChatCompletion(ChatCompletionRequest request, Map<String, String> customHeaders) {
        if (request.getStream() != null && request.getStream()) {
            throw new ArkException("streaming is not supported for batch chat completions");
        }
        return execute(api.createBatchChatCompletion(request, request.getModel(), customHeaders));
    }

    public EmbeddingResponse createEmbeddings(EmbeddingRequest request) {
        return createEmbeddings(request, new HashMap<>());
    }

    public EmbeddingResponse createEmbeddings(EmbeddingRequest request, Map<String, String> customHeaders) {
        return execute(api.createEmbeddings(request, request.getModel(), customHeaders));
    }

    public EmbeddingResponse createBatchEmbeddings(EmbeddingRequest request) {
        return createBatchEmbeddings(request, new HashMap<>());
    }

    public EmbeddingResponse createBatchEmbeddings(EmbeddingRequest request, Map<String, String> customHeaders) {
        return execute(api.createBatchEmbeddings(request, request.getModel(), customHeaders));
    }

    public MultiModalEmbeddingResponse createMultiModalEmbeddings(MultiModalEmbeddingRequest request) {
        return execute(api.createMultiModalEmbeddings(request, request.getModel(), new HashMap<>()));
    }

    public MultiModalEmbeddingResponse createMultiModalEmbeddings(MultiModalEmbeddingRequest request, Map<String, String> customHeaders) {
        return execute(api.createMultiModalEmbeddings(request, request.getModel(), customHeaders));
    }

    public MultiModalEmbeddingResponse createBatchMultiModalEmbeddings(MultiModalEmbeddingRequest request) {
        return createBatchMultiModalEmbeddings(request, new HashMap<>());
    }

    public MultiModalEmbeddingResponse createBatchMultiModalEmbeddings(MultiModalEmbeddingRequest request, Map<String, String> customHeaders) {
        return execute(api.createBatchMultiModalEmbeddings(request, request.getModel(), customHeaders));
    }

    public TokenizationResponse createTokenization(TokenizationRequest request) {
        return execute(api.createTokenization(request, request.getModel(), new HashMap<>()));
    }

    public TokenizationResponse createTokenization(TokenizationRequest request, Map<String, String> customHeaders) {
        return execute(api.createTokenization(request, request.getModel(), customHeaders));
    }


    @Override
    public ImageGenerationResponse generateImages(CreateImageGenerationRequest request) {
        return execute(api.generateImages(request, request.getModel(), new HashMap<>()));
    }

    public ImageGenerationResponse generateImages(CreateImageGenerationRequest request, Map<String, String> customHeaders) {
        return execute(api.generateImages(request, request.getModel(), customHeaders));
    }

    @Override
    public Flowable<ImageGenerationStreamEvent> streamGenerateImages(CreateImageGenerationRequest request) {
        return streamGenerateImages(request, new HashMap<>());
    }

    public Flowable<ImageGenerationStreamEvent> streamGenerateImages(CreateImageGenerationRequest request, Map<String, String> customHeaders) {
        request.setStream(true);
        return stream(api.streamGenerateImages(request, request.getModel(), customHeaders), ImageGenerationStreamEvent.class);
    }

    @Override
    public CreateContentGenerationTaskResponse createContentGenerationTask(CreateContentGenerationTaskRequest request) {
        return execute(api.createContentGenerationTask(request, request.getModel(), new HashMap<>()));
    }

    public CreateContentGenerationTaskResponse createContentGenerationTask(CreateContentGenerationTaskRequest request, Map<String, String> customHeaders) {
        return execute(api.createContentGenerationTask(request, request.getModel(), customHeaders));
    }

    @Override
    public ContentGenerationTask getContentGenerationTask(String taskId) {
        return execute(api.getContentGenerationTask(taskId, new HashMap<>()));
    }

    public ContentGenerationTask getContentGenerationTask(String taskId, Map<String, String> customHeaders) {
        return execute(api.getContentGenerationTask(taskId, customHeaders));
    }

    @Override
    public ListContentGenerationTasksResponse listContentGenerationTasks(ListContentGenerationTasksRequest request) {
        return listContentGenerationTasks(request, new HashMap<>());
    }

    public ListContentGenerationTasksResponse listContentGenerationTasks(
            ListContentGenerationTasksRequest request,
            Map<String, String> customHeaders
    ) {
        return execute(api.listContentGenerationTasks(
                request.getPageNum(),
                request.getPageSize(),
                request.getFilterStatus() == null ? null : request.getFilterStatus().getValue(),
                request.getFilterModel(),
                request.getFilterServiceTier(),
                request.getFilterTaskIds(),
                customHeaders
        ));
    }

    @Override
    public void deleteContentGenerationTask(String taskId) {
        execute(api.deleteContentGenerationTask(taskId, new HashMap<>()));
    }

    public void deleteContentGenerationTask(String taskId, Map<String, String> customHeaders) {
        execute(api.deleteContentGenerationTask(taskId, customHeaders));
    }


    @Override
    public Response createResponse(ResponsesRequest request) {
        request.setStream(false);
        return execute(api.createResponse(request, request.getModel(), new HashMap<>()));
    }

    public Response createResponse(ResponsesRequest request, Map<String, String> customHeaders) {
        request.setStream(false);
        return execute(api.createResponse(request, request.getModel(), customHeaders));
    }

    @Override
    public Flowable<ResponseStreamEvent> streamResponse(ResponsesRequest request) {
        request.setStream(true);
        return stream(api.streamResponse(request, request.getModel(), new HashMap<>()), ResponseStreamEvent.class);
    }

    public Flowable<ResponseStreamEvent> streamResponse(ResponsesRequest request, Map<String, String> customHeaders) {
        request.setStream(true);
        return stream(api.streamResponse(request, request.getModel(), customHeaders), ResponseStreamEvent.class);
    }

    @Override
    public Response getResponse(GetResponseRequest request) {
        return execute(api.getResponse(request.getResponseId(), new HashMap<>()));
    }

    @Override
    public DeleteResponseResponse deleteResponse(DeleteResponseRequest request) {
        return execute(api.deleteResponse(request.getResponseId(), new HashMap<>()));
    }

    @Override
    public ListInputItemsResponse listResponseInputItems(ListInputItemsRequest request) {
        List<String> includeStrings = null;
        if (request.getInclude() != null) {
            includeStrings = new ArrayList<>();
            for (ResponseIncludable ri : request.getInclude()) {
                includeStrings.add(ri.getValue());
            }
        }
        return execute(api.listResponseInputItems(
                request.getResponseId(),
                request.getAfter(),
                request.getBefore(),
                request.getLimit(),
                request.getOrder() == null ? null : request.getOrder().getValue(),
                includeStrings,
                new HashMap<>()
        ));
    }

    @Override
    public FileObject uploadFile(FileCreateRequest request, java.io.File file) {
        Map<String, String> headers = new HashMap<>();

        okhttp3.RequestBody fileBody = okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/octet-stream"), file);
        okhttp3.MultipartBody.Part filePart = okhttp3.MultipartBody.Part.createFormData("file", file.getName(), fileBody);

        okhttp3.RequestBody purposeBody = okhttp3.RequestBody.create(
            okhttp3.MediaType.parse("text/plain"), request.getPurpose().getValue());
        okhttp3.RequestBody expireAtBody = request.getExpireAt() == null ? null
            : okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), String.valueOf(request.getExpireAt()));
        okhttp3.RequestBody urlBody = request.getUrl() == null ? null
            : okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), request.getUrl());
        okhttp3.RequestBody fpsBody = (request.getPreprocessConfigs() != null
                && request.getPreprocessConfigs().getVideo() != null
                && request.getPreprocessConfigs().getVideo().getFps() != null)
            ? okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"),
                                         String.valueOf(request.getPreprocessConfigs().getVideo().getFps()))
            : null;
        okhttp3.RequestBody fpsModelBody = (request.getPreprocessConfigs() != null
                && request.getPreprocessConfigs().getVideo() != null
                && request.getPreprocessConfigs().getVideo().getModel() != null)
            ? okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"),
                                         request.getPreprocessConfigs().getVideo().getModel())
            : null;
        okhttp3.RequestBody tosBucketBody = (request.getTos() != null && request.getTos().getBucket() != null)
            ? okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), request.getTos().getBucket())
            : null;
        okhttp3.RequestBody tosPrefixBody = (request.getTos() != null && request.getTos().getPrefix() != null)
            ? okhttp3.RequestBody.create(okhttp3.MediaType.parse("text/plain"), request.getTos().getPrefix())
            : null;

        return execute(api.uploadFile(filePart, purposeBody, expireAtBody, urlBody,
                                      fpsBody, fpsModelBody, tosBucketBody, tosPrefixBody, headers));
    }

    @Override
    public FileObject retrieveFile(String fileId) {
        return execute(api.retrieveFile(fileId, new HashMap<>()));
    }

    @Override
    public FileDeleted deleteFile(String fileId) {
        return execute(api.deleteFile(fileId, new HashMap<>()));
    }

    @Override
    public FileListResponse listFiles(FileListRequest request) {
        return execute(api.listFiles(
            request.getLimit() == null ? null : request.getLimit().intValue(),
            request.getAfter(),
            request.getPurpose() == null ? null : request.getPurpose().getValue(),
            request.getOrder() == null ? null : request.getOrder().getValue(),
            new HashMap<>()));
    }

    @Override
    public FileObject waitForFileProcessing(String fileId) {
        return waitForFileProcessing(fileId, java.time.Duration.ofSeconds(3), java.time.Duration.ofMinutes(10));
    }

    @Override
    public FileObject waitForFileProcessing(String fileId, java.time.Duration pollInterval, java.time.Duration maxWait) {
        long deadline = System.currentTimeMillis() + maxWait.toMillis();
        FileObject file = retrieveFile(fileId);
        while (!"active".equals(file.getStatus().getValue()) && !"failed".equals(file.getStatus().getValue())) {
            if (System.currentTimeMillis() > deadline) {
                throw new RuntimeException(
                    "Giving up on waiting for file " + fileId + " to finish processing after " + maxWait);
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            file = retrieveFile(fileId);
        }
        return file;
    }

    public void shutdownExecutor() {
        Objects.requireNonNull(this.executorService, "executorService must be set in order to shut down");
        this.executorService.shutdown();
    }

    public static ArkService.Builder builder() {
        return new ArkService.Builder();
    }

    /**
     * Builder pre-wired for the Volcengine cloud. Picks up
     * {@code ARK_API_KEY} / {@code VOLC_ACCESSKEY} / {@code VOLC_SECRETKEY}
     * from the environment (explicit setters override).
     */
    public static ArkService.Builder volc() {
        return forCloud(Cloud.VOLC);
    }

    /**
     * Builder pre-wired for the Byteplus cloud. Picks up
     * {@code ARK_API_KEY} / {@code BYTEPLUS_ACCESSKEY} /
     * {@code BYTEPLUS_SECRETKEY} from the environment (explicit setters
     * override).
     */
    public static ArkService.Builder byteplus() {
        return forCloud(Cloud.BYTEPLUS);
    }

    private static ArkService.Builder forCloud(Cloud cloud) {
        ArkService.Builder b = new ArkService.Builder()
                .baseUrl(cloud.baseUrl())
                .region(cloud.region());
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey != null && !apiKey.isEmpty()) {
            b.apiKey(apiKey);
        }
        String ak = System.getenv(cloud.akEnv());
        String sk = System.getenv(cloud.skEnv());
        if (ak != null && !ak.isEmpty()) {
            b.ak(ak);
        }
        if (sk != null && !sk.isEmpty()) {
            b.sk(sk);
        }
        return b;
    }

    // Wrappers around ArkApi's managed-agents endpoints. See ark-apis
    // typespec/{agent, environment, memory, session, skill, vault} for
    // the source of truth on shapes and semantics.

    // ---- Environment ----
    public Environment createEnvironment(CreateEnvironmentRequest request) {
        return execute(api.createEnvironment(request, new HashMap<>()));
    }

    public Environment getEnvironment(String environmentId) {
        return execute(api.getEnvironment(environmentId, new HashMap<>()));
    }

    public ListEnvironmentsResponse listEnvironments(Integer limit, String page) {
        return execute(api.listEnvironments(limit, page, new HashMap<>()));
    }

    public Environment updateEnvironment(String environmentId, UpdateEnvironmentRequest request) {
        return execute(api.updateEnvironment(environmentId, request, new HashMap<>()));
    }

    public DeleteEnvironmentResponse deleteEnvironment(String environmentId) {
        return execute(api.deleteEnvironment(environmentId, new HashMap<>()));
    }

    // ---- Agent ----
    public Agent createAgent(CreateAgentRequest request) {
        return execute(api.createAgent(request, new HashMap<>()));
    }

    public Agent getAgent(String agentId) {
        return execute(api.getAgent(agentId, new HashMap<>()));
    }

    /**
     * List agents with optional filters. Any of the filter args may be null;
     * all null args are omitted from the query string.
     */
    public ListAgentsResponse listAgents(Integer limit, String page,
                                          String createdAtGte, String createdAtLte) {
        return execute(api.listAgents(limit, page, createdAtGte, createdAtLte,
            new HashMap<>()));
    }

    public Agent updateAgent(String agentId, UpdateAgentRequest request) {
        return execute(api.updateAgent(agentId, request, new HashMap<>()));
    }

    public DeleteAgentResponse deleteAgent(String agentId) {
        return execute(api.deleteAgent(agentId, new HashMap<>()));
    }

    public ListAgentsResponse listAgentVersions(String agentId, Integer limit, String page) {
        return execute(api.listAgentVersions(agentId, limit, page, new HashMap<>()));
    }

    // ---- Vault ----
    public Vault createVault(CreateVaultRequest request) {
        return execute(api.createVault(request, new HashMap<>()));
    }

    public Vault getVault(String vaultId) {
        return execute(api.getVault(vaultId, new HashMap<>()));
    }

    public ListVaultsResponse listVaults(Integer limit, String page) {
        return execute(api.listVaults(limit, page, new HashMap<>()));
    }

    public Vault updateVault(String vaultId, UpdateVaultRequest request) {
        return execute(api.updateVault(vaultId, request, new HashMap<>()));
    }

    public DeleteVaultResponse deleteVault(String vaultId) {
        return execute(api.deleteVault(vaultId, new HashMap<>()));
    }

    // ---- Credential (nested under Vault) ----
    public Credential createCredential(String vaultId, CreateCredentialRequest request) {
        return execute(api.createCredential(vaultId, request, new HashMap<>()));
    }

    public Credential getCredential(String vaultId, String credentialId) {
        return execute(api.getCredential(vaultId, credentialId, new HashMap<>()));
    }

    public ListCredentialsResponse listCredentials(String vaultId, Integer limit, String page) {
        return execute(api.listCredentials(vaultId, limit, page, new HashMap<>()));
    }

    public Credential updateCredential(String vaultId, String credentialId, UpdateCredentialRequest request) {
        return execute(api.updateCredential(vaultId, credentialId, request, new HashMap<>()));
    }

    public DeleteCredentialResponse deleteCredential(String vaultId, String credentialId) {
        return execute(api.deleteCredential(vaultId, credentialId, new HashMap<>()));
    }

    public CredentialValidation validateCredential(String vaultId, String credentialId) {
        return execute(api.validateCredential(vaultId, credentialId, new HashMap<>()));
    }

    // ---- MemoryStore ----
    public MemoryStore createMemoryStore(CreateMemoryStoreRequest request) {
        return execute(api.createMemoryStore(request, new HashMap<>()));
    }

    public MemoryStore getMemoryStore(String memoryStoreId) {
        return execute(api.getMemoryStore(memoryStoreId, new HashMap<>()));
    }

    public ListMemoryStoresResponse listMemoryStores(Integer limit, String page,
                                                     List<String> createdBy, String name) {
        return execute(api.listMemoryStores(limit, page, createdBy, name, new HashMap<>()));
    }

    public MemoryStore updateMemoryStore(String memoryStoreId, UpdateMemoryStoreRequest request) {
        return execute(api.updateMemoryStore(memoryStoreId, request, new HashMap<>()));
    }

    public DeleteMemoryStoreResponse deleteMemoryStore(String memoryStoreId) {
        return execute(api.deleteMemoryStore(memoryStoreId, new HashMap<>()));
    }

    // ---- Memory (nested under MemoryStore) ----
    public Memory createMemory(String memoryStoreId, CreateMemoryRequest request) {
        return execute(api.createMemory(memoryStoreId, request, new HashMap<>()));
    }

    public Memory getMemory(String memoryStoreId, String memoryId) {
        return execute(api.getMemory(memoryStoreId, memoryId, new HashMap<>()));
    }

    public ListMemoriesResponse listMemories(String memoryStoreId, String pathPrefix, Integer depth,
                                              String orderBy, Integer limit, String page) {
        return execute(api.listMemories(memoryStoreId, pathPrefix, depth, orderBy, limit,
            page, new HashMap<>()));
    }

    public Memory updateMemory(String memoryStoreId, String memoryId, UpdateMemoryRequest request) {
        return execute(api.updateMemory(memoryStoreId, memoryId, request, new HashMap<>()));
    }

    public DeleteMemoryResponse deleteMemory(String memoryStoreId, String memoryId) {
        return execute(api.deleteMemory(memoryStoreId, memoryId, new HashMap<>()));
    }

    // ---- Skill (multipart) ----
    /**
     * Upload a zip package to create a Skill. The server-side field name is
     * fixed to `files` (see typespec/skill/request.tsp); `displayTitle` is
     * optional.
     */
    public Skill createSkill(MultipartBody.Part files, String displayTitle) {
        RequestBody titlePart = displayTitle == null
            ? null
            : RequestBody.create(MediaType.parse("text/plain"), displayTitle);
        return execute(api.createSkill(files, titlePart, new HashMap<>()));
    }

    public Skill getSkill(String skillId) {
        return execute(api.getSkill(skillId, new HashMap<>()));
    }

    // ---- Session ----
    public Session createSession(CreateSessionRequest request) {
        return execute(api.createSession(request, new HashMap<>()));
    }

    public Session getSession(String sessionId) {
        return execute(api.getSession(sessionId, new HashMap<>()));
    }

    public ListSessionsResponse listSessions(String agentId, Integer agentVersion,
                                              String createdAtGt, String createdAtGte,
                                              String createdAtLt, String createdAtLte,
                                              Integer limit, String memoryStoreId,
                                              String order, String page,
                                              List<String> status) {
        return execute(api.listSessions(agentId, agentVersion, createdAtGt, createdAtGte,
            createdAtLt, createdAtLte, limit, memoryStoreId, order, page, status,
            new HashMap<>()));
    }

    public Session updateSession(String sessionId, UpdateSessionRequest request) {
        return execute(api.updateSession(sessionId, request, new HashMap<>()));
    }

    public DeleteSessionResponse deleteSession(String sessionId) {
        return execute(api.deleteSession(sessionId, new HashMap<>()));
    }

    // ---- SessionResource (nested under Session) ----
    public SessionResource createSessionResource(String sessionId, CreateSessionResourceRequest request) {
        return execute(api.createSessionResource(sessionId, request, new HashMap<>()));
    }

    public ListSessionResourcesResponse listSessionResources(String sessionId) {
        return execute(api.listSessionResources(sessionId, new HashMap<>()));
    }

    // ---- SessionEvent (nested under Session) ----
    public SendSessionEventsResponse sendSessionEvents(String sessionId,
                                                       SendSessionEventsRequest request) {
        return execute(api.sendSessionEvents(sessionId, request, new HashMap<>()));
    }

    /**
     * Open the session's SSE event stream. Returns the raw retrofit Call —
     * caller is responsible for enqueue/execute and decoding SSE frames from
     * the ResponseBody. Follow the pattern used by streamChatCompletion /
     * streamResponse for typed decoding via the SSE utility if the caller
     * needs typed variants; server-side event schemas are heterogeneous
     * (agent.message / span.* / session.*) so this SDK layer stays raw.
     */
    public Call<ResponseBody> streamSessionEvents(String sessionId) {
        return api.streamSessionEvents(sessionId, new HashMap<>());
    }

    /**
     * List historical session events. The response wraps the wire body
     * with a typed events list —
     * {@link ListSessionEventsResponse#getEvents()} returns
     * {@link com.volcengine.ark.runtime.models.session.ManagedAgentsSessionEvent}
     * envelopes carrying the type / id / processed_at fields. Variant-
     * specific payload fields (content / tool_use_id / stop_reason / …)
     * live in {@link ListSessionEventsResponse#getData()} — the union
     * ships 30+ variants (agent.* / session.* / span.* / user.* echoes).
     */
    public ListSessionEventsResponse listSessionEvents(String sessionId,
                                                       String createdAtGt, String createdAtGte,
                                                       String createdAtLt, String createdAtLte,
                                                       Integer limit, String order, String page,
                                                       List<String> types) {
        return execute(api.listSessionEvents(sessionId,
            createdAtGt, createdAtGte, createdAtLt, createdAtLte,
            limit, order, page, types, new HashMap<>()));
    }

    /** Overload with all filters left null — matches the Go SDK convenience. */
    public ListSessionEventsResponse listSessionEvents(String sessionId) {
        return listSessionEvents(sessionId, null, null, null, null, null, null, null, null);
    }

    // ---- SessionThread (nested under Session) ----

    /**
     * List threads under a session (primary + subagent-spawned). Primary
     * thread is materialized lazily on the first event — callers may need
     * to send at least one user.message + wait for status_idle before the
     * list is populated.
     */
    public ListSessionThreadsResponse listSessionThreads(String sessionId,
                                                         Integer limit, String page) {
        return execute(api.listSessionThreads(sessionId, limit, page, new HashMap<>()));
    }

    public ListSessionThreadsResponse listSessionThreads(String sessionId) {
        return listSessionThreads(sessionId, null, null);
    }

    /** Retrieve one thread by id. */
    public SessionThread getSessionThread(String sessionId, String threadId) {
        return execute(api.getSessionThread(sessionId, threadId, new HashMap<>()));
    }

    /**
     * List events attributed to a specific thread. Query semantics mirror
     * listSessionEvents (types/limit/order/created_at bounds/page).
     */
    public ListSessionEventsResponse listSessionThreadEvents(String sessionId, String threadId,
                                                             String createdAtGt, String createdAtGte,
                                                             String createdAtLt, String createdAtLte,
                                                             Integer limit, String order, String page,
                                                             List<String> types) {
        return execute(api.listSessionThreadEvents(sessionId, threadId,
            createdAtGt, createdAtGte, createdAtLt, createdAtLte,
            limit, order, page, types, new HashMap<>()));
    }

    public ListSessionEventsResponse listSessionThreadEvents(String sessionId, String threadId) {
        return listSessionThreadEvents(sessionId, threadId,
            null, null, null, null, null, null, null, null);
    }

    /**
     * Open a thread-scoped SSE event stream. Same lifecycle as
     * streamSessionEvents (no history replay, `data: [DONE]` frame closes).
     */
    public Call<ResponseBody> streamSessionThreadEvents(String sessionId, String threadId) {
        return api.streamSessionThreadEvents(sessionId, threadId, new HashMap<>());
    }

    public static class Builder {
        private String ak;
        private String sk;
        private String apiKey;
        private String region = BASE_REGION;
        private String baseUrl = BASE_URL;
        private Duration timeout = DEFAULT_TIMEOUT;
        private Duration callTimeout;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private int retryTimes = DEFAULT_RETRY_TIMES;
        private Proxy proxy;
        private ConnectionPool connectionPool;
        private Dispatcher dispatcher;
        private Executor callbackExecutor;

        public ArkService.Builder ak(String ak) {
            this.ak = ak;
            return this;
        }

        public ArkService.Builder sk(String sk) {
            this.sk = sk;
            return this;
        }

        public ArkService.Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ArkService.Builder region(String region) {
            this.region = region;
            return this;
        }

        public ArkService.Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            if (!baseUrl.endsWith("/")) {
                this.baseUrl = baseUrl + "/";
            }
            return this;
        }

        public ArkService.Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public ArkService.Builder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        public ArkService.Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public ArkService.Builder retryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
            return this;
        }

        public ArkService.Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public ArkService.Builder connectionPool(ConnectionPool connectionPool) {
            this.connectionPool = connectionPool;
            return this;
        }

        public ArkService.Builder dispatcher(Dispatcher dispatcher) {
            this.dispatcher = dispatcher;
            return this;
        }

        public ArkService.Builder callbackExecutor(Executor callbackExecutor) {
            this.callbackExecutor = callbackExecutor;
            return this;
        }

        public ArkService build() {
            ObjectMapper mapper = defaultObjectMapper();
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
            if (apiKey != null && apiKey.length() > 0) {
                clientBuilder.addInterceptor(new AuthenticationInterceptor(apiKey));
                clientBuilder.addInterceptor(new EncryptionInterceptor(apiKey, baseUrl));
            } else if (ak != null && sk != null && ak.length() > 0 && sk.length() > 0) {
                clientBuilder.addInterceptor(new ArkResourceStsAuthenticationInterceptor(ak, sk, region));
            } else {
                throw new ArkException("missing api_key or ak&sk.");
            }

            if (proxy != null) {
                clientBuilder.proxy(proxy);
            }

            if (connectionPool != null) {
                clientBuilder.connectionPool(connectionPool);
            } else {
                clientBuilder.connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS));
            }

            if (dispatcher != null) {
                clientBuilder.dispatcher(dispatcher);
            } else {
                // Tune the default dispatcher for batch fan-out (see
                // BatchInterceptor / /api/v3/batch/*). Callers that want the
                // stock OkHttp limits can pass their own Dispatcher.
                clientBuilder.dispatcher(defaultBatchDispatcher());
            }

            OkHttpClient client = clientBuilder
                    .addInterceptor(new RequestIdInterceptor())
                    .addInterceptor(new RetryInterceptor(retryTimes))
                    .addInterceptor(new BatchInterceptor())
                    .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .callTimeout(callTimeout == null ? timeout.toMillis() : callTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .connectTimeout(connectTimeout)
                    .build();
            Retrofit retrofit = defaultRetrofit(client, mapper, baseUrl, callbackExecutor);
            return new ArkService(
                    retrofit.create(ArkApi.class),
                    client.dispatcher().executorService()
            );
        }
    }
}
