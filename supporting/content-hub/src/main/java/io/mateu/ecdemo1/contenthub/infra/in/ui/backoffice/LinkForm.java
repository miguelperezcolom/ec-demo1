package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.Label;

/**
 * One quick link of a block: where it goes — the same in every language — and what it says in each (a language
 * without its text shows the default language's).
 */
@Editors
public class LinkForm {

    @Label("Texto (Español)")
    public String labelEs;
    @Label("URL")
    @Help("Una ruta de la intranet (/intranet/buscar) se abre aquí; una dirección completa, en otra pestaña.")
    public String url;
    @HiddenInList
    @Label("Text (English)")
    public String labelEn;
    @HiddenInList
    @Label("Text (Deutsch)")
    public String labelDe;

    /** A link with its text in one language. */
    public static LinkForm of(String language, String label, String url) {
        var f = new LinkForm();
        f.label(language, label);
        f.url = url;
        return f;
    }

    public String label(String language) {
        return switch (language == null ? "es" : language) {
            case "en" -> labelEn;
            case "de" -> labelDe;
            default -> labelEs;
        };
    }

    public void label(String language, String label) {
        switch (language == null ? "es" : language) {
            case "en" -> labelEn = label;
            case "de" -> labelDe = label;
            default -> labelEs = label;
        }
    }

    @Override
    public String toString() {
        var label = labelEs != null ? labelEs : labelEn != null ? labelEn : labelDe;
        return label == null ? "Enlace" : label;
    }
}
