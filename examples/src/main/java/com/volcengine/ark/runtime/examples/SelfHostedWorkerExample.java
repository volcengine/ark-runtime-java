// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

// Retrofit 2.9 may report an illegal reflective access warning on JDK 9-16.
// The warning is not a startup failure. To suppress it when running with Maven:
//
//   MAVEN_OPTS="--add-opens=java.base/java.lang.invoke=ALL-UNNAMED" \
//     mvn -q -f examples/pom.xml exec:java \
//     -Dexec.mainClass=com.volcengine.ark.runtime.examples.SelfHostedWorkerExample

package com.volcengine.ark.runtime.examples;

import com.volcengine.ark.runtime.selfhosted.EnvironmentWorker;
import com.volcengine.ark.runtime.selfhosted.SelfHostedClient;

public final class SelfHostedWorkerExample {
    private SelfHostedWorkerExample() {
    }

    public static void main(String[] args) {
        String environmentId = requiredEnv("MA_ENVIRONMENT_ID");
        String workerId = System.getenv("MA_WORKER_ID");
        String workdir = envOrDefault("MA_WORKDIR", ".");
        SelfHostedClient.Builder clientBuilder = new SelfHostedClient.Builder()
                .apiKey(requiredEnv("ARK_API_KEY"));
        String baseUrl = System.getenv("ARK_BASE_URL");
        if (baseUrl != null && !baseUrl.isEmpty()) {
            clientBuilder.baseUrl(baseUrl);
        }
        System.out.printf(
                "starting self-hosted worker base_url=%s environment_id=%s worker_id=%s workdir=%s%n",
                baseUrl == null || baseUrl.isEmpty() ? SelfHostedClient.DEFAULT_BASE_URL : baseUrl,
                environmentId,
                workerId == null || workerId.isEmpty() ? "auto" : workerId,
                workdir);
        SelfHostedClient client = clientBuilder.build();
        EnvironmentWorker worker = new EnvironmentWorker(
                client,
                new EnvironmentWorker.Options()
                        .environmentId(environmentId)
                        .workerId(workerId)
                        .workdir(workdir));
        Runtime.getRuntime().addShutdownHook(new Thread(worker::close, "ark-self-hosted-worker-shutdown"));
        try {
            worker.run();
        } finally {
            worker.close();
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? defaultValue : value;
    }
}
