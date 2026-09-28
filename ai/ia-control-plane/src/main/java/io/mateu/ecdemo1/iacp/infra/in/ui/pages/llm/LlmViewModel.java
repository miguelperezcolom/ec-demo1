package io.mateu.ecdemo1.iacp.infra.in.ui.pages.llm;

import io.mateu.ecdemo1.uicommons.crud.CatalogueEditor;
import io.mateu.ecdemo1.iacp.application.out.query.dto.LlmDto;
import io.mateu.ecdemo1.iacp.application.usecases.llm.create.CreateLlmCommand;
import io.mateu.ecdemo1.iacp.application.usecases.llm.create.CreateLlmUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.llm.replacecredential.ReplaceLlmCredentialCommand;
import io.mateu.ecdemo1.iacp.application.usecases.llm.replacecredential.ReplaceLlmCredentialUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.llm.update.UpdateLlmCommand;
import io.mateu.ecdemo1.iacp.application.usecases.llm.update.UpdateLlmUseCase;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmProvider;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The editor for a model and its key.
 *
 * <p><strong>The credential is write-only and that shape is load-bearing.</strong>
 * {@link #credentialStatus} is what an operator sees — the word "set" or "missing", never the key
 * — and {@link #newApiKey} is always blank on load. Saving the form does not touch the stored key
 * at all: {@code UpdateLlmCommand} has no field for one. Replacing it is a separate action, which
 * is what stops the ordinary "open, change the temperature, save" from wiping a working key
 * because a password input rendered empty.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class LlmViewModel implements CatalogueEditor<LlmDto> {

    @Section("Model")
    @Help("Referenced by agents and RAG sources. Cannot be changed once created.")
    @ReadOnly
    @HiddenInCreate
    // Deliberately not @NotEmpty: a validation is emitted for every field of this class, hidden
    // ones included, so requiring a field that the creation form does not render makes creating
    // one impossible. The requirement belongs on newId, which is the field that is on screen.
    String id;

    @NotEmpty
    @HiddenInList
    @Help("Only used when creating. Lowercase, no spaces — e.g. anthropic-opus.")
    String newId;

    @NotEmpty
    String name;

    @NotEmpty
    LlmProvider provider;

    @NotEmpty
    @Help("The provider's own model id, verbatim — e.g. claude-opus-5. Not validated here: "
            + "new models appear faster than this service is rebuilt.")
    String model;

    @Help("Required for OPENAI_COMPATIBLE — Ollama, vLLM, a gateway — and left empty for a "
            + "provider's own endpoint. With or without a trailing /v1: both are accepted.")
    String baseUrl;

    @Section("Sampling")
    @Help("Between 0 and 1. Low values keep a tool-calling agent from improvising arguments.")
    Double temperature;

    @Help("At least 256. Too low truncates every answer mid-sentence without any error.")
    Integer maxTokens;

    @Section("Credential")
    @ReadOnly
    @Help("Whether a key is stored. The key itself is never shown, here or anywhere else.")
    String credentialStatus;

    @Stereotype(FieldStereotype.password)
    @Help("Leave empty to keep the stored key. Type a new one and use 'Replace credential' — "
            + "saving the form does not touch it.")
    String newApiKey;

    @Section("Status")
    boolean enabled;

    final CreateLlmUseCase createLlmUseCase;
    final UpdateLlmUseCase updateLlmUseCase;
    final ReplaceLlmCredentialUseCase replaceLlmCredentialUseCase;

    public String create(HttpRequest httpRequest) {
        return createLlmUseCase.handle(new CreateLlmCommand(newId, name, provider, model,
                baseUrl, temperature, maxTokens, newApiKey));
    }

    public void save(HttpRequest httpRequest) {
        updateLlmUseCase.handle(new UpdateLlmCommand(id, name, provider, model, baseUrl,
                temperature, maxTokens, enabled));
    }

    @Toolbar
    @Action(confirmationRequired = true,
            confirmationTitle = "Replace this model's credential?",
            confirmationMessage = "The stored key is overwritten and cannot be recovered.")
    public Object replaceCredential(HttpRequest httpRequest) {
        if (newApiKey == null || newApiKey.isBlank()) {
            // The button is in the header of the view as well as the editor, where this field is
            // always empty. Clearing on an empty field made one misclick there erase the key; a
            // model that should not be used is disabled instead.
            return Message.error("Type the new key in 'New api key' first (Edit). To stop using "
                    + "this model, disable it.");
        }
        replaceLlmCredentialUseCase.handle(new ReplaceLlmCredentialCommand(id, newApiKey));
        newApiKey = null;
        credentialStatus = "set";
        return List.of(new Message("Credential replaced"), new State(this));
    }

    @Override
    public String id() {
        return id;
    }

    public LlmViewModel load(LlmDto dto) {
        id = dto.id();
        newId = dto.id();
        name = dto.name();
        provider = dto.provider();
        model = dto.model();
        baseUrl = dto.baseUrl();
        temperature = dto.temperature();
        maxTokens = dto.maxTokens();
        credentialStatus = dto.credentialSet() ? "set" : "missing";
        // Never populated from storage. There is nothing to populate it from — the read side does
        // not carry the key — and this line is here so nobody adds one later.
        newApiKey = null;
        enabled = dto.enabled();
        return this;
    }

    @Override
    public String toString() {
        return id != null ? name + " (" + model + ")" : "New LLM";
    }
}
