// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class ToolContext {
    private String workdir;
    private Map<String, String> env = new HashMap<>();
    private boolean explicitEnv;
    private boolean unrestrictedPaths;
    private long toolTimeoutMillis = SelfHostedConstants.DEFAULT_TOOL_TIMEOUT_MILLIS;
    private BooleanSupplier cancelled = () -> false;

    public ToolContext(String workdir) {
        this.workdir = workdir;
    }

    public String getWorkdir() {
        return workdir;
    }

    public void setWorkdir(String workdir) {
        this.workdir = workdir;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env == null ? new HashMap<String, String>() : env;
        this.explicitEnv = true;
    }

    public boolean hasExplicitEnv() {
        return explicitEnv;
    }

    public boolean isUnrestrictedPaths() {
        return unrestrictedPaths;
    }

    public void setUnrestrictedPaths(boolean unrestrictedPaths) {
        this.unrestrictedPaths = unrestrictedPaths;
    }

    public long getToolTimeoutMillis() {
        return toolTimeoutMillis;
    }

    public void setToolTimeoutMillis(long toolTimeoutMillis) {
        this.toolTimeoutMillis = toolTimeoutMillis;
    }

    public boolean isCancelled() {
        return cancelled != null && cancelled.getAsBoolean();
    }

    public void setCancelled(BooleanSupplier cancelled) {
        this.cancelled = cancelled == null ? () -> false : cancelled;
    }
}
