// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.service;

import com.volcengine.ark.runtime.Const;
import com.volcengine.ark.runtime.models.agent.Agent;
import com.volcengine.ark.runtime.models.agent.CreateAgentRequest;
import com.volcengine.ark.runtime.models.agent.DeleteAgentResponse;
import com.volcengine.ark.runtime.models.agent.ListAgentsResponse;
import com.volcengine.ark.runtime.models.agent.UpdateAgentRequest;
import com.volcengine.ark.runtime.models.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.models.chat.ChatCompletionResponse;
import com.volcengine.ark.runtime.models.content_generation.ContentGenerationTask;
import com.volcengine.ark.runtime.models.content_generation.CreateContentGenerationTaskRequest;
import com.volcengine.ark.runtime.models.content_generation.CreateContentGenerationTaskResponse;
import com.volcengine.ark.runtime.models.content_generation.ListContentGenerationTasksResponse;
import com.volcengine.ark.runtime.models.embedding.EmbeddingRequest;
import com.volcengine.ark.runtime.models.embedding.EmbeddingResponse;
import com.volcengine.ark.runtime.models.environment.CreateEnvironmentRequest;
import com.volcengine.ark.runtime.models.environment.DeleteEnvironmentResponse;
import com.volcengine.ark.runtime.models.environment.Environment;
import com.volcengine.ark.runtime.models.environment.ListEnvironmentsResponse;
import com.volcengine.ark.runtime.models.environment.UpdateEnvironmentRequest;
import com.volcengine.ark.runtime.models.file.FileDeleted;
import com.volcengine.ark.runtime.models.file.FileListResponse;
import com.volcengine.ark.runtime.models.file.FileObject;
import com.volcengine.ark.runtime.models.images.CreateImageGenerationRequest;
import com.volcengine.ark.runtime.models.images.ImageGenerationResponse;
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
import com.volcengine.ark.runtime.models.responses.DeleteResponseResponse;
import com.volcengine.ark.runtime.models.responses.ListInputItemsResponse;
import com.volcengine.ark.runtime.models.responses.Response;
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
import io.reactivex.Completable;
import io.reactivex.Single;
import java.util.List;
import java.util.Map;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ArkApi {

    @POST("/api/v3/chat/completions")
    Single<ChatCompletionResponse> createChatCompletion(@Body ChatCompletionRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/batch/chat/completions")
    Single<ChatCompletionResponse> createBatchChatCompletion(@Body ChatCompletionRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @Streaming
    @POST("/api/v3/chat/completions")
    Call<ResponseBody> streamChatCompletion(@Body ChatCompletionRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/embeddings")
    Single<EmbeddingResponse> createEmbeddings(@Body EmbeddingRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/batch/embeddings")
    Single<EmbeddingResponse> createBatchEmbeddings(@Body EmbeddingRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/embeddings/multimodal")
    Single<MultiModalEmbeddingResponse> createMultiModalEmbeddings(@Body MultiModalEmbeddingRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/batch/embeddings/multimodal")
    Single<MultiModalEmbeddingResponse> createBatchMultiModalEmbeddings(@Body MultiModalEmbeddingRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/tokenization")
    Single<TokenizationResponse> createTokenization(@Body TokenizationRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/images/generations")
    Single<ImageGenerationResponse> generateImages(@Body CreateImageGenerationRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @Streaming
    @POST("/api/v3/images/generations")
    Call<ResponseBody> streamGenerateImages(@Body CreateImageGenerationRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/contents/generations/tasks")
    Single<CreateContentGenerationTaskResponse> createContentGenerationTask(@Body CreateContentGenerationTaskRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/contents/generations/tasks/{taskId}")
    Single<ContentGenerationTask> getContentGenerationTask(@Path("taskId") String taskId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/contents/generations/tasks")
    Single<ListContentGenerationTasksResponse> listContentGenerationTasks(
            @Query("page_num") Integer pageNum,
            @Query("page_size") Integer pageSize,
            @Query("filter.status") String status,
            @Query("filter.model") String model,
            @Query("filter.service_tier") String serviceTier,
            @Query("filter.task_ids") List<String> taskIds,
            @HeaderMap Map<String, String> customHeaders
    );

    @DELETE("/api/v3/contents/generations/tasks/{taskId}")
    Completable deleteContentGenerationTask(@Path("taskId") String taskId, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/responses")
    Single<Response> createResponse(@Body ResponsesRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @Streaming
    @POST("/api/v3/responses")
    Call<ResponseBody> streamResponse(@Body ResponsesRequest request, @Header(Const.REQUEST_MODEL) String model, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/responses/{responseId}")
    Single<Response> getResponse(@Path("responseId") String responsesId, @HeaderMap Map<String, String> customHeaders);

    @DELETE("/api/v3/responses/{responseId}")
    Single<DeleteResponseResponse> deleteResponse(@Path("responseId") String responsesId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/responses/{responseId}/input_items")
    Single<ListInputItemsResponse> listResponseInputItems(
            @Path("responseId") String responsesId,
            @Query("after") String after,
            @Query("before") String before,
            @Query("limit") Integer limit,
            @Query("order") String order,
            @Query("include[]") List<String> include,
            @HeaderMap Map<String, String> customHeaders
    );

    @Multipart
    @POST("/api/v3/files")
    Single<FileObject> uploadFile(@Part MultipartBody.Part file,
                                  @Part("purpose") RequestBody purpose,
                                  @Part("expire_at") RequestBody expireAt,
                                  @Part("url") RequestBody url,
                                  @Part("preprocess_configs[video][fps]") RequestBody fps,
                                  @Part("preprocess_configs[video][model]") RequestBody fpsModel,
                                  @Part("tos[bucket]") RequestBody tosBucket,
                                  @Part("tos[prefix]") RequestBody tosPrefix,
                                  @HeaderMap Map<String, String> customHeaders);

    @DELETE("/api/v3/files/{fileId}")
    Single<FileDeleted> deleteFile(@Path("fileId") String fileId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/files/{fileId}")
    Single<FileObject> retrieveFile(@Path("fileId") String fileId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/files")
    Single<FileListResponse> listFiles(@Query("limit") Integer limit,
                                       @Query("after") String after,
                                       @Query("purpose") String purpose,
                                       @Query("order") String order,
                                       @HeaderMap Map<String, String> customHeaders);

    // See ark-apis typespec/{agent, environment, memory, session, skill, vault}.

    // ---- Environment ----
    @POST("/api/v3/environments")
    Single<Environment> createEnvironment(@Body CreateEnvironmentRequest request, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/environments/{environmentId}")
    Single<Environment> getEnvironment(@Path("environmentId") String environmentId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/environments")
    Single<ListEnvironmentsResponse> listEnvironments(@Query("limit") Integer limit, @Query("page") String page, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/environments/{environmentId}")
    Single<Environment> updateEnvironment(@Path("environmentId") String environmentId, @Body UpdateEnvironmentRequest request, @HeaderMap Map<String, String> customHeaders);

    @DELETE("/api/v3/environments/{environmentId}")
    Single<DeleteEnvironmentResponse> deleteEnvironment(@Path("environmentId") String environmentId, @HeaderMap Map<String, String> customHeaders);

    // ---- Agent ----
    @POST("/api/v3/agents")
    Single<Agent> createAgent(@Body CreateAgentRequest request, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/agents/{agentId}")
    Single<Agent> getAgent(@Path("agentId") String agentId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/agents")
    Single<ListAgentsResponse> listAgents(@Query("limit") Integer limit,
                                          @Query("page") String page,
                                          @Query("created_at_gte") String createdAtGte,
                                          @Query("created_at_lte") String createdAtLte,
                                          @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/agents/{agentId}")
    Single<Agent> updateAgent(@Path("agentId") String agentId, @Body UpdateAgentRequest request, @HeaderMap Map<String, String> customHeaders);

    @DELETE("/api/v3/agents/{agentId}")
    Single<DeleteAgentResponse> deleteAgent(@Path("agentId") String agentId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/agents/{agentId}/versions")
    Single<ListAgentsResponse> listAgentVersions(@Path("agentId") String agentId, @Query("limit") Integer limit, @Query("page") String page, @HeaderMap Map<String, String> customHeaders);

    // ---- Vault ----
    @POST("/api/v3/vaults")
    Single<Vault> createVault(@Body CreateVaultRequest request, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/vaults/{vaultId}")
    Single<Vault> getVault(@Path("vaultId") String vaultId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/vaults")
    Single<ListVaultsResponse> listVaults(@Query("limit") Integer limit, @Query("page") String page, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/vaults/{vaultId}")
    Single<Vault> updateVault(@Path("vaultId") String vaultId, @Body UpdateVaultRequest request, @HeaderMap Map<String, String> customHeaders);

    @DELETE("/api/v3/vaults/{vaultId}")
    Single<DeleteVaultResponse> deleteVault(@Path("vaultId") String vaultId, @HeaderMap Map<String, String> customHeaders);

    // ---- Credential (nested under Vault) ----
    @POST("/api/v3/vaults/{vaultId}/credentials")
    Single<Credential> createCredential(@Path("vaultId") String vaultId, @Body CreateCredentialRequest request, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/vaults/{vaultId}/credentials/{credentialId}")
    Single<Credential> getCredential(@Path("vaultId") String vaultId, @Path("credentialId") String credentialId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/vaults/{vaultId}/credentials")
    Single<ListCredentialsResponse> listCredentials(@Path("vaultId") String vaultId, @Query("limit") Integer limit, @Query("page") String page, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/vaults/{vaultId}/credentials/{credentialId}")
    Single<Credential> updateCredential(@Path("vaultId") String vaultId, @Path("credentialId") String credentialId, @Body UpdateCredentialRequest request, @HeaderMap Map<String, String> customHeaders);

    @DELETE("/api/v3/vaults/{vaultId}/credentials/{credentialId}")
    Single<DeleteCredentialResponse> deleteCredential(@Path("vaultId") String vaultId, @Path("credentialId") String credentialId, @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/vaults/{vaultId}/credentials/{credentialId}/mcp_oauth_validate")
    Single<CredentialValidation> validateCredential(@Path("vaultId") String vaultId, @Path("credentialId") String credentialId, @HeaderMap Map<String, String> customHeaders);

    // ---- MemoryStore ----
    @POST("/api/v3/memory_stores")
    Single<MemoryStore> createMemoryStore(@Body CreateMemoryStoreRequest request, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/memory_stores/{memoryStoreId}")
    Single<MemoryStore> getMemoryStore(@Path("memoryStoreId") String memoryStoreId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/memory_stores")
    Single<ListMemoryStoresResponse> listMemoryStores(@Query("limit") Integer limit,
                                                       @Query("page") String page,
                                                       @Query("created_by") List<String> createdBy,
                                                       @Query("name") String name,
                                                       @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/memory_stores/{memoryStoreId}")
    Single<MemoryStore> updateMemoryStore(@Path("memoryStoreId") String memoryStoreId, @Body UpdateMemoryStoreRequest request, @HeaderMap Map<String, String> customHeaders);

    @DELETE("/api/v3/memory_stores/{memoryStoreId}")
    Single<DeleteMemoryStoreResponse> deleteMemoryStore(@Path("memoryStoreId") String memoryStoreId, @HeaderMap Map<String, String> customHeaders);

    // ---- Memory (nested under MemoryStore) ----
    @POST("/api/v3/memory_stores/{memoryStoreId}/memories")
    Single<Memory> createMemory(@Path("memoryStoreId") String memoryStoreId, @Body CreateMemoryRequest request, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/memory_stores/{memoryStoreId}/memories/{memoryId}")
    Single<Memory> getMemory(@Path("memoryStoreId") String memoryStoreId, @Path("memoryId") String memoryId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/memory_stores/{memoryStoreId}/memories")
    Single<ListMemoriesResponse> listMemories(@Path("memoryStoreId") String memoryStoreId,
                                              @Query("path_prefix") String pathPrefix,
                                              @Query("depth") Integer depth,
                                              @Query("order_by") String orderBy,
                                              @Query("limit") Integer limit,
                                              @Query("page") String page,
                                              @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/memory_stores/{memoryStoreId}/memories/{memoryId}")
    Single<Memory> updateMemory(@Path("memoryStoreId") String memoryStoreId, @Path("memoryId") String memoryId, @Body UpdateMemoryRequest request, @HeaderMap Map<String, String> customHeaders);

    @DELETE("/api/v3/memory_stores/{memoryStoreId}/memories/{memoryId}")
    Single<DeleteMemoryResponse> deleteMemory(@Path("memoryStoreId") String memoryStoreId, @Path("memoryId") String memoryId, @HeaderMap Map<String, String> customHeaders);

    // ---- Skill (multipart) ----
    @Multipart
    @POST("/api/v3/skills")
    Single<Skill> createSkill(@Part MultipartBody.Part files,
                              @Part("display_title") RequestBody displayTitle,
                              @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/skills/{skillId}")
    Single<Skill> getSkill(@Path("skillId") String skillId, @HeaderMap Map<String, String> customHeaders);

    // ---- Session ----
    @POST("/api/v3/sessions")
    Single<Session> createSession(@Body CreateSessionRequest request, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/sessions/{sessionId}")
    Single<Session> getSession(@Path("sessionId") String sessionId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/sessions")
    Single<ListSessionsResponse> listSessions(@Query("agent_id") String agentId,
                                              @Query("agent_version") Integer agentVersion,
                                              @Query("created_at_gt") String createdAtGt,
                                              @Query("created_at_gte") String createdAtGte,
                                              @Query("created_at_lt") String createdAtLt,
                                              @Query("created_at_lte") String createdAtLte,
                                              @Query("limit") Integer limit,
                                              @Query("memory_store_id") String memoryStoreId,
                                              @Query("order") String order,
                                              @Query("page") String page,
                                              @Query("status") List<String> status,
                                              @HeaderMap Map<String, String> customHeaders);

    @POST("/api/v3/sessions/{sessionId}")
    Single<Session> updateSession(@Path("sessionId") String sessionId, @Body UpdateSessionRequest request, @HeaderMap Map<String, String> customHeaders);

    @DELETE("/api/v3/sessions/{sessionId}")
    Single<DeleteSessionResponse> deleteSession(@Path("sessionId") String sessionId, @HeaderMap Map<String, String> customHeaders);

    // ---- SessionResource (nested under Session) ----
    @POST("/api/v3/sessions/{sessionId}/resources")
    Single<SessionResource> createSessionResource(@Path("sessionId") String sessionId, @Body CreateSessionResourceRequest request, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/sessions/{sessionId}/resources")
    Single<ListSessionResourcesResponse> listSessionResources(@Path("sessionId") String sessionId, @HeaderMap Map<String, String> customHeaders);

    // ---- SessionEvent (nested under Session) ----
    @POST("/api/v3/sessions/{sessionId}/events")
    Single<SendSessionEventsResponse> sendSessionEvents(@Path("sessionId") String sessionId, @Body SendSessionEventsRequest request, @HeaderMap Map<String, String> customHeaders);

    @Streaming
    @GET("/api/v3/sessions/{sessionId}/events/stream")
    Call<ResponseBody> streamSessionEvents(@Path("sessionId") String sessionId, @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/sessions/{sessionId}/events")
    Single<ListSessionEventsResponse> listSessionEvents(@Path("sessionId") String sessionId,
                                                        @Query("created_at[gt]") String createdAtGt,
                                                        @Query("created_at[gte]") String createdAtGte,
                                                        @Query("created_at[lt]") String createdAtLt,
                                                        @Query("created_at[lte]") String createdAtLte,
                                                        @Query("limit") Integer limit,
                                                        @Query("order") String order,
                                                        @Query("page") String page,
                                                        @Query("types") List<String> types,
                                                        @HeaderMap Map<String, String> customHeaders);

    // ---- SessionThread (nested under Session) ----
    @GET("/api/v3/sessions/{sessionId}/threads")
    Single<ListSessionThreadsResponse> listSessionThreads(@Path("sessionId") String sessionId,
                                                          @Query("limit") Integer limit,
                                                          @Query("page") String page,
                                                          @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/sessions/{sessionId}/threads/{threadId}")
    Single<SessionThread> getSessionThread(@Path("sessionId") String sessionId,
                                           @Path("threadId") String threadId,
                                           @HeaderMap Map<String, String> customHeaders);

    @GET("/api/v3/sessions/{sessionId}/threads/{threadId}/events")
    Single<ListSessionEventsResponse> listSessionThreadEvents(@Path("sessionId") String sessionId,
                                                              @Path("threadId") String threadId,
                                                              @Query("created_at[gt]") String createdAtGt,
                                                              @Query("created_at[gte]") String createdAtGte,
                                                              @Query("created_at[lt]") String createdAtLt,
                                                              @Query("created_at[lte]") String createdAtLte,
                                                              @Query("limit") Integer limit,
                                                              @Query("order") String order,
                                                              @Query("page") String page,
                                                              @Query("types") List<String> types,
                                                              @HeaderMap Map<String, String> customHeaders);

    @Streaming
    @GET("/api/v3/sessions/{sessionId}/threads/{threadId}/stream")
    Call<ResponseBody> streamSessionThreadEvents(@Path("sessionId") String sessionId,
                                                 @Path("threadId") String threadId,
                                                 @HeaderMap Map<String, String> customHeaders);

}
