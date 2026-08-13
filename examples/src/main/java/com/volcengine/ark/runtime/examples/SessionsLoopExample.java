package com.volcengine.ark.runtime.examples;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volcengine.ark.runtime.models.agent.Agent;
import com.volcengine.ark.runtime.models.agent.CreateAgentRequest;
import com.volcengine.ark.runtime.models.agent.ModelConfig;
import com.volcengine.ark.runtime.models.agent.ToolItem;
import com.volcengine.ark.runtime.models.environment.CreateEnvironmentRequest;
import com.volcengine.ark.runtime.models.environment.EnvConfig;
import com.volcengine.ark.runtime.models.environment.EnvConfigType;
import com.volcengine.ark.runtime.models.environment.Environment;
import com.volcengine.ark.runtime.models.environment.NetworkingConfig;
import com.volcengine.ark.runtime.models.environment.NetworkingType;
import com.volcengine.ark.runtime.models.session.AgentIdentifier;
import com.volcengine.ark.runtime.models.session.CreateSessionRequest;
import com.volcengine.ark.runtime.models.session.IncomingEventParams;
import com.volcengine.ark.runtime.models.session.IncomingEventParamsType;
import com.volcengine.ark.runtime.models.session.SendSessionEventsRequest;
import com.volcengine.ark.runtime.models.session.Session;
import com.volcengine.ark.runtime.models.session.TurnInputContent;
import com.volcengine.ark.runtime.models.session.TurnInputContentType;
import com.volcengine.ark.runtime.models.session.TurnInputTextContent;
import com.volcengine.ark.runtime.models.session.UserMessageEventParams;
import com.volcengine.ark.runtime.service.ArkService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Managed Agents — end-to-end agent-loop example.
 *
 * <p>Create Agent + Environment + Session, open the SSE stream, send a text
 * prompt, drain events until the loop settles (session.status_idle /
 * terminated / error), and print the assistant's response.
 *
 * <p>Environment:
 * <pre>
 *   export ARK_API_KEY=...
 *   export ARK_MODEL_ID=doubao-seed-1-8-251228
 * </pre>
 */
public class SessionsLoopExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> STOP_TYPES = new HashSet<>(Arrays.asList(
        "session.status_idle", "session.status_terminated", "session.error"));

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("ARK_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("set ARK_API_KEY");
        }
        String modelId = System.getenv().getOrDefault("ARK_MODEL_ID", "${YOUR_MODEL_ID}");

        ArkService service = ArkService.builder().apiKey(apiKey).build();

        // 1. Agent
        CreateAgentRequest agReq = new CreateAgentRequest();
        agReq.setName("example-loop-agent-" + System.nanoTime());
        ModelConfig model = new ModelConfig();
        model.setId(modelId);
        agReq.setModel(model);
        agReq.setSystem("You are a helpful assistant. Answer the user's question briefly.");
        ToolItem toolset = new ToolItem();
        toolset.setType("agent_toolset_20260401");
        agReq.setTools(Arrays.asList(toolset));
        Agent ag = service.createAgent(agReq);
        System.out.printf("agent:      id=%s%n", ag.getId());

        // 2. Environment
        CreateEnvironmentRequest envReq = new CreateEnvironmentRequest();
        envReq.setName("example-loop-env-" + System.nanoTime());
        EnvConfig cfg = new EnvConfig();
        cfg.setType(EnvConfigType.CLOUD);
        NetworkingConfig net = new NetworkingConfig();
        net.setType(NetworkingType.UNRESTRICTED);
        cfg.setNetworking(net);
        envReq.setConfig(cfg);
        Environment env = service.createEnvironment(envReq);
        System.out.printf("env:        id=%s%n", env.getId());

        // 3. Session
        CreateSessionRequest sessReq = new CreateSessionRequest();
        sessReq.setAgent(new AgentIdentifier(ag.getId()));
        sessReq.setEnvironmentId(env.getId());
        sessReq.setTitle("ark-runtime-java example loop");
        Session sess = service.createSession(sessReq);
        System.out.printf("session:    id=%s%n%n", sess.getId());

        StringBuilder assistantOut = new StringBuilder();
        try {
            // 4. Open SSE first, then fire the user.message from a background
            //    thread so we don't race and miss the earliest events.
            Thread sender = new Thread(() -> {
                try {
                    Thread.sleep(500);
                    TurnInputTextContent textBlock = new TurnInputTextContent();
                    textBlock.setType(TurnInputContentType.TEXT);
                    textBlock.setText("What's the tallest mountain? One sentence.");
                    UserMessageEventParams msg = new UserMessageEventParams();
                    msg.setType(IncomingEventParamsType.USER_MESSAGE);
                    msg.setContent(Arrays.<TurnInputContent>asList(textBlock));
                    SendSessionEventsRequest req = new SendSessionEventsRequest();
                    req.setEvents(Arrays.<IncomingEventParams>asList(msg));
                    service.sendSessionEvents(sess.getId(), req);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    System.err.println("send user.message: " + t);
                }
            }, "example-sender");
            sender.setDaemon(true);
            sender.start();

            // 5. Drain the stream.
            Call<ResponseBody> call = service.streamSessionEvents(sess.getId());
            Response<ResponseBody> resp = call.execute();
            try (ResponseBody body = resp.body()) {
                if (!resp.isSuccessful() || body == null) {
                    throw new RuntimeException("open stream failed: HTTP " + resp.code());
                }
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder dataBuf = new StringBuilder();
                    boolean done = false;
                    while (!done && (line = br.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (dataBuf.length() > 0) {
                                Map<String, Object> ev = parseFrame(dataBuf.toString());
                                dataBuf.setLength(0);
                                if (ev != null) {
                                    String type = String.valueOf(ev.get("type"));
                                    System.out.printf("[EVT] %s%n", type);
                                    if ("agent.message".equals(type)) {
                                        Object content = ev.get("content");
                                        if (content instanceof List) {
                                            for (Object block : (List<?>) content) {
                                                if (block instanceof Map) {
                                                    Object text = ((Map<?, ?>) block).get("text");
                                                    if (text != null) {
                                                        assistantOut.append(text);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (STOP_TYPES.contains(type)) {
                                        done = true;
                                    }
                                }
                            }
                        } else if (line.startsWith("data:")) {
                            dataBuf.append(line.substring(5).trim());
                        }
                    }
                }
            }
        } finally {
            try { service.deleteSession(sess.getId()); } catch (Exception ignore) {}
            try { service.deleteEnvironment(env.getId()); } catch (Exception ignore) {}
            try { service.deleteAgent(ag.getId()); } catch (Exception ignore) {}
            service.shutdownExecutor();
        }

        String joined = assistantOut.toString().trim();
        if (!joined.isEmpty()) {
            System.out.printf("%nassistant → %s%n", joined);
        } else {
            System.out.println("\n(no assistant text captured — check the [EVT] trace above)");
        }
    }

    private static Map<String, Object> parseFrame(String data) {
        try {
            return MAPPER.readValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
