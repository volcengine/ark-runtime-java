# Examples

Runnable examples for the `ark-runtime-java` SDK. Each class has a `main` that
reads `ARK_API_KEY` from env:

```bash
export ARK_API_KEY=...
cd examples
mvn -q -DskipTests install
mvn -q exec:java -Dexec.mainClass=com.volcengine.ark.runtime.examples.CreateResponseExample
```

First-time setup: `mvn -q -DskipTests install` at the parent repo root so
the examples module can resolve the `ark-runtime` jar from the local Maven
cache. The examples module is **not** wired into the parent aggregator
pom on purpose — install the SDK first, then build the examples separately.

| Class | What it shows |
|---|---|
| `CreateResponseExample` | Create a response, stream the output |
| `ResponseOperationsExample` | get / delete / list input items |
| `KnowledgeSearchCreateResponsesExample` | Create with knowledge search tool |
| `DoubaoAppCreateResponsesExample` | Create with Doubao app tools |
| `MultiModalEmbeddingsExample` | POST /embeddings/multimodal |
| `ContentGenerationTaskExample` | full lifecycle on POST /contents/generations/tasks (create / poll / list / delete) |
| `ImageGenerationExample` | POST /images/generations — Seedream T2I, Seededit edit-from-image, sequential image generation |
| `AgentsLifecycleExample` | Managed-Agents: Agent lifecycle — Create/Get/List/Update/ListVersions/Delete |
| `EnvironmentsLifecycleExample` | Managed-Agents: Environment lifecycle — Create/Get/List/Update/Delete (cloud + unrestricted networking) |
| `SessionsLoopExample` | Managed-Agents: end-to-end agent loop — Agent + Env + Session, send user.message, stream events until idle |
| `MemoryStoresLifecycleExample` | Managed-Agents: MemoryStore + nested Memory CRUD |

The Managed-Agents examples additionally accept `ARK_MODEL_ID` for the model id (falls back to a `${YOUR_MODEL_ID}` placeholder that will 400 at runtime).

Only currently-implemented APIs have runnable examples. See the API Coverage
table in the top-level README for the roadmap.

## Known limitations

Responses API requests use a union `ResponsesInput` (string-or-list) and a
union `MessageContent` (string-or-list). The OpenAPI-generated stubs for
these unions are empty placeholders today, so the ported responses examples
construct the request shape without populating the input body. Once codegen
emits real setters for the union variants, the examples should be updated
to pass actual prompts through `ResponsesInput` / `MessageContent`.
