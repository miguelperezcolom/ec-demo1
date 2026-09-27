package io.mateu.ecdemo1.uicommons.crud;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.mateu.uidl.reflection.GenericClassProvider.getGenericClass;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogueCrudTest {

    record ThingDto(String id, String name) {
    }

    record ThingRow(String id, String name) {
    }

    static class ThingForm implements CatalogueEditor<ThingDto> {
        String id;
        String name;

        @Override
        public ThingForm load(ThingDto dto) {
            id = dto.id();
            name = dto.name();
            return this;
        }

        @Override
        public void save(HttpRequest httpRequest) {
        }

        @Override
        public String create(HttpRequest httpRequest) {
            return "new";
        }

        @Override
        public String id() {
            return id;
        }
    }

    static class Things implements CatalogueQueries<ThingDto, ThingRow, String> {
        @Override
        public ListingData<ThingRow> findAll(String searchText, Object filters, Pageable pageable) {
            return ListingData.of(new ThingRow("1", "one"));
        }

        @Override
        public Optional<ThingDto> getById(String id) {
            return Optional.of(new ThingDto(id, "thing " + id));
        }
    }

    static class ThingCrud extends CatalogueCrud<ThingForm, ThingForm, ThingForm, NoFilters, ThingRow, String, ThingDto> {
        final ThingForm form = new ThingForm();

        @Override
        protected ThingForm editor() {
            return form;
        }

        @Override
        protected CatalogueQueries<ThingDto, ThingRow, String> queries() {
            return new Things();
        }

        @Override
        public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        }
    }

    /** What the CRUDs looked like before the base class: straight on Mateu's Crud. */
    static abstract class DirectCrud extends Crud<ThingForm, ThingForm, ThingForm, NoFilters, ThingRow, String> {
    }

    @Test
    void mateu_resolves_the_same_classes_through_the_base_class_as_straight_on_crud() {
        var crud = new ThingCrud();
        assertThat(crud.viewClass()).isEqualTo(ThingForm.class);
        assertThat(crud.editorClass()).isEqualTo(ThingForm.class);
        assertThat(crud.creationFormClass()).isEqualTo(ThingForm.class);
        assertThat(crud.filtersClass()).isEqualTo(NoFilters.class);
        assertThat(crud.rowClass()).isEqualTo(ThingRow.class);
        assertThat(crud.idClass()).isEqualTo(String.class);
        assertThat(crud.getIdFieldForRow()).isEqualTo("id");
        for (var slot : List.of("View", "Editor", "CreationForm", "Filters", "Row", "IdType", "EntityType")) {
            assertThat(getGenericClass(ThingCrud.class, Crud.class, slot)).as(slot)
                    .isEqualTo(getGenericClass(DirectCrud.class, Crud.class, slot));
        }
        assertThat(getGenericClass(ThingCrud.class, Listing.class, "Row"))
                .isEqualTo(getGenericClass(DirectCrud.class, Listing.class, "Row"));
    }

    @Test
    void view_and_edit_load_the_form_from_the_entry() {
        var crud = new ThingCrud();
        assertThat(crud.view("7", null).name).isEqualTo("thing 7");
        assertThat(crud.edit("8", null).name).isEqualTo("thing 8");
        assertThat(crud.creationForm(null)).isSameAs(crud.form);
    }

    @Test
    void search_asks_the_queries() {
        var crud = new ThingCrud();
        var request = new SearchRequest("", null, List.of(), new Pageable(0, 20, List.of()));
        assertThat(crud.search(request, null).page().content()).extracting(ThingRow::name).containsExactly("one");
    }
}
