package io.mateu.ecdemo1.apimcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The pod that turns catalogued APIs into MCP servers agents can be given.
 *
 * <p>It owns no data and no screens. The catalogue lives in {@code ia-control-plane}, which holds
 * the OFFER — which operations of which API, under which names, described how — and this service
 * holds the TRANSLATION: reading the spec properly, building each tool's input schema, and making
 * the call. The split is the reason the control plane is not on the path every tool call travels,
 * and the reason it carries no OpenAPI parser.
 *
 * <p>One endpoint per catalogue entry, at {@code /{apiMcpId}/sse}. An operator then catalogues
 * that URL as an ordinary MCP server, and an agent composes it exactly like it composes booking or
 * the orchestrator — nothing on the agent's side has to learn what an API-backed entry is.
 */
@SpringBootApplication
@EnableScheduling
public class ApiMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiMcpApplication.class, args);
    }
}
