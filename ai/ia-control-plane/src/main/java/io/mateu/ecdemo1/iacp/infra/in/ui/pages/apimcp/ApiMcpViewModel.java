package io.mateu.ecdemo1.iacp.infra.in.ui.pages.apimcp;

import io.mateu.ecdemo1.iacp.application.out.query.dto.ApiMcpDto;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.create.CreateApiMcpCommand;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.create.CreateApiMcpUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.exposetools.ExposeApiToolsCommand;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.exposetools.ExposeApiToolsUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.importoperations.ImportApiOperationsUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.replacecredential.ReplaceApiMcpCredentialCommand;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.replacecredential.ReplaceApiMcpCredentialUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.update.UpdateApiMcpCommand;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.update.UpdateApiMcpUseCase;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiKind;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.MasterDetail;
import io.mateu.uidl.annotations.Multiline;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turning an API this deployment already talks to into tools an agent can be given.
 *
 * <p>Three acts, and they are separate on purpose. <b>Import</b> asks the API what it can do and
 * fills the table below, replacing nothing that is already there but what it re-imports. <b>Save</b>
 * stores the offer. <b>Replace credential</b> is its own action, so that saving the form can never
 * touch the stored secret — the same arrangement the LLM catalogue uses, and for the same reason.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class ApiMcpViewModel implements Identifiable {

    @Section("API")
    @ReadOnly
    @HiddenInCreate
    String id;

    @HiddenInList
    @Help("Referenced by agents. Cannot be changed once created.")
    String newId;

    @NotEmpty
    String name;

    @NotNull
    @Help("REST reads an OpenAPI document. SOAP can be catalogued, but importing its operations "
            + "is refused until a WSDL reader exists.")
    ApiKind kind;

    @NotEmpty
    @Help("Where calls go — e.g. https://api.example.com/v1. The spec's own server list is "
            + "advisory; this is the one that is used.")
    String baseUrl;

    @NotEmpty
    @HiddenInList
    @Help("Where the OpenAPI document is fetched from when operations are imported")
    String specUrl;

    @Multiline
    @HiddenInList
    @Help("For whoever reads this catalogue. What a MODEL reads is each tool's own description, "
            + "below.")
    String description;

    @Section("Credential")
    @ReadOnly
    @Help("Whether a secret is stored. The secret itself is never shown, here or anywhere else.")
    String credentialStatus;

    @HiddenInList
    @Stereotype(FieldStereotype.password)
    @Help("Leave empty to keep the stored secret. Type a new one and use 'Replace credential' — "
            + "saving the form does not touch it.")
    String newSecret;

    @Section("Tools")
    @HiddenInList
    @ReadOnly
    @Help("Result of the last 'Import operations' in this session. Not stored.")
    String lastImport;

    @HiddenInList
    @Colspan(2)
    @MasterDetail(minHeightWhenDetailVisible = "22rem;")
    @Help("What this API offers an agent. Import fills it; what stays here is what is offered.")
    List<ExposedToolViewModel> tools;

    @Section("Status")
    boolean enabled;

    final CreateApiMcpUseCase createApiMcpUseCase;
    final UpdateApiMcpUseCase updateApiMcpUseCase;
    final ReplaceApiMcpCredentialUseCase replaceApiMcpCredentialUseCase;
    final ImportApiOperationsUseCase importApiOperationsUseCase;
    final ExposeApiToolsUseCase exposeApiToolsUseCase;

    public String create(HttpRequest httpRequest) {
        return createApiMcpUseCase.handle(
                new CreateApiMcpCommand(newId, name, kind, baseUrl, specUrl, description));
    }

    /**
     * Saves the entry and the offer together, because to the person on this screen they are one
     * edit. They are two use cases underneath, since the offer has invariants of its own.
     */
    public void save(HttpRequest httpRequest) {
        updateApiMcpUseCase.handle(new UpdateApiMcpCommand(id, name, kind, baseUrl, specUrl,
                description, enabled));
        exposeApiToolsUseCase.handle(new ExposeApiToolsCommand(id,
                (tools == null ? List.<ExposedToolViewModel>of() : tools).stream()
                        .map(t -> new ExposeApiToolsCommand.Tool(t.operation(), t.toolName(),
                                t.description(), roles(t.requiredRoles())))
                        .toList()));
    }

    /**
     * Asks the API what it can do, and adds what is not already offered.
     *
     * <p>Adds rather than replaces. Re-importing after a spec changes is the normal case, and the
     * descriptions in this table were written by a person — wiping them because the spec grew an
     * endpoint would be losing the only part of this that cannot be regenerated.
     */
    @Action(idempotent = true)
    public String importOperations(HttpRequest httpRequest) {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Save this API before importing its operations");
        }
        var operations = importApiOperationsUseCase.handle(id);
        var current = tools == null ? new ArrayList<ExposedToolViewModel>() : new ArrayList<>(tools);
        var known = current.stream().map(ExposedToolViewModel::operation).toList();
        var added = 0;
        for (var operation : operations) {
            if (known.contains(operation.reference())) {
                continue;
            }
            current.add(new ExposedToolViewModel(operation.reference(),
                    suggestedName(operation.operationId(), operation.reference()),
                    // The spec's summary is offered as a starting point and never as the
                    // description: it is written for someone reading the spec, not for a model
                    // deciding whether to call the thing. Left blank when there is none, because a
                    // blank field is a prompt and a bad description is not.
                    operation.summary(),
                    null));
            added++;
        }
        tools = current;
        lastImport = operations.size() + " operation(s) declared, " + added + " new";
        return lastImport;
    }

    @Action(confirmationRequired = true,
            confirmationTitle = "Replace this API's credential?",
            confirmationMessage = "The stored secret is overwritten and cannot be recovered. "
                    + "An empty field clears it, which leaves the API catalogued and unable to "
                    + "answer anything that needs one.")
    public String replaceCredential(HttpRequest httpRequest) {
        replaceApiMcpCredentialUseCase.handle(new ReplaceApiMcpCredentialCommand(id, newSecret));
        var cleared = newSecret == null || newSecret.isBlank();
        newSecret = null;
        credentialStatus = cleared ? "missing" : "set";
        return cleared ? "Credential cleared" : "Credential replaced";
    }

    @Override
    public String id() {
        return id;
    }

    public ApiMcpViewModel load(ApiMcpDto dto) {
        id = dto.id();
        newId = dto.id();
        name = dto.name();
        kind = dto.kind();
        baseUrl = dto.baseUrl();
        specUrl = dto.specUrl();
        description = dto.description();
        credentialStatus = dto.credentialSet() ? "set" : "missing";
        newSecret = null;
        lastImport = null;
        tools = dto.tools().stream()
                .map(t -> new ExposedToolViewModel(t.operation(), t.toolName(), t.description(),
                        String.join(", ", t.requiredRoles())))
                .toList();
        enabled = dto.enabled();
        return this;
    }

    private static List<String> roles(String csv) {
        return csv == null || csv.isBlank() ? List.of()
                : Arrays.stream(csv.split(",")).map(String::trim).filter(r -> !r.isEmpty()).toList();
    }

    /** A name to start from. The operator is expected to improve it; the spec rarely names well. */
    private static String suggestedName(String operationId, String reference) {
        return operationId != null && !operationId.isBlank()
                ? operationId
                : reference.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    @Override
    public String toString() {
        return id != null ? name : "New API MCP server";
    }
}
