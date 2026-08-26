// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.selfhosted;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListEventsResponse {
    private final List<Event> events;
    private final String nextPage;

    @SuppressWarnings("unchecked")
    public static ListEventsResponse fromMap(Map<String, Object> raw) {
        List<Event> events = new ArrayList<>();
        if (raw != null && raw.get("data") instanceof List) {
            for (Object item : (List<Object>) raw.get("data")) {
                if (item instanceof Map) {
                    events.add(Event.fromMap((Map<String, Object>) item));
                }
            }
        }
        String nextPage = raw == null || raw.get("next_page") == null ? "" : String.valueOf(raw.get("next_page"));
        return new ListEventsResponse(events, nextPage);
    }

    public ListEventsResponse(List<Event> events, String nextPage) {
        this.events = events == null ? new ArrayList<Event>() : events;
        this.nextPage = nextPage == null ? "" : nextPage;
    }

    public List<Event> getEvents() {
        return events;
    }

    public String getNextPage() {
        return nextPage;
    }
}
