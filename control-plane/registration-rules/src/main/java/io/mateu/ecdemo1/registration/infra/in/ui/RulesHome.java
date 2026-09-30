package io.mateu.ecdemo1.registration.infra.in.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import org.springframework.stereotype.Service;

/**
 * The registration rules, on the control console (console, rw-console): governance, not operation —
 * what each destination's law requires of a guest's registration, kept by compliance and applied by
 * every front office.
 */
@UI("/_registration-rules")
@Title("")
@FavIcon("/images/riu.svg")
@PageTitle("Registro de huéspedes")
@Logo("/images/riu.svg")
@Style(StyleConstants.FULL_WIDTH)
@Service
public class RulesHome {

    @Menu
    @Label("Registro de huéspedes")
    RulesMenu registro;
}
