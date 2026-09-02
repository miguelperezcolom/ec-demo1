package io.mateu.ecdemo1.iacp.application.out.query.dto;

/**
 * What a listing of API-backed MCP servers shows.
 *
 * <p>{@code tools} is a count rather than the list: an entry with none is catalogued but not yet
 * offering anything, and that is the single most useful thing to see while scanning.
 */
public record ApiMcpRow(String id, String name, String kind, String baseUrl,
                        int tools, String credential, String status) {
}
