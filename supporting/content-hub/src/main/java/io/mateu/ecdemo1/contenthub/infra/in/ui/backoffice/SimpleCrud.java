package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;

import java.util.List;

/**
 * The backoffice's smaller CRUDs: one form for view, edit and create, rows listed in memory (they are
 * a handful: sections, audiences, slots, editions). Same six type parameters as {@link Crud}, in the same
 * places, so Mateu resolves the row and form classes.
 */
public abstract class SimpleCrud<Form extends SimpleCrud.CrudForm, F2 extends SimpleCrud.CrudForm,
        F3 extends SimpleCrud.CrudForm, Filters, Row, IdType> extends Crud<Form, F2, F3, Filters, Row, IdType> {

    /** What the form does for the CRUD. */
    public interface CrudForm extends Identifiable {
        String create(HttpRequest httpRequest);

        void save(HttpRequest httpRequest);
    }

    protected abstract Class<? extends CrudForm> formClass();

    protected static <R> ListingData<R> all(List<R> rows, String empty) {
        return new ListingData<>(new Page<>("", Math.max(20, rows.size()), 0, rows.size(), rows), empty);
    }

    @Override
    @SuppressWarnings("unchecked")
    public IdType save(HttpRequest httpRequest) {
        var form = httpRequest.getComponentState(formClass());
        form.save(httpRequest);
        return (IdType) form.id();
    }

    @Override
    @SuppressWarnings("unchecked")
    public IdType create(HttpRequest httpRequest) {
        return (IdType) httpRequest.getComponentState(formClass()).create(httpRequest);
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
