// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0
package com.volcengine.ark.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.models.content_generation.*;
import com.volcengine.ark.runtime.models.vault.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArkApiListCompatibilityTest {
  @Test public void existingOverloadsPreserveV1AndSendV2WireQueries() {
    final java.util.List<okhttp3.HttpUrl> seen = new ArrayList<>();
    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
      seen.add(chain.request().url());
      return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
        .code(200).message("OK").body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"),
          "{\"total\":0,\"items\":[],\"data\":[]}")).build();
    }).build();
    ArkApi api = new retrofit2.Retrofit.Builder().baseUrl("https://example.test/").client(client)
      .addConverterFactory(retrofit2.converter.jackson.JacksonConverterFactory.create(ArkService.defaultObjectMapper()))
      .addCallAdapterFactory(retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory.create()).build().create(ArkApi.class);
    ArkService service = new ArkService(api);
    service.listContentGenerationTasks(new ListContentGenerationTasksRequest().pageNum(2));
    assertEquals("2", seen.get(0).queryParameter("page_num"));
    assertNull(seen.get(0).queryParameter("model"));
    service.listContentGenerationTasks(new ListContentGenerationTasksRequest().model("ep-example").limit(5)
      .filterStatuses(Arrays.asList(TaskStatus.QUEUED, TaskStatus.SUCCEEDED)).order(ListOrder.DESC));
    assertEquals("ep-example", seen.get(1).queryParameter("model"));
    assertEquals("5", seen.get(1).queryParameter("limit"));
    assertEquals(Arrays.asList("queued", "succeeded"), seen.get(1).queryParameterValues("filter.statuses"));
    assertEquals("desc", seen.get(1).queryParameter("order"));
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
  }

  @Test public void oldAccessorsSerializeOnlyDataAndEmptyListsSurvive() throws Exception {
    assertTrue(ListCredentialsResponse.class.getMethod("getCredentials").isAnnotationPresent(javax.annotation.Nonnull.class));
    for (String name : new String[]{"setCredentials", "credentials"}) {
      boolean nonnull = false;
      for (java.lang.annotation.Annotation annotation : ListCredentialsResponse.class.getMethod(name, List.class).getParameterAnnotations()[0]) {
        nonnull |= annotation.annotationType() == javax.annotation.Nonnull.class;
      }
      assertTrue(name + " lost Nonnull", nonnull);
    }
    ObjectMapper mapper = ArkService.defaultObjectMapper();
    ListCredentialsResponse list = ListCredentialsResponse.builder().credentials(new ArrayList<>()).build();
    list.addCredentialsItem(new Credential().auth(new CredentialAuth().type(CredentialAuthType.MCP_STATIC)));
    assertSame(list.getData(), list.getCredentials());
    list.setCredentials(Collections.emptyList());
    JsonNode wire = mapper.readTree(mapper.writeValueAsString(list));
    assertTrue(wire.get("data").isArray()); assertFalse(wire.has("credentials"));
    for (String raw : new String[]{"{\"total\":0,\"items\":[]}", "{\"data\":[]}", "{}"}) {
      assertEquals(mapper.readTree(raw), mapper.readTree(mapper.writeValueAsString(mapper.readValue(raw,ListContentGenerationTasksResponse.class))));
    }
  }
}
