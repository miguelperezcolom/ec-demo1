package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.menu.SiteMenus;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The sites' menus, entry by entry in menu order (a submenu followed by what it holds, indented): the
 * intranet's today, and each newsletter's once it has one. Removing a submenu removes what it holds.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Menús")
@ContentAdmins
public class MenusCrud extends SimpleCrud<MenuEntryForm, MenuEntryForm, MenuEntryForm, NoFilters, MenusCrud.Row, String> {

    public record Row(@Label("Id") String id, @Label("Sitio") String site, @Label("Entrada") String label,
                      @Label("Destino") String target, @Label("Quién lo verá") String audience,
                      @Label("Activa") String active) {
    }

    final ObjectProvider<MenuEntryForm> forms;
    final SiteMenus menus;
    final NamedAudiences named;
    final Readers readers;

    @Override
    protected Class<MenuEntryForm> formClass() {
        return MenuEntryForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var labels = named.labels();
        var rows = new ArrayList<Row>();
        for (var site : menus.sites()) {
            for (var line : menus.lines(site)) {
                var e = line.entry();
                rows.add(new Row(MenuEntryForm.rowId(site, e.id()), site,
                        "— ".repeat(line.depth()) + e.label("es", "es") + (e.isSubmenu() ? " ▸" : ""),
                        menus.describe(site, e.target()), e.audience().describe(labels), e.active() ? "Sí" : "No"));
            }
        }
        return all(rows, "Sin entradas de menú");
    }

    @Override
    public MenuEntryForm view(String id, HttpRequest httpRequest) {
        var parts = MenuEntryForm.split(id);
        return forms.getObject().load(parts[0], menus.menu(parts[0]).find(parts[1]).orElseThrow());
    }

    @Override
    public MenuEntryForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public MenuEntryForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        for (var id : ids) {
            var parts = MenuEntryForm.split(id);
            // a submenu removed earlier in the same batch took its entries along
            if (menus.menu(parts[0]).find(parts[1]).isPresent()) {
                menus.remove(reader, parts[0], parts[1]);
            }
        }
    }
}
