package io.mateu.ecdemo1.iacp.infra.in.ui;

import io.mateu.ecdemo1.iacp.infra.in.ui.pages.agent.AgentCrudOrchestrator;
import io.mateu.ecdemo1.iacp.infra.in.ui.pages.llm.LlmCrudOrchestrator;
import io.mateu.ecdemo1.iacp.infra.in.ui.pages.mcp.McpCrudOrchestrator;
import io.mateu.ecdemo1.iacp.infra.in.ui.pages.rag.RagCrudOrchestrator;
import io.mateu.uidl.annotations.Menu;

/**
 * Agents first, then the three catalogues an agent is composed from.
 *
 * <p>The order is the reading order: an agent is the thing anyone came here to change, and the
 * other three are what it is made of. Alphabetical would put the parts before the whole.
 */
public class ControlPlaneMenu {

    @Menu
    AgentCrudOrchestrator agents;

    @Menu
    LlmCrudOrchestrator llms;

    @Menu
    McpCrudOrchestrator mcpServers;

    @Menu
    RagCrudOrchestrator ragSources;

}
