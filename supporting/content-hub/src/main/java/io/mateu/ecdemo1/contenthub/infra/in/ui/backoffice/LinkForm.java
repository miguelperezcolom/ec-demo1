package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;

/** One quick link of a block: what it says and where it goes. */
@Editors
public class LinkForm {

    @Label("Texto")
    public String label;
    @Label("URL")
    @Help("Una ruta de la intranet (/intranet/buscar) se abre aquí; una dirección completa, en otra pestaña.")
    public String url;

    public static LinkForm of(String label, String url) {
        var f = new LinkForm();
        f.label = label;
        f.url = url;
        return f;
    }

    @Override
    public String toString() {
        return label == null ? "Enlace" : label;
    }
}
