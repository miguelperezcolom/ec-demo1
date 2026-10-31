package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Searchable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Per item with mandatory confirmation: the percentage confirmed and the breakdown by hotel and
 * department. Exportable.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Confirmaciones — resumen")
public class ConfirmationsListing implements Listing<ConfirmationsListing.Row> {

    public record Row(@Label("Item") String item, @Label("Hotel") String hotel, @Label("Departamento") String department,
                      @Label("Deben confirmar") long expected, @Label("Han confirmado") long confirmed,
                      @Label("%") Status percent) {
    }

    final ConfirmationsReport report;

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var lines = report.lines();
        var rows = lines.stream()
                .collect(Collectors.groupingBy(l -> l.itemTitle() + "|" + ConfirmationsReport.hotel(l.employee()) + "|"
                        + ConfirmationsReport.department(l.employee())))
                .entrySet().stream().map(e -> {
                    var k = e.getKey().split("\\|", -1);
                    var expected = e.getValue().size();
                    var confirmed = e.getValue().stream().filter(ConfirmationsReport.Line::confirmed).count();
                    var pct = expected == 0 ? 100 : (int) (confirmed * 100 / expected);
                    return new Row(k[0], k[1], k[2], expected, confirmed, new Status(pct == 100 ? StatusType.SUCCESS
                            : pct >= 50 ? StatusType.WARNING : StatusType.DANGER, pct + " %"));
                })
                .sorted(Comparator.comparing(Row::item).thenComparing(Row::hotel).thenComparing(Row::department))
                .toList();
        return new ListingData<>(new Page<>("", Math.max(20, rows.size()), 0, rows.size(), rows),
                "Ningún contenido publicado pide confirmación");
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
