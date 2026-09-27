package io.mateu.ecdemo1.iacp.application.usecases.apimcp.exposetools;

import java.util.List;

/** The whole offer, because it is one decision — see ApiMcp.exposeExactly. */
public record ExposeApiToolsCommand(String id, List<Tool> tools) {

    public record Tool(String operation, String toolName, String description,
                       List<String> requiredRoles) {
    }
}
