package io.mateu.ecdemo1.contenthub.application.menu;

import io.mateu.ecdemo1.contenthub.application.security.Reader;

import java.util.Arrays;
import java.util.Optional;

/**
 * The portal's own screens a BUILTIN target can name, in the order the portal has always shown them.
 * The key is the route's segment (/intranet/{key}) and the name of the field that routes it
 * (infra.in.ui.portal.IntranetMenu): renaming one breaks deep links.
 */
public enum PortalFunction {
    INICIO("inicio", "Inicio"),
    NOTICIAS("noticias", "Noticias"),
    AVISOS("avisos", "Avisos"),
    DOCUMENTOS("documentos", "Documentos"),
    SECCIONES("secciones", "Secciones"),
    PERSONAS("personas", "Personas"),
    ORGANIGRAMA("organigrama", "Organigrama"),
    BUSCAR("buscar", "Buscar"),
    DOCUMENTOS_DRIVE("documentosDrive", "Documentos de Drive"),
    FAVORITOS("favoritos", "Mis favoritos"),
    PENDIENTES("pendientes", "Pendientes"),
    NUEVA_SOLICITUD("nuevaSolicitud", "Nueva solicitud"),
    SOLICITUDES("solicitudes", "Mis solicitudes"),
    POR_APROBAR("porAprobar", "Por aprobar"),
    ENCUESTAS("encuestas", "Encuestas"),
    PREFERENCIAS("preferencias", "Mis preferencias");

    private final String key;
    private final String label;

    PortalFunction(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    /** Its label in the portal until now (Spanish), the seed's. */
    public String label() {
        return label;
    }

    /**
     * Whether a reader can use it, so its entry is left out for whoever cannot. Every function is for
     * every employee today — «Por aprobar» included, which shows an empty list to whoever has nothing to
     * decide (asking the workflow engine on every drawing of the menu would cost a round trip) — so this
     * is where such a rule goes, not a rule yet.
     */
    public boolean usableBy(Reader reader) {
        return true;
    }

    public static Optional<PortalFunction> byKey(String key) {
        return Arrays.stream(values()).filter(f -> f.key.equals(key)).findFirst();
    }
}
