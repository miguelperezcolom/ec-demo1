package io.mateu.ecdemo1.iacp.infra.in.ui.pages.apimcp;

import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Multiline;
import io.mateu.uidl.annotations.ReadOnly;

/**
 * One row of the offer: an operation of the API, as it will be presented to a model.
 *
 * <p>{@code operation} is read-only because it is the API's, not the operator's — it comes from the
 * imported spec and editing it here would mean naming an operation that does not exist, which
 * nothing would notice until a tool call failed at run time.
 */
public class ExposedToolViewModel {

    @ReadOnly
    @Help("The operation this tool calls, as the spec declares it")
    String operation;

    @Help("What the model calls this tool. Two tools of one API cannot share a name")
    String toolName;

    @Multiline
    @Help("What the model reads to decide whether to call it. This is the field that decides "
            + "whether the tool is ever used — an operationId copied from the spec is not a "
            + "description")
    String description;

    @Help("Comma-separated. Empty means anyone the agent is offered to. Checked where the call is "
            + "made, not here")
    String requiredRoles;

    public ExposedToolViewModel() {
    }

    public ExposedToolViewModel(String operation, String toolName, String description,
                                String requiredRoles) {
        this.operation = operation;
        this.toolName = toolName;
        this.description = description;
        this.requiredRoles = requiredRoles;
    }

    public String operation() { return operation; }
    public String toolName() { return toolName; }
    public String description() { return description; }
    public String requiredRoles() { return requiredRoles; }

    @Override
    public String toString() {
        return toolName != null && !toolName.isBlank() ? toolName : operation;
    }
}
