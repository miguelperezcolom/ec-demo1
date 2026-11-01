package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterPreferences;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Hydratable;
import io.mateu.uidl.interfaces.OptionsSupplier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * What the reader wants from the newsletter: whether to receive it, and which sections. Only narrows:
 * the sections offered are those with something the reader can see, and nobody is ever sent anything
 * their audience hides from them.
 */
@Service
@Scope("prototype")
@Title("Mis preferencias")
public class PreferencesPage implements OptionsSupplier, Hydratable {

    @Section("Newsletter")
    @Label("Recibir la newsletter")
    boolean recibir = true;

    @Label("Solo de estas secciones")
    @Help("Vacío: de todas. Solo salen secciones con algo que puedes ver.")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> secciones = new ArrayList<>();

    /** Set once the reader's saved preferences are in the fields: an action's round trip keeps what they typed. */
    @Hidden
    boolean loaded;

    final PortalSupport portal;
    final NewsletterPreferences preferences;
    final NavigationRepository navigation;
    final Visibility visibility;

    public PreferencesPage(PortalSupport portal, NewsletterPreferences preferences, NavigationRepository navigation,
                           Visibility visibility) {
        this.portal = portal;
        this.preferences = preferences;
        this.navigation = navigation;
        this.visibility = visibility;
    }

    /** The reader's saved preferences, the first time the page is drawn for them. */
    @Override
    public void hydrate(HttpRequest httpRequest) {
        if (loaded) {
            return;
        }
        var p = preferences.of(portal.reader(httpRequest).userId());
        recibir = p.subscribed();
        secciones = new ArrayList<>(p.sections());
        loaded = true;
    }

    @Toolbar
    @Action
    public Object guardar(HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        if (reader.viewingAs() != null) {
            return Message.warning("Estás viendo la intranet como otro perfil: tus preferencias no se tocan");
        }
        var allowed = new HashSet<String>();
        options("secciones", httpRequest).forEach(o -> allowed.add(String.valueOf(o.value())));
        var chosen = new HashSet<String>(secciones == null ? List.of() : secciones);
        chosen.retainAll(allowed);
        preferences.save(reader.userId(), new NewsletterPreferences.Preference(recibir, chosen));
        return Message.success(!recibir ? "No recibirás la newsletter"
                : chosen.isEmpty() ? "Recibirás la newsletter de todas las secciones"
                : "Recibirás la newsletter de " + chosen.size() + " sección(es)");
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "secciones".equals(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var nav = navigation.get();
        var shown = nav.shown(visibility.sectionsWithVisibleContent(reader.attributes()));
        return nav.sections().stream().filter(s -> shown.contains(s.id()))
                .map(s -> new Option(s.id(), s.name(reader.attributes().language()))).toList();
    }
}
