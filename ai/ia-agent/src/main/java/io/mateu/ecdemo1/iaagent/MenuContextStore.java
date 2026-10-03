package io.mateu.ecdemo1.iaagent;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caches the UI menu context per browser session.
 *
 * The menu is sent by the frontend (typically on the first request or when it changes).
 * Subsequent prompts reuse the last cached menu so the LLM always knows which screens
 * are available in the UI.
 */
@Component
public class MenuContextStore {

    private static final Logger log = LoggerFactory.getLogger(MenuContextStore.class);

    private final Cache<String, List<ChatRequest.MenuEntry>> menus;

    public MenuContextStore() {
        this.menus = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Stores the menu for the given session. No-ops if {@code entries} is null or empty
     * (keeps the previous value so stale menus are not accidentally cleared).
     */
    public void update(String sessionId, List<ChatRequest.MenuEntry> entries) {
        if (entries == null || entries.isEmpty()) return;
        menus.put(sessionId, entries);
        log.debug("Session {}: menu updated with {} entries", sessionId, entries.size());
    }

    /** {@link #buildMenuSystemPrompt(String, String)} without knowing the screen the user is on. */
    public String buildMenuSystemPrompt(String sessionId) {
        return buildMenuSystemPrompt(sessionId, null);
    }

    /**
     * Returns a system-prompt-ready string that lists all available UI screens, what each listing
     * accepts in its URL (from the listing metadata the shell publishes with its menu) and how to
     * trigger navigation. Returns an empty string if no menu has been cached for the session yet.
     *
     * @param currentRoute the route (with query) the user is looking at, when the chat says
     */
    public String buildMenuSystemPrompt(String sessionId, String currentRoute) {
        var entries = menus.getIfPresent(sessionId);
        if (entries == null || entries.isEmpty()) return "";
        return render(entries, currentRoute);
    }

    static String render(List<ChatRequest.MenuEntry> entries, String currentRoute) {
        var sb = new StringBuilder();
        sb.append("## Pantallas disponibles en la UI\n\n");
        sb.append("El usuario está interactuando con una interfaz que tiene las siguientes pantallas:\n\n");

        boolean anyListing = false;
        for (var entry : entries) {
            String label = entry.path() != null
                    ? String.join(" > ", entry.path())
                    : "(sin nombre)";
            sb.append("- **").append(label).append("**");
            if (entry.description() != null && !entry.description().isBlank()) {
                sb.append(" (").append(entry.description().trim()).append(")");
            }
            if (entry.navigation() != null) {
                var nav = entry.navigation();
                sb.append(" — para abrir esta pantalla emite:\n  `[NAVIGATE:{");
                sb.append("\"route\":\"").append(nav.route()).append("\"");
                sb.append(",\"consumedRoute\":\"").append(nvl(nav.consumedRoute())).append("\"");
                sb.append(",\"actionId\":\"").append(nvl(nav.actionId())).append("\"");
                sb.append(",\"baseUrl\":\"").append(nvl(nav.baseUrl())).append("\"");
                sb.append(",\"serverSideType\":\"").append(nvl(nav.serverSideType())).append("\"");
                sb.append(",\"uriPrefix\":\"").append(nvl(nav.uriPrefix())).append("\"");
                sb.append("}]`");
            }
            sb.append("\n");
            if (entry.listing() != null) {
                anyListing = true;
                appendListing(sb, entry.listing());
            }
        }

        sb.append("""

## Navegación desde el agente

Para abrir una pantalla en la UI, incluye en tu respuesta, EN UNA LÍNEA APARTE, \
el bloque `[NAVIGATE:{...}]` exacto que aparece junto a la pantalla. \
El sistema lo interceptará, lo eliminará del texto mostrado al usuario y navegará automáticamente. \
Solo se navega a UNA pantalla por respuesta: emite un único bloque. \
Un bloque escrito en medio de una frase no navega: se convierte en un enlace. \
Para enlazar fichas sueltas en el texto usa enlaces markdown con la ruta relativa, \
p. ej. `[4MBZS7](/booking/bookings/4MBZS7)`; el chat los abre en la aplicación.
""");

        if (anyListing) {
            sb.append("""

## Enseñar elementos en un listado

Cuando el usuario pregunte por un conjunto de elementos («¿qué reservas hay canceladas?», \
«enséñame estas tres», «las reservas de clientes alemanes»), ENSÉÑASELOS en el listado de su \
entidad en vez de enumerarlos en el chat:
1. Si un filtro declarado del listado expresa exactamente el criterio, añádelo a la query de \
"route" (`/booking/bookings?status=Cancelled`).
2. Si no, averigua con tus herramientas qué elementos cumplen el criterio, recoge sus \
identificadores (el campo indicado en «identificador de fila») y navega al listado con \
`ids=<id1>,<id2>,…` (`/booking/bookings?ids=4MBZS7,JXD3G6`). Sirve para cualquier criterio, \
tenga filtro o no.
Copia el bloque NAVIGATE del listado tal cual y cambia solo "route", añadiéndole la query \
(valores separados por comas, `&` entre parámetros). Si el usuario ya está en ese listado, \
emítelo igual: el listado se filtra en el sitio.
En el chat, una o dos frases: qué has encontrado y que se lo enseñas en el listado \
(«Hay 2 reservas canceladas; te las muestro en el listado.»). No describas dónde está nada en la \
pantalla (izquierda, derecha…).
""");
        }
        if (currentRoute != null && !currentRoute.isBlank()) {
            sb.append("\nEl usuario está viendo ahora mismo la ruta `").append(currentRoute).append("`.\n");
        }
        return sb.toString();
    }

    private static void appendListing(StringBuilder sb, ChatRequest.ListingInfo listing) {
        sb.append("  Es un listado. Parámetros de la query de \"route\":\n");
        if (listing.filters() != null) {
            for (var f : listing.filters()) {
                if (f == null || f.param() == null) continue;
                sb.append("  - ");
                if (f.fromParam() != null || f.toParam() != null) {
                    sb.append("`").append(nvl(f.fromParam())).append("` / `").append(nvl(f.toParam())).append("`");
                    sb.append(" (").append(labelOf(f)).append(", rango ").append(rangeFormat(f.type()))
                            .append(", ambos extremos inclusive, cualquiera puede omitirse)");
                } else {
                    sb.append("`").append(f.param()).append("`");
                    sb.append(" (").append(labelOf(f));
                    if (f.values() != null && !f.values().isEmpty()) {
                        sb.append(f.multiple() ? ", uno o varios separados por comas de: " : ", uno de: ")
                                .append(String.join("|", f.values()));
                    } else if (f.type() != null) {
                        sb.append(", ").append(typeFormat(f.type()));
                    }
                    sb.append(")");
                }
                sb.append("\n");
            }
        }
        if (listing.searchParam() != null && !listing.searchParam().isBlank()) {
            sb.append("  - `").append(listing.searchParam()).append("` (búsqueda de texto libre)\n");
        }
        String ids = listing.idsParam() != null && !listing.idsParam().isBlank() ? listing.idsParam() : "ids";
        sb.append("  - `").append(ids).append("` (un conjunto concreto: identificadores de fila separados por comas");
        if (listing.idField() != null && !listing.idField().isBlank()) {
            sb.append("; identificador de fila: `").append(listing.idField()).append("`");
        }
        sb.append(")\n");
    }

    private static String labelOf(ChatRequest.ListingFilter f) {
        return f.label() != null && !f.label().isBlank() ? f.label() : f.param();
    }

    private static String rangeFormat(String type) {
        if (type != null && type.toLowerCase().contains("number")) return "numérico";
        if (type != null && type.toLowerCase().contains("time")) return "fecha y hora ISO";
        return "fecha ISO yyyy-MM-dd";
    }

    private static String typeFormat(String type) {
        return switch (type.toLowerCase()) {
            case "boolean", "bool" -> "true|false";
            case "date" -> "fecha ISO yyyy-MM-dd";
            case "datetime" -> "fecha y hora ISO";
            case "number", "integer", "int", "long", "decimal", "double" -> "número";
            default -> "texto";
        };
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
