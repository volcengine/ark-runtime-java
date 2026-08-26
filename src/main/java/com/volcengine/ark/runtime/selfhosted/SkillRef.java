// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.util.Map;

public class SkillRef {
    private String name = "";
    private String displayName = "";
    private String id = "";
    private String skillId = "";
    private String type = "";
    private String version = "";
    private String downloadUrl = "";

    public static SkillRef fromMap(Map<String, Object> raw) {
        SkillRef ref = new SkillRef();
        if (raw == null) {
            return ref;
        }
        ref.name = stringValue(raw.get("name"));
        ref.displayName = stringValue(raw.get("display_name"));
        ref.id = stringValue(raw.get("id"));
        ref.skillId = stringValue(raw.get("skill_id"));
        ref.type = stringValue(raw.get("type"));
        ref.version = stringValue(raw.get("version"));
        ref.downloadUrl = stringValue(raw.get("download_url"));
        return ref;
    }

    public String idValue() {
        return skillId != null && !skillId.isEmpty() ? skillId : id;
    }

    public String nameValue() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        return idValue();
    }

    SkillRef withResolvedMetadata(String resolvedName, String latestVersion) {
        this.name = stringValue(resolvedName);
        if (this.version == null || this.version.isEmpty()) {
            this.version = stringValue(latestVersion);
        }
        return this;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}
