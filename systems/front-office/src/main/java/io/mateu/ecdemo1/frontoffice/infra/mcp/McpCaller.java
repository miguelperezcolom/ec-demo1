package io.mateu.ecdemo1.frontoffice.infra.mcp;

/**
 * Who a tool call is for, and in which turn of the conversation it came. ia-agent forwards the token of
 * the person chatting to every MCP server, and opens a fresh MCP session for every prompt — so the
 * session a call came in on tells one turn of the person from the next.
 */
public interface McpCaller {

  /** The person at the desk the agent acts for, from their token; null if the call carried none. */
  String person();

  /** The MCP session of this call — one per prompt of the person; null if unknown. */
  String turn();
}
