package io.mateu.ecdemo1.users.infra.in.ui.pages.attributevalues;

import io.mateu.ecdemo1.uicommons.crud.CatalogueCrud;
import io.mateu.ecdemo1.users.application.query.AttributeValueQueryService;
import io.mateu.ecdemo1.users.application.query.dto.AttributeValueDto;
import io.mateu.ecdemo1.users.application.query.dto.AttributeValueRow;
import io.mateu.ecdemo1.users.application.usecases.attributevalue.delete.DeleteAttributeValueCommand;
import io.mateu.ecdemo1.users.application.usecases.attributevalue.delete.DeleteAttributeValueUseCase;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The catalogue of values the employee attributes take (companies, hotels, departments…): listing,
 * form and queries in {@link CatalogueCrud}; deleting here.
 */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Valores de atributo")
public class AttributeValuesCrudOrchestrator extends CatalogueCrud<
        AttributeValueViewModel, AttributeValueViewModel, AttributeValueViewModel, NoFilters, AttributeValueRow,
        String, AttributeValueDto> {

    final AttributeValueViewModel viewModel;
    final DeleteAttributeValueUseCase deleteAttributeValueUseCase;
    final AttributeValueQueryService queryService;

    @Override
    protected AttributeValueViewModel editor() {
        return viewModel;
    }

    @Override
    protected AttributeValueQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteAttributeValueUseCase.handle(new DeleteAttributeValueCommand(selectedIds));
    }
}
