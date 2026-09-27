package io.mateu.ecdemo1.iacp.infra.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.mateu.ecdemo1.iacp.application.out.query.AgentQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.LlmQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.McpQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.QueryService;
import io.mateu.ecdemo1.iacp.application.out.query.RagQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

import java.util.Map;

/**
 * What Grafana can ask about the catalogues.
 *
 * <p>Two gauges per catalogue — how many entries, and how many are enabled — because the gap
 * between them is the number an operator actually wants: a disabled MCP server is invisible in an
 * agent's answers and looks like nothing at all from outside. A dashboard that shows six servers
 * catalogued and four enabled asks the right question on its own.
 *
 * <p>Gauges rather than counters, and read through the query services on scrape, so they are
 * correct after a restart without anything having to replay. Each scrape is four {@code count()}
 * queries against a table with tens of rows — cheap enough not to cache, and caching would
 * reintroduce exactly the staleness this is meant to show.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CatalogueMetrics {

    final MeterRegistry registry;
    final LlmQueryService llms;
    final McpQueryService mcps;
    final RagQueryService rags;
    final AgentQueryService agents;

    @PostConstruct
    void register() {
        Map<String, QueryService<?, ?, String>> catalogues = Map.of(
                "llm", llms, "mcp", mcps, "rag", rags, "agent", agents);
        catalogues.forEach((name, service) -> {
            Gauge.builder("ia_control_plane_catalogue_entries", service, QueryService::count)
                    .description("Entries in a catalogue, enabled or not")
                    .tag("catalogue", name)
                    .register(registry);
            Gauge.builder("ia_control_plane_catalogue_enabled", service, QueryService::countEnabled)
                    .description("Entries in a catalogue that an agent may actually be served")
                    .tag("catalogue", name)
                    .register(registry);
        });
        log.info("Catalogue gauges registered for {}", catalogues.keySet());
    }
}
