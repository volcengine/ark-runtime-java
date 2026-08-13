// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

/*
 * Ark Managed Agents Session API
 *
 * Hand-written typed wrapper on top of the codegen-emitted
 * ListSessionEventsResponseWire. Preserved across regen by the Makefile
 * vendor-session step (rsync --exclude=ListSessionEventsResponse.java).
 *
 * Rationale: the wire response ships a heterogeneous JSON union under
 * `data` (30+ variants: agent.message / span.* / session.* / user.*
 * echoes). openapi-generator can only model that as
 * List<Map<String, Object>>, which is technically accurate but forces
 * callers to reach into a map for the discriminator. We expose the
 * same shape here typed as List<ManagedAgentsSessionEvent> so callers
 * get compile-time access to the envelope fields (type / id /
 * processed_at) while variant-specific payload access still goes
 * through the raw map (getRawData()) — parity with the Go SDK's
 * ListSessionEventsResponse.Events slice + fallback ManagedAgentsUnknown
 * *Event.RawPayload escape hatch.
 */

package com.volcengine.ark.runtime.models.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Typed response for ArkService#listSessionEvents /
 * #listSessionThreadEvents. Wraps the wire schema
 * ({@link ListSessionEventsResponseWire}) with an events list where each
 * item is a {@link ManagedAgentsSessionEvent} envelope (populated with the
 * type / id / processed_at fields from the wire object). Variant-specific
 * payload fields (content / tool_use_id / stop_reason / …) are not
 * declared on the envelope; access them through {@link #getData()} until
 * a follow-up typed-variant layer lands.
 */
public class ListSessionEventsResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ManagedAgentsSessionEvent> events;
    private final List<Map<String, Object>> rawData;
    private final String nextPage;

    /**
     * Jackson entry point — deserializes a wire response payload
     * ({@code {"data": [...], "next_page": "..."}}) into this typed shape.
     */
    @JsonCreator
    public ListSessionEventsResponse(
            @JsonProperty("data") List<Map<String, Object>> data,
            @JsonProperty("next_page") String nextPage) {
        this.rawData = data != null ? data : new ArrayList<>();
        this.nextPage = nextPage;
        this.events = new ArrayList<>(this.rawData.size());
        for (Map<String, Object> item : this.rawData) {
            this.events.add(toEnvelope(item));
        }
    }

    /**
     * Build a typed response from the codegen-emitted wire class. Useful
     * when a caller has a {@link ListSessionEventsResponseWire} in hand
     * (e.g. from lower-level plumbing) and wants the typed view.
     */
    public static ListSessionEventsResponse fromWire(ListSessionEventsResponseWire wire) {
        if (wire == null) {
            return new ListSessionEventsResponse(null, null);
        }
        return new ListSessionEventsResponse(wire.getData(), wire.getNextPage());
    }

    /**
     * Typed page of events — each item carries the envelope fields
     * (type / id / processed_at). For variant-specific payload access,
     * fall back to {@link #getData()} and index into the matching map.
     */
    public List<ManagedAgentsSessionEvent> getEvents() {
        return events;
    }

    /**
     * Raw wire page of events — the untouched JSON each event ships with,
     * one Map per event. Use when you need fields the envelope doesn't
     * declare (content / tool_use_id / stop_reason / …); same order as
     * {@link #getEvents()}. Retains the wire field name (`data`) so
     * callers moving off {@link ListSessionEventsResponseWire} keep the
     * same accessor.
     */
    public List<Map<String, Object>> getData() {
        return rawData;
    }

    /**
     * Cursor for the next page of results; null / empty when this is the
     * last page.
     */
    public String getNextPage() {
        return nextPage;
    }

    /**
     * Build a {@link ManagedAgentsSessionEvent} envelope from a raw wire
     * event map. Only the shared envelope fields are populated
     * (type / id / processed_at); everything else stays in the raw map.
     * Unknown / malformed items surface with type = "" so callers can
     * detect them.
     */
    private static ManagedAgentsSessionEvent toEnvelope(Map<String, Object> item) {
        ManagedAgentsSessionEvent ev = new ManagedAgentsSessionEvent();
        if (item == null) {
            ev.setType("");
            return ev;
        }
        Object t = item.get("type");
        ev.setType(t instanceof String ? (String) t : "");
        Object id = item.get("id");
        if (id instanceof String) {
            ev.setId((String) id);
        }
        Object at = item.get("processed_at");
        if (at instanceof String) {
            ev.setProcessedAt((String) at);
        }
        return ev;
    }
}
