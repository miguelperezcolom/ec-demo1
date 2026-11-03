package io.mateu.ecdemo1.users.infra.in.ui.pages.people;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import org.springframework.stereotype.Service;

/**
 * One's own photo, for everybody: a hidden section of the shells, reached from a person's card and from «Mis
 * preferencias». A screen of its own and not embedded in the portal's: Redwood doesn't send a picked file from an
 * embedded form, and saving would remove the photo.
 */
@UI("/_mi-foto")
@Title("")
@PageTitle("Mi foto")
@Service
public class MyPhotoHome {

    @Menu
    @Label("Mi foto")
    MyPhotoPage miFoto;
}
