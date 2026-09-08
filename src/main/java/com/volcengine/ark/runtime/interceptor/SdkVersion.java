// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.interceptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class SdkVersion {
    static final String VERSION = loadVersion();

    private SdkVersion() {}

    private static String loadVersion() {
        // Maven filters this resource from project.version and includes it in the JAR.
        try (InputStream input = SdkVersion.class.getResourceAsStream(
                "/com/volcengine/ark/runtime/sdk-version.properties")) {
            if (input != null) {
                Properties properties = new Properties();
                properties.load(input);
                String version = properties.getProperty("version", "").trim();
                if (!version.isEmpty() && !version.contains("${")) {
                    return version;
                }
            }
        } catch (IOException ignored) {
            // Source-only builds may omit the generated metadata; requests still work.
        }
        return "unknown";
    }
}
