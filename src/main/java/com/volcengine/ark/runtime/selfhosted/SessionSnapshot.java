// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SessionSnapshot {
    private String id = "";
    private final List<SkillRef> skills = new ArrayList<>();
    private final List<SkillRef> agentSkills = new ArrayList<>();
    private Map<String, Object> raw;

    @SuppressWarnings("unchecked")
    public static SessionSnapshot fromMap(Map<String, Object> raw) {
        SessionSnapshot snapshot = new SessionSnapshot();
        if (raw == null) {
            return snapshot;
        }
        snapshot.raw = raw;
        snapshot.id = stringValue(raw.get("id"));
        Object skills = raw.get("skills");
        if (skills instanceof List) {
            snapshot.skills.addAll(parseSkills((List<Object>) skills));
        }
        Object agent = raw.get("agent");
        if (agent instanceof Map) {
            Object agentSkill = ((Map<String, Object>) agent).get("skills");
            if (agentSkill instanceof List) {
                snapshot.agentSkills.addAll(parseSkills((List<Object>) agentSkill));
            }
        }
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private static List<SkillRef> parseSkills(List<Object> raw) {
        List<SkillRef> out = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map) {
                out.add(SkillRef.fromMap((Map<String, Object>) item));
            }
        }
        return out;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public List<SkillRef> skillRefs() {
        return skills.isEmpty() ? agentSkills : skills;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }
}
