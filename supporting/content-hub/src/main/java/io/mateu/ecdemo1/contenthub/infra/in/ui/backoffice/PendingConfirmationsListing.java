package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/** Who still has to confirm what: the list to chase. Exportable. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Confirmaciones — pendientes")
public class PendingConfirmationsListing implements Listing<PendingConfirmationsListing.Row> {

    public record Row(@Label("Item") String item, @Label("Empleado") String employee, @Label("Email") String email,
                      @Label("Hotel") String hotel, @Label("Departamento") String department) {
    }

    final ConfirmationsReport report;

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var rows = report.lines().stream().filter(l -> !l.confirmed())
                .map(l -> new Row(l.itemTitle(), l.employee().name(), l.employee().email(),
                        ConfirmationsReport.hotel(l.employee()), ConfirmationsReport.department(l.employee())))
                .toList();
        return new ListingData<>(new Page<>("", Math.max(20, rows.size()), 0, rows.size(), rows), "Nadie tiene nada pendiente");
    }

    @Override
    public boolean csvExportable() {
        return true;
    }

    @Override
    public boolean excelExportable() {
        return true;
    }
}
