package io.mateu.ecdemo1.iacp.infra.in.ui.pages.rag;

import io.mateu.ecdemo1.iacp.application.out.query.RagQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RagDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RagRow;
import io.mateu.ecdemo1.iacp.application.usecases.rag.delete.DeleteRagCommand;
import io.mateu.ecdemo1.iacp.application.usecases.rag.delete.DeleteRagUseCase;
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
@Title("RAG sources")
public class RagCrudOrchestrator extends CatalogueCrud<
        RagViewModel, RagViewModel, RagViewModel, NoFilters, RagRow, String, RagDto> {

    final RagViewModel viewModel;
    final DeleteRagUseCase deleteRagUseCase;
    final RagQueryService queryService;

    @Override
    protected RagViewModel editor() {
        return viewModel;
    }

    @Override
    protected RagQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteRagUseCase.handle(new DeleteRagCommand(selectedIds));
    }
}
