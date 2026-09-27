package io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo;

import java.util.List;

/**
 * One operation of an API, offered to an agent as a tool.
 *
 * <p>{@code operation} is how the serving side finds it again — for REST, the method and path the
 * OpenAPI document declared, e.g. {@code GET /bookings/{id}}. It is not shown to the model.
 *
 * <p>{@code description} is, and it is the load-bearing field on this record. It is what a model
 * reads when deciding whether to call this tool at all, so a vague one produces a tool that is
 * never called — exactly the lesson the RAG catalogue already carries about a source's description.
 * An operationId copied out of a spec is not a description.
 *
 * <p>{@code requiredRoles} are checked where the call is made, not here. An empty list means the
 * tool is offered to anyone the agent is offered to; a non-empty one narrows it further. Recording
 * them on the catalogue entry is what lets one API be exposed with different reach to different
 * agents without cataloguing it twice.
 */
public record ExposedTool(String operation, String toolName, String description,
                          List<String> requiredRoles) {

    public ExposedTool {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("A tool must name the operation it calls");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("A tool needs a name: " + operation);
        }
        if (description == null || description.isBlank()) {
            // Refused rather than defaulted to the operation id. A tool the model never calls is
            // indistinguishable from one that does not exist, and far harder to notice.
            throw new IllegalArgumentException(
                    "A tool needs a description — it is what the model reads to decide whether to "
                    + "call it: " + toolName);
        }
        requiredRoles = requiredRoles == null ? List.of() : List.copyOf(requiredRoles);
    }
}
