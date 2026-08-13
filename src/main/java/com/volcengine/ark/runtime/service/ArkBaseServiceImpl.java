// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.service;

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
import com.volcengine.ark.runtime.models.file.FileCreateRequest;
import com.volcengine.ark.runtime.models.file.FileDeleted;
import com.volcengine.ark.runtime.models.file.FileListRequest;
import com.volcengine.ark.runtime.models.file.FileListResponse;
import com.volcengine.ark.runtime.models.file.FileObject;
import com.volcengine.ark.runtime.models.images.CreateImageGenerationRequest;
import com.volcengine.ark.runtime.models.images.ImageGenerationResponse;
import com.volcengine.ark.runtime.models.images.ImageGenerationStreamEvent;
import com.volcengine.ark.runtime.models.multimodal_embedding.MultiModalEmbeddingRequest;
import com.volcengine.ark.runtime.models.multimodal_embedding.MultiModalEmbeddingResponse;
import com.volcengine.ark.runtime.models.responses.DeleteResponseRequest;
import com.volcengine.ark.runtime.models.responses.DeleteResponseResponse;
import com.volcengine.ark.runtime.models.responses.GetResponseRequest;
import com.volcengine.ark.runtime.models.responses.ListInputItemsRequest;
import com.volcengine.ark.runtime.models.responses.ListInputItemsResponse;
import com.volcengine.ark.runtime.models.responses.Response;
import com.volcengine.ark.runtime.models.responses.ResponseStreamEvent;
import com.volcengine.ark.runtime.models.responses.ResponsesRequest;
import com.volcengine.ark.runtime.models.tokenization.TokenizationRequest;
import com.volcengine.ark.runtime.models.tokenization.TokenizationResponse;
import io.reactivex.Flowable;

public interface ArkBaseServiceImpl {

    ChatCompletionResponse createChatCompletion(ChatCompletionRequest request);

    ChatCompletionResponse createBatchChatCompletion(ChatCompletionRequest request);

    Flowable<ChatCompletionStreamResponse> streamChatCompletion(ChatCompletionRequest request);

    Response createResponse(ResponsesRequest request);

    Flowable<ResponseStreamEvent> streamResponse(ResponsesRequest request);

    TokenizationResponse createTokenization(TokenizationRequest request);

    EmbeddingResponse createEmbeddings(EmbeddingRequest request);

    EmbeddingResponse createBatchEmbeddings(EmbeddingRequest request);

    MultiModalEmbeddingResponse createMultiModalEmbeddings(MultiModalEmbeddingRequest request);

    MultiModalEmbeddingResponse createBatchMultiModalEmbeddings(MultiModalEmbeddingRequest request);

    ImageGenerationResponse generateImages(CreateImageGenerationRequest request);

    Flowable<ImageGenerationStreamEvent> streamGenerateImages(CreateImageGenerationRequest request);

    CreateContentGenerationTaskResponse createContentGenerationTask(CreateContentGenerationTaskRequest request);

    ContentGenerationTask getContentGenerationTask(String taskId);

    ListContentGenerationTasksResponse listContentGenerationTasks(ListContentGenerationTasksRequest request);

    void deleteContentGenerationTask(String taskId);

    Response getResponse(GetResponseRequest request);

    DeleteResponseResponse deleteResponse(DeleteResponseRequest request);

    ListInputItemsResponse listResponseInputItems(ListInputItemsRequest request);

    FileObject uploadFile(FileCreateRequest request, java.io.File file);

    FileObject retrieveFile(String fileId);

    FileDeleted deleteFile(String fileId);

    FileListResponse listFiles(FileListRequest request);

    FileObject waitForFileProcessing(String fileId);

    FileObject waitForFileProcessing(String fileId, java.time.Duration pollInterval, java.time.Duration maxWait);
}
