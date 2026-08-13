// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.service;

/**
 * Identifies which cloud provider an {@link ArkService} client targets.
 *
 * <p>The {@code ARK_API_KEY} environment variable is shared across clouds,
 * but AK/SK env-var names differ. Use {@link ArkService#volc()} or
 * {@link ArkService#byteplus()} to construct a builder with the right
 * defaults wired in.
 */
public enum Cloud {
    VOLC(
            "https://ark.cn-beijing.volces.com",
            "cn-beijing",
            "VOLC_ACCESSKEY",
            "VOLC_SECRETKEY"),
    BYTEPLUS(
            "https://ark.ap-southeast.bytepluses.com",
            "ap-southeast-1",
            "BYTEPLUS_ACCESSKEY",
            "BYTEPLUS_SECRETKEY");

    private final String baseUrl;
    private final String region;
    private final String akEnv;
    private final String skEnv;

    Cloud(String baseUrl, String region, String akEnv, String skEnv) {
        this.baseUrl = baseUrl;
        this.region = region;
        this.akEnv = akEnv;
        this.skEnv = skEnv;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String region() {
        return region;
    }

    public String akEnv() {
        return akEnv;
    }

    public String skEnv() {
        return skEnv;
    }
}
