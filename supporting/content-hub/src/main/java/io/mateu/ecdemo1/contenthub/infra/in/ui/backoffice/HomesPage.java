package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.HomePages;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Which pages are homes, in order. A home is an ordinary page — written, reviewed and published like
 * any other, with its own audience — and a reader's home is the first page of this list they can see;
 * one who sees none gets the portal's default home.
 */
@Service
@Scope("prototype")
@Title("Inicio de la intranet")
@ContentAdmins
public class HomesPage {

    @Label("Páginas de inicio, en orden")
    @Help("A cada lector le toca la primera que puede ver (por su audiencia); si no ve ninguna, el inicio por defecto. "
            + "Pon primero las más concretas (Recepción Canarias) y después las generales.")
    @Colspan(2)
    List<HomeEntry> paginas = new ArrayList<>();

    final HomePages homes;
    final ItemRepository items;
    final Readers readers;

    public HomesPage(HomePages homes, ItemRepository items, Readers readers) {
        this.homes = homes;
        this.items = items;
        this.readers = readers;
        homes.pageIds().forEach(id -> paginas.add(HomeEntry.of(id)));
    }

    @Toolbar
    @Action
    public Object guardar(HttpRequest httpRequest) {
        readers.of(httpRequest).require(EditorialPermission.ADMIN);
        var ids = new LinkedHashSet<String>();
        for (var entry : paginas) {
            if (entry == null || entry.pageId == null || entry.pageId.isBlank()) {
                continue;
            }
            var item = items.find(entry.pageId).orElse(null);
            if (item == null || item.type() != ItemType.PAGE) {
                return Message.error("«" + entry.pageId + "» no es una página");
            }
            if (item.status() != ItemStatus.PUBLISHED) {
                return Message.error("«" + entry.pageId + "» no está publicada");
            }
            ids.add(entry.pageId);
        }
        homes.save(List.copyOf(ids));
        return Message.success(ids.isEmpty() ? "Sin páginas de inicio: todos verán el inicio por defecto"
                : ids.size() + " página(s) de inicio guardadas");
    }
}
