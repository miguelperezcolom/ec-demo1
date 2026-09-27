package io.mateu.ecdemo1.uicommons.crud;

import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;

/**
 * The form a {@link CatalogueCrud} views, edits and creates with: one class for all three, filled
 * from the entry's DTO, and saving or creating itself.
 *
 * @param <Dto> what the query side answers for one entry
 */
public interface CatalogueEditor<Dto> extends Identifiable {

    /** This form, filled with {@code dto}. */
    CatalogueEditor<Dto> load(Dto dto);

    /** Saves the edited entry. */
    void save(HttpRequest httpRequest);

    /** Creates the entry and answers its id. */
    String create(HttpRequest httpRequest);
}
