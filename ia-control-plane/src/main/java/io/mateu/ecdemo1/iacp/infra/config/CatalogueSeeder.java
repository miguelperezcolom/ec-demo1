package io.mateu.ecdemo1.iacp.infra.config;

import io.mateu.ecdemo1.iacp.application.out.query.AgentQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.LlmQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.McpQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.RagQueryService;
import io.mateu.ecdemo1.iacp.application.usecases.agent.create.CreateAgentCommand;
import io.mateu.ecdemo1.iacp.application.usecases.agent.create.CreateAgentUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.llm.create.CreateLlmCommand;
import io.mateu.ecdemo1.iacp.application.usecases.llm.create.CreateLlmUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.mcp.create.CreateMcpCommand;
import io.mateu.ecdemo1.iacp.application.usecases.mcp.create.CreateMcpUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.rag.create.CreateRagCommand;
import io.mateu.ecdemo1.iacp.application.usecases.rag.create.CreateRagUseCase;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagKind;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmProvider;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpTransport;
import io.mateu.ecdemo1.iacp.infra.out.crypto.AesGcmSecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts this deployment's own agent in the catalogues, once, on a control plane that has never had
 * anything in it.
 *
 * <p>Without this, moving the agent's configuration out of a properties file would have made a
 * fresh {@code deploy.sh} produce a chat panel that does not work until somebody opens the control
 * console and types four things in. The point of a control plane is to be able to change the
 * configuration, not to have to invent it — so the deployment still comes up working, and what it
 * comes up with is visible and editable instead of compiled in.
 *
 * <p><strong>Only when all four catalogues are empty.</strong> Not "create if missing" per entry:
 * that would resurrect an MCP server someone deliberately deleted on the next restart, which is
 * the kind of helpfulness that is impossible to argue with and impossible to switch off. One
 * decision, taken once, on a control plane that is demonstrably brand new.
 *
 * <p>Consequently it is also not an upgrade path. A control plane that already holds anything is
 * left exactly as it is.
 */
@Configuration
public class CatalogueSeeder {

    private static final Logger log = LoggerFactory.getLogger(CatalogueSeeder.class);

    @Bean
    ApplicationRunner seedCatalogues(
            LlmQueryService llms, McpQueryService mcps, RagQueryService rags, AgentQueryService agents,
            CreateLlmUseCase createLlm, CreateMcpUseCase createMcp, CreateRagUseCase createRag,
            CreateAgentUseCase createAgent,
            AesGcmSecretCipher cipher,
            @Value("${cp.seed.agent-id:console-agent}") String agentId,
            @Value("${cp.seed.anthropic-api-key:}") String anthropicApiKey,
            @Value("${cp.seed.orchestrator-url:}") String orchestratorUrl,
            @Value("${cp.seed.forms-url:}") String formsUrl,
            @Value("${cp.seed.booking-url:}") String bookingUrl,
            @Value("${cp.seed.rag-url:}") String ragUrl) {
        return args -> {
            if (llms.count() > 0 || mcps.count() > 0 || rags.count() > 0 || agents.count() > 0) {
                log.info("Catalogues already hold something — nothing seeded.");
                return;
            }
            if (!cipher.isConfigured()) {
                // Seeding an LLM would mean encrypting a credential, and there is nothing to
                // encrypt with. Leaving the catalogues empty is better than seeding half of the
                // agent and then never seeding the rest, because this only ever runs on empty.
                log.error("Catalogues are empty and CP_CRYPTO_KEY is not set, so nothing was "
                        + "seeded. Set it and restart, or create the entries by hand.");
                return;
            }

            log.info("Catalogues are empty — seeding this deployment's own agent.");

            createLlm.handle(new CreateLlmCommand("anthropic", "Anthropic",
                    LlmProvider.ANTHROPIC, "claude-sonnet-4-5", null, 0.1, 4096,
                    // Empty is fine and expected: the key is bought, not generated, so a
                    // deployment without one gets a catalogued model that says "missing" until
                    // someone fills it in from the console. That is the same failure as before,
                    // now with a place to fix it.
                    anthropicApiKey));

            var mcpIds = new ArrayList<String>();
            seedMcp(createMcp, mcpIds, "orchestrator", "Orchestrator", orchestratorUrl,
                    "The workflow engine: processes, definitions, step executions.");
            seedMcp(createMcp, mcpIds, "forms", "Forms engine", formsUrl,
                    "Form definitions and the human tasks waiting to be done.");
            seedMcp(createMcp, mcpIds, "booking", "Booking service", bookingUrl,
                    "Bookings: create, list and change the status of one.");

            // A retrieval source, and an embedding model for it. Both are seeded with no
            // credential: Anthropic has no embeddings API, so the model here has to be an
            // OpenAI-shaped one and this deployment's Anthropic key cannot pay for it. It is
            // catalogued so the wiring is visible and one field away from working.
            var ragIds = new ArrayList<String>();
            if (ragUrl != null && !ragUrl.isBlank()) {
                createLlm.handle(new CreateLlmCommand("embeddings", "Embeddings",
                        LlmProvider.OPENAI, "text-embedding-3-small", null, 0.0, 256, null));
                createRag.handle(new CreateRagCommand("handbook", "Ops handbook",
                        RagKind.PGVECTOR, ragUrl, "handbook_vectors", "embeddings", 5,
                        "Notes about running this deployment: what the topics are for, what the "
                                + "known gaps are, and what to check when a process is stuck."));
                ragIds.add("handbook");
            }

            // The RAG source is NOT put on the agent. A tool that always answers "nothing found"
            // is worse than no tool: it teaches the model that the source is useless and costs a
            // round trip per prompt to prove it. Ingest something first — Content → Ingest text
            // on the source — then add it to the agent.
            createAgent.handle(new CreateAgentCommand(agentId, "Console agent",
                    defaultPrompt(), "anthropic", mcpIds, List.of(),
                    "The agent behind the demo console's chat panel. Seeded on first start."));

            log.info("Seeded LLM 'anthropic'{}, {} MCP server(s), {} RAG source(s) and agent '{}'. "
                            + "The RAG source is catalogued but not on the agent — ingest into it "
                            + "and add it once it holds something.",
                    anthropicApiKey == null || anthropicApiKey.isBlank() ? " (no credential yet)" : "",
                    mcpIds.size(), ragIds.size(), agentId);
        };
    }

    private void seedMcp(CreateMcpUseCase createMcp, List<String> into,
                         String id, String name, String url, String description) {
        if (url == null || url.isBlank()) {
            // A URL that was not passed in is a service that is not in this deployment. Better an
            // agent with fewer servers than one pointed at a host that does not resolve.
            log.info("No URL for MCP server '{}' — not seeded.", id);
            return;
        }
        createMcp.handle(new CreateMcpCommand(id, name, url, McpTransport.SSE, 60L, description));
        into.add(id);
    }

    private String defaultPrompt() {
        try {
            return new ClassPathResource("default-agent-prompt.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            // The seeded agent is editable, so a fallback beats refusing to seed. It is short on
            // purpose: anything longer pretending to be the real prompt would be worse.
            log.warn("Could not read default-agent-prompt.txt, seeding a minimal prompt", e);
            return "Eres un asistente. Responde únicamente llamando a las herramientas disponibles, "
                    + "y si una falla, di exactamente qué falló en lugar de inventar la respuesta.";
        }
    }
}
