package io.mateu.ecdemo1.uicommons.crud;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;

/**
 * A catalogue's CRUD — list, view, edit, create, delete — for the common case: one form for all
 * three screens ({@link CatalogueEditor}), the rows and entries from a {@link CatalogueQueries},
 * and deletion by a use case. A subclass names its three collaborators and its title:
 *
 * <pre>{@code
 * @Service @Scope("prototype") @RequiredArgsConstructor @Title("Agents")
 * public class AgentCrudOrchestrator extends CatalogueCrud<
 *         AgentViewModel, AgentViewModel, AgentViewModel, NoFilters, AgentRow, String, AgentDto> {
 *     final AgentViewModel viewModel;
 *     final DeleteAgentUseCase deleteAgentUseCase;
 *     final AgentQueryService queryService;
 *
 *     protected AgentViewModel editor() { return viewModel; }
 *     protected AgentQueryService queries() { return queryService; }
 *     public void deleteAllById(List<String> ids, HttpRequest r) { deleteAgentUseCase.handle(new DeleteAgentCommand(ids)); }
 * }
 * }</pre>
 *
 * <p><b>Why it repeats {@link Crud}'s six type parameters, in the same order</b>, and adds its own
 * only after them: Mateu finds the row, editor and id classes by walking the generic superclasses
 * from the concrete class up to {@code Crud}, matching each type variable by its POSITION. A base
 * class that passed one variable to several of Crud's slots, or reordered them, would make it
 * resolve the wrong class — or none. With the same six in the same places, every step of that walk
 * is the identity. CatalogueCrudTest holds it to that.
 *
 * @param <Dto> what {@link #queries()} answers for one entry, and {@link CatalogueEditor#load} takes
 */
public abstract class CatalogueCrud<
        View extends CatalogueEditor<Dto>,
        Editor extends CatalogueEditor<Dto>,
        CreationForm extends CatalogueEditor<Dto>,
        Filters,
        Row,
        IdType,
        Dto> extends Crud<View, Editor, CreationForm, Filters, Row, IdType> {

    /** The form, as injected: a prototype, so each call gets its own. */
    protected abstract View editor();

    /** Where the rows and the entries come from. */
    protected abstract CatalogueQueries<Dto, Row, IdType> queries();

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        return queries().findAll(request.searchText(), filters(request), request.pageable());
    }

    @Override
    @SuppressWarnings("unchecked")
    public View view(IdType id, HttpRequest httpRequest) {
        return (View) editor().load(queries().getById(id).orElseThrow());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Editor edit(IdType id, HttpRequest httpRequest) {
        return (Editor) editor().load(queries().getById(id).orElseThrow());
    }

    @Override
    @SuppressWarnings("unchecked")
    public CreationForm creationForm(HttpRequest httpRequest) {
        return (CreationForm) editor();
    }

    @Override
    @SuppressWarnings("unchecked")
    public IdType save(HttpRequest httpRequest) {
        var form = httpRequest.getComponentState(editorClass());
        form.save(httpRequest);
        return (IdType) form.id();
    }

    @Override
    @SuppressWarnings("unchecked")
    public IdType create(HttpRequest httpRequest) {
        return (IdType) httpRequest.getComponentState(creationFormClass()).create(httpRequest);
    }
}
