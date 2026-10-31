package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
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

/** The employee directory as the users service keeps it in step (read-only here). */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Directorio de empleados")
public class DirectoryListing implements Listing<DirectoryListing.Row> {

    public record Row(@Label("Usuario") String id, @Label("Nombre") String name, @Label("Hotel") String hotel,
                      @Label("Región") String region, @Label("Departamento") String department,
                      @Label("Puesto") String jobTitle, @Label("Idioma") String language, @Label("Perfil (hash)") String hash) {
    }

    final EmployeeDirectory directory;

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request == null || request.searchText() == null ? "" : request.searchText().toLowerCase();
        var rows = directory.all().stream()
                .filter(e -> text.isBlank() || (e.userId() + " " + e.name()).toLowerCase().contains(text))
                .map(e -> new Row(e.userId(), e.name(), e.attributes().get(Attribute.HOTEL).orElse(""),
                        e.attributes().get(Attribute.REGION).orElse(""), e.attributes().get(Attribute.DEPARTMENT).orElse(""),
                        e.attributes().get(Attribute.JOB_TITLE).orElse(""), e.attributes().language(), e.hash()))
                .toList();
        return new ListingData<>(new Page<>(text, Math.max(20, rows.size()), 0, rows.size(), rows),
                "El directorio está vacío: se pide una copia al servicio de usuarios al arrancar");
    }
}
