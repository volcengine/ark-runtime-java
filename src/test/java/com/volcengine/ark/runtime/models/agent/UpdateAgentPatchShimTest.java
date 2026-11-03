// Copyright (c) 2026 ByteDance Ltd. and/or its affiliates.
// SPDX-License-Identifier: Apache-2.0

package com.volcengine.ark.runtime.models.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.service.ArkService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class UpdateAgentPatchShimTest {
    private final ObjectMapper mapper = ArkService.defaultObjectMapper();

    @Test
    public void serializesOuterMultiagentAsOmittedNullAndValue() throws Exception {
        JsonNode omitted = mapper.readTree(mapper.writeValueAsString(UpdateAgentPatch_shim.request(1)));
        assertFalse(omitted.toString(), omitted.has("multiagent"));

        JsonNode explicitNull = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.request(1).removeMultiagent()));
        assertTrue(explicitNull.get("multiagent").isNull());

        UpdateAgentRequest plainRequest = new UpdateAgentRequest();
        plainRequest.setVersion(1);
        JsonNode plainExplicitNull = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.removeMultiagent(plainRequest)));
        assertTrue(plainExplicitNull.get("multiagent").isNull());

        MultiagentPatch value = UpdateAgentPatch_shim.multiagent("multiagent_20261001")
                .explicitNullSubagents();
        JsonNode object = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.request(1).multiagent(value)));
        assertEquals("multiagent_20261001", object.get("multiagent").get("type").asText());
        assertTrue(object.get("multiagent").get("subagents").isNull());
    }

    @Test
    public void serializesNestedOmittedAndExplicitNullFields() throws Exception {
        UpdateAgentPatch_shim.Multiagent multiagent = UpdateAgentPatch_shim.multiagent("multiagent_20261001");
        JsonNode omitted = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.request(1).multiagent(multiagent)));
        assertFalse(omitted.toString(), omitted.get("multiagent").has("agents"));
        assertFalse(omitted.toString(), omitted.get("multiagent").has("subagents"));

        UpdateAgentPatch_shim.Multiagent explicitNullParent = UpdateAgentPatch_shim.multiagent("multiagent_20261001")
                .subagents(UpdateAgentPatch_shim.subagents("enabled")
                        .explicitNullInlineAgents()
                        .explicitNullPredefinedAgents());
        JsonNode explicitNull = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.request(1).multiagent(explicitNullParent)));
        JsonNode subagents = explicitNull.get("multiagent").get("subagents");
        assertTrue(subagents.get("inline_agents").isNull());
        assertTrue(subagents.get("predefined_agents").isNull());
    }

    @Test
    public void distinguishesEmptyAndNullPredefinedAgents() throws Exception {
        MultiagentPatch empty = UpdateAgentPatch_shim.multiagent("multiagent_20261001")
                .subagents(UpdateAgentPatch_shim.subagents("enabled")
                        .predefinedAgents(Collections.emptyList()));
        JsonNode emptyNode = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.request(1).multiagent(empty)));
        assertTrue(emptyNode.get("multiagent").get("subagents").get("predefined_agents").isArray());
        assertEquals(0, emptyNode.get("multiagent").get("subagents").get("predefined_agents").size());

        MultiagentPatch explicitNull = UpdateAgentPatch_shim.multiagent("multiagent_20261001")
                .subagents(UpdateAgentPatch_shim.subagents("enabled")
                        .explicitNullPredefinedAgents());
        JsonNode nullNode = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.request(1).multiagent(explicitNull)));
        assertTrue(nullNode.get("multiagent").get("subagents").get("predefined_agents").isNull());
    }

    @Test
    public void roundTripsCompleteMultiagent20261001Object() throws Exception {
        AgentRef agent = new AgentRef()
                .type(AgentRefType.AGENT)
                .id("agent-example")
                .version(1);
        AgentRef self = new AgentRef().type(AgentRefType.SELF);
        MultiagentPatch multiagent = UpdateAgentPatch_shim.multiagent("multiagent_20261001")
                .subagents(UpdateAgentPatch_shim.subagents("enabled")
                        .inlineAgents(UpdateAgentPatch_shim.inlineAgents("enabled"))
                        .predefinedAgents(Arrays.asList(agent, self)));
        UpdateAgentPatch_shim.Request request = UpdateAgentPatch_shim.request(7)
                .multiagent(multiagent);
        String wire = mapper.writeValueAsString(request);

        UpdateAgentPatch_shim.Request decoded = mapper.readValue(wire, UpdateAgentPatch_shim.Request.class);
        String encoded = mapper.writeValueAsString(decoded);

        assertEquals(mapper.readTree(wire), mapper.readTree(encoded));
        UpdateAgentPatch_shim.Multiagent decodedMultiagent =
                (UpdateAgentPatch_shim.Multiagent) decoded.getMultiagent();
        assertTrue(decodedMultiagent.hasSubagents());
        assertFalse(decodedMultiagent.subagentsExplicitNull());
        UpdateAgentPatch_shim.Subagents subagents =
                (UpdateAgentPatch_shim.Subagents) decodedMultiagent.getSubagentsValue();
        assertTrue(subagents.hasInlineAgents());
        assertTrue(subagents.hasPredefinedAgents());
        assertFalse(subagents.predefinedAgentsExplicitNull());
        assertEquals(Arrays.asList(agent, self), subagents.getPredefinedAgentsValue());
    }

    @Test
    public void serializesEmptyArraysFromGeneratedModels() throws Exception {
        MultiagentPatch multiagent = new MultiagentPatch()
                .type(MultiagentType.COORDINATOR)
                .agents(Collections.emptyList());
        JsonNode multiagentNode = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.request(1).multiagent(multiagent)));
        assertTrue(multiagentNode.get("multiagent").get("agents").isArray());
        assertEquals(0, multiagentNode.get("multiagent").get("agents").size());

        MultiagentSubagentsConfig subagents = new MultiagentSubagentsConfig()
                .type(MultiagentFeatureType.ENABLED)
                .predefinedAgents(Collections.emptyList());
        JsonNode subagentsNode = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.request(1).multiagent(
                        new MultiagentPatch()
                                .type(MultiagentType.MULTIAGENT_20261001)
                                .subagents(subagents))));
        assertTrue(subagentsNode.get("multiagent").get("subagents").get("predefined_agents").isArray());
        assertEquals(
                0,
                subagentsNode.get("multiagent").get("subagents").get("predefined_agents").size());
    }

    @Test
    public void keepsShimStateInSyncWithInheritedSetters() throws Exception {
        AgentRef agent = new AgentRef()
                .type(AgentRefType.AGENT)
                .id("agent-example")
                .version(1);
        MultiagentSubagentsConfig subagents = UpdateAgentPatch_shim.subagents("enabled");
        subagents.setType(MultiagentFeatureType.DISABLED);
        subagents.setInlineAgents(UpdateAgentPatch_shim.inlineAgents("enabled"));
        subagents.setPredefinedAgents(Collections.singletonList(agent));

        MultiagentPatch multiagent = UpdateAgentPatch_shim.multiagent("coordinator");
        multiagent.setType(MultiagentType.MULTIAGENT_20261001);
        multiagent.setSubagents(subagents);
        multiagent.addAgentsItem(agent);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.request(1).multiagent(multiagent))).get("multiagent");
        assertEquals("multiagent_20261001", node.get("type").asText());
        assertEquals(1, node.get("agents").size());
        assertEquals("disabled", node.get("subagents").get("type").asText());
        assertEquals("enabled", node.get("subagents").get("inline_agents").get("type").asText());
        assertEquals("agent-example", node.get("subagents").get("predefined_agents").get(0).get("id").asText());
    }

    @Test
    public void normalizesTypeStringsToContractValues() throws Exception {
        MultiagentPatch multiagent = UpdateAgentPatch_shim.multiagent("Multiagent_20261001")
                .subagents(UpdateAgentPatch_shim.subagents("ENABLED")
                        .inlineAgents(UpdateAgentPatch_shim.inlineAgents("DISABLED")));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(
                UpdateAgentPatch_shim.request(1).multiagent(multiagent))).get("multiagent");
        assertEquals("multiagent_20261001", node.get("type").asText());
        assertEquals("enabled", node.get("subagents").get("type").asText());
        assertEquals("disabled", node.get("subagents").get("inline_agents").get("type").asText());
    }

    @Test
    public void rejectsNullAgents() {
        String wire = "{\"version\":1,\"multiagent\":{\"type\":\"coordinator\",\"agents\":null}}";
        assertThrows(JsonMappingException.class, () -> mapper.readValue(wire, UpdateAgentPatch_shim.Request.class));
        assertThrows(IllegalArgumentException.class,
                () -> UpdateAgentPatch_shim.multiagent("coordinator").agents(null));
    }

    @Test
    public void rejectsNullNestedPatchFields() {
        UpdateAgentPatch_shim.Multiagent multiagent =
                UpdateAgentPatch_shim.multiagent("multiagent_20261001");
        UpdateAgentPatch_shim.Subagents subagents = UpdateAgentPatch_shim.subagents("enabled");

        assertThrows(IllegalArgumentException.class, () -> multiagent.subagents(null));
        assertThrows(IllegalArgumentException.class, () -> subagents.inlineAgents(null));
        assertThrows(IllegalArgumentException.class, () -> subagents.predefinedAgents(null));
    }

    @Test
    public void wrapsUnknownTypeValuesInJsonMappingException() {
        assertThrows(JsonMappingException.class, () -> mapper.readValue(
                "{\"version\":1,\"multiagent\":{\"type\":\"unknown\"}}",
                UpdateAgentPatch_shim.Request.class));
        assertThrows(JsonMappingException.class, () -> mapper.readValue(
                "{\"version\":1,\"multiagent\":{\"type\":\"multiagent_20261001\","
                        + "\"subagents\":{\"type\":\"unknown\"}}}",
                UpdateAgentPatch_shim.Request.class));
        assertThrows(JsonMappingException.class, () -> mapper.readValue(
                "{\"version\":1,\"multiagent\":{\"type\":\"multiagent_20261001\","
                        + "\"subagents\":{\"type\":\"enabled\","
                        + "\"inline_agents\":{\"type\":\"unknown\"}}}}",
                UpdateAgentPatch_shim.Request.class));
    }

    @Test
    public void rejectsMissingRequiredTypesDuringSerialization() {
        assertThrows(JsonMappingException.class,
                () -> mapper.writeValueAsString(new UpdateAgentPatch_shim.Multiagent()));
        assertThrows(JsonMappingException.class,
                () -> mapper.writeValueAsString(new UpdateAgentPatch_shim.Subagents()));
        assertThrows(JsonMappingException.class,
                () -> mapper.writeValueAsString(new UpdateAgentPatch_shim.InlineAgents()));
    }
}
