package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.visibility.Projection;
import io.mateu.ecdemo1.contenthub.application.visibility.ProjectionCache;
import io.mateu.ecdemo1.contenthub.infra.in.kafka.EmployeeDirectoryListener;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/** How the projection stands, and the two levers an administrator has over it. */
@Service
@Scope("prototype")
@Title("Visibilidad — estado")
public class ProjectionPage {

    @ReadOnly
    @Label("Empleados en el directorio")
    long employees;
    @ReadOnly
    @Label("Perfiles (hashes) materializados")
    int profiles;
    @ReadOnly
    @Label("Sujetos con audiencia (items, bloques, variantes)")
    int subjects;
    @ReadOnly
    @Label("Entradas en caché")
    long cacheEntries;

    final Projection projection;
    final ContentIndex index;
    final EmployeeDirectory directory;
    final ProjectionCache cache;
    final EmployeeDirectoryListener directoryListener;
    final Readers readers;

    public ProjectionPage(Projection projection, ContentIndex index, EmployeeDirectory directory, ProjectionCache cache,
                          EmployeeDirectoryListener directoryListener, Readers readers) {
        this.projection = projection;
        this.index = index;
        this.directory = directory;
        this.cache = cache;
        this.directoryListener = directoryListener;
        this.readers = readers;
        employees = directory.count();
        profiles = index.profiles().size();
        subjects = index.subjects().size();
        cacheEntries = cache.size();
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "¿Recalcular la proyección desde cero?",
            confirmationMessage = "Se evalúa cada audiencia para cada perfil. Debe dar exactamente lo mismo que ya hay.")
    public Object recalcular(HttpRequest httpRequest) {
        readers.of(httpRequest).require(EditorialPermission.ADMIN);
        var changed = projection.rebuild();
        return Message.success(changed == 0 ? "Recalculada: idéntica a la que había" : "Recalculada: " + changed + " sujetos cambiaron");
    }

    @Toolbar
    @Action
    public Object pedirCopiaDelDirectorio(HttpRequest httpRequest) {
        readers.of(httpRequest).require(EditorialPermission.ADMIN);
        directoryListener.requestSnapshot();
        return Message.success("Pedida al servicio de usuarios; llega en unos segundos");
    }
}
