package io.mateu.ecdemo1.users.infra.in.ui.pages.people;

import io.mateu.ecdemo1.uicommons.user.DisplayOnlyTokenClaims;
import io.mateu.ecdemo1.users.application.query.UserQueryService;
import io.mateu.ecdemo1.users.application.usecases.user.people.PersonPhotos;
import io.mateu.ecdemo1.users.infra.in.ui.Refusals;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.annotations.UploadableImage;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * One's own photo. Whose it is comes only from the token, which the gateway verified on the way here (/_mi-foto/**
 * is never anonymous), and never from the screen: nobody can reach anybody else's from here.
 */
@Title("Mi foto")
@io.mateu.uidl.annotations.Trigger(type = io.mateu.uidl.annotations.TriggerType.OnLoad, actionId = "cargar")
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class MyPhotoPage {

    @UploadableImage
    @Label("Mi foto")
    @Help("Se ve en tu ficha, en el organigrama y junto a tus comentarios. Puedes cambiarla o quitarla cuando quieras.")
    String foto;

    final PersonPhotos photos;
    final UserQueryService users;

    /** The photo there is, on opening: a route's view isn't hydrated, so it's asked for. */
    @Action
    public Object cargar(HttpRequest httpRequest) {
        foto = current(httpRequest);
        return this;
    }

    @Toolbar
    @Action
    @Label("Guardar mi foto")
    public Object guardar(HttpRequest httpRequest) {
        if (foto == null || !foto.startsWith("data:")) {
            return Message.warning("Elige una foto con «Subir». Para quitar la tuya, usa «Quitar mi foto».");
        }
        Refusals.shown(() -> photos.replace(me(httpRequest), foto));
        foto = current(httpRequest);
        return List.of(this, Message.success("Foto guardada"));
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "¿Quitar tu foto?",
            confirmationMessage = "Deja de verse en tu ficha, en el organigrama y en tus comentarios: se verán tus iniciales.")
    @Label("Quitar mi foto")
    public Object quitar(HttpRequest httpRequest) {
        Refusals.shown(() -> photos.remove(me(httpRequest)));
        foto = null;
        return List.of(this, Message.success("Foto quitada"));
    }

    private String current(HttpRequest httpRequest) {
        return users.getById(me(httpRequest)).map(u -> u.profile() == null ? null : u.profile().photoUrl()).orElse(null);
    }

    static String me(HttpRequest httpRequest) {
        return DisplayOnlyTokenClaims.of(httpRequest)
                .map(c -> c.get("preferred_username"))
                .map(String::valueOf)
                .orElseThrow(() -> new io.mateu.uidl.UserFacingException("Inicia sesión para cambiar tu foto"));
    }
}
