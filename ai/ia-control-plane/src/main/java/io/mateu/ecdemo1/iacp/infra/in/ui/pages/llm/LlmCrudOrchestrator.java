package io.mateu.ecdemo1.iacp.infra.in.ui.pages.llm;

import io.mateu.ecdemo1.iacp.application.out.query.LlmQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.LlmDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.LlmRow;
import io.mateu.ecdemo1.iacp.application.usecases.llm.delete.DeleteLlmCommand;
import io.mateu.ecdemo1.iacp.application.usecases.llm.delete.DeleteLlmUseCase;
import io.mateu.ecdemo1.uicommons.crud.CatalogueCrud;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/** The CRUD of this catalogue: listing, form and queries in {@link CatalogueCrud}; deleting here. */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("LLMs")
public class LlmCrudOrchestrator extends CatalogueCrud<
        LlmViewModel, LlmViewModel, LlmViewModel, NoFilters, LlmRow, String, LlmDto> {

    final LlmViewModel viewModel;
    final DeleteLlmUseCase deleteLlmUseCase;
    final LlmQueryService queryService;

    @Override
    protected LlmViewModel editor() {
        return viewModel;
    }

    @Override
    protected LlmQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteLlmUseCase.handle(new DeleteLlmCommand(selectedIds));
    }
}
