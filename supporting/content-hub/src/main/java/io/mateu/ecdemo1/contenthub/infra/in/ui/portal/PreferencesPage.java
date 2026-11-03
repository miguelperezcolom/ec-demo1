package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterPreferences;
import io.mateu.ecdemo1.contenthub.application.out.NotificationPreferences;
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
 * The newsletters the reader receives — each with its own subscription, managed on the newsletter's page —
 * and how they want to hear of what is published: per channel.
 */
@Service
@Scope("prototype")
@Title("Mis preferencias")
public class PreferencesPage implements OptionsSupplier, Hydratable {

    @Section("Newsletters")
    @Label("")
    @io.mateu.uidl.annotations.Colspan(2)
    java.util.concurrent.Callable<io.mateu.uidl.fluent.Component> newsletters = this::newsletters;

    @Section("Mi foto")
    @Label("")
    @io.mateu.uidl.annotations.Colspan(2)
    java.util.concurrent.Callable<io.mateu.uidl.fluent.Component> miFoto = this::miFoto;

    @Section("Notificaciones")
    @Label("Avisarme en la aplicación")
    @Help("La campana y las notificaciones del navegador: lo nuevo que puedes ver y lo que debes confirmar.")
    boolean enApp = true;

    @Label("Avisarme por correo")
    @Help("Solo de lo que se publica como importante.")
    boolean porCorreo = true;

    /** Set once the reader's saved preferences are in the fields: an action's round trip keeps what they typed. */
    @Hidden
    boolean loaded;

    final PortalSupport portal;
    final io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService newsletterService;
    final NotificationPreferences notifications;
    /** The reader, for the newsletters' list (drawn after hydration); not part of the form's state. */
    @Hidden
    transient io.mateu.ecdemo1.contenthub.application.security.Reader reader;

    public PreferencesPage(PortalSupport portal,
                           io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService newsletterService,
                           NotificationPreferences notifications) {
        this.portal = portal;
        this.newsletterService = newsletterService;
        this.notifications = notifications;
    }

    /** Its own screen: the users service's, the employee's data being theirs. */
    io.mateu.uidl.fluent.Component miFoto() {
        return new io.mateu.uidl.data.Anchor("Cambiar mi foto", "/miFoto");
    }

    /** Each newsletter for the reader: whether they receive it, and the way to its page (subscribe, unsubscribe, sections). */
    io.mateu.uidl.fluent.Component newsletters() {
        if (reader == null) {
            return PortalComponents.muted("");
        }
        var list = newsletterService.newslettersOf(reader);
        if (list.isEmpty()) {
            return PortalComponents.muted("No hay newsletters para ti.");
        }
        return PortalComponents.column(list.stream().<io.mateu.uidl.fluent.Component>map(s -> PortalComponents.column(List.of(
                new io.mateu.uidl.data.Anchor(s.site().name(), io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui.PORTAL + "/n/" + s.site().id()),
                PortalComponents.small(!s.preference().subscribed() ? "No la recibes"
                        : s.preference().sections().isEmpty() ? "La recibes, todas las secciones"
                        : "La recibes, " + s.preference().sections().size() + " sección(es)")))).toList());
    }

    /** The reader's saved preferences, the first time the page is drawn for them. */
    @Override
    public void hydrate(HttpRequest httpRequest) {
        reader = portal.reader(httpRequest);
        if (loaded) {
            return;
        }
        var told = notifications.of(portal.reader(httpRequest).userId());
        enApp = told.inApp();
        porCorreo = told.email();
        loaded = true;
    }

    @Toolbar
    @Action
    public Object guardar(HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        if (reader.viewingAs() != null) {
            return Message.warning("Estás viendo la intranet como otro perfil: tus preferencias no se tocan");
        }
        notifications.save(reader.userId(), new NotificationPreferences.Preference(enApp, porCorreo));
        return Message.success("Preferencias guardadas. Las de cada newsletter, en su página («Newsletters»).");
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return false;
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return List.of();
    }
}
