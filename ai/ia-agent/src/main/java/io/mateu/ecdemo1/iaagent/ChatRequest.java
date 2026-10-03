package io.mateu.ecdemo1.iaagent;

import java.util.List;
import java.util.Map;

/**
 * Request body for /api/agent/chat and /api/agent/stream.
 *
 * @param message     User's text input.
 * @param sessionId   Browser-side chat session identifier (used for conversation history
 *                    and menu context caching).
 * @param menuContext Full application menu flattened as a list of navigable screens.
 *                    Only needs to be sent when the menu changes; subsequent requests
 *                    may omit it and the last cached value will be used.
 * @param currentRoute The UI route the prompt was sent from, if the client sends it. Used by the
 *                    control plane's routing rules that key on a screen; null when not sent, in
 *                    which case screen-based rules simply do not match.
 * @param locale      The UI locale, e.g. "es", if the client sends it. Used by locale-based routing
 *                    rules; null when not sent.
 * @param context     What Mateu's chat sends of the screen the user is on ({@code url}, the screen's
 *                    title, its state); only {@code url} is read, as the route the user is on when
 *                    {@code currentRoute} is not sent.
 */
public record ChatRequest(
        String message,
        String sessionId,
        List<MenuEntry> menuContext,
        String currentRoute,
        String locale,
        Map<String, Object> context
) {
    public ChatRequest(String message, String sessionId, List<MenuEntry> menuContext,
                       String currentRoute, String locale) {
        this(message, sessionId, menuContext, currentRoute, locale, null);
    }

    /** The route (with its query) the user is looking at, or null when the client says nothing. */
    public String screenRoute() {
        if (currentRoute != null && !currentRoute.isBlank()) {
            return currentRoute;
        }
        if (context != null && context.get("url") instanceof String url && !url.isBlank()) {
            return url;
        }
        return null;
    }

    /**
     * @param description what the screen is for, when the app says
     * @param listing     for a listing screen, what its URL accepts (Mateu's menu metadata); null
     *                    for other screens and for shells that do not publish it
     */
    public record MenuEntry(
            List<String> path,
            NavigationDetail navigation,
            String description,
            ListingInfo listing
    ) {
        public MenuEntry(List<String> path, NavigationDetail navigation) {
            this(path, navigation, null, null);
        }
    }

    /**
     * What a listing's URL accepts, as Mateu derives it from the listing's declaration.
     *
     * @param idField     the row field that identifies a row (what {@code idsParam} matches)
     * @param idsParam    the framework-reserved id-set filter ({@code ids}): comma-joined row ids
     * @param searchParam the free-text search param, null when the listing has no search box
     * @param filters     the declared filters
     */
    public record ListingInfo(
            String idField,
            String idsParam,
            String searchParam,
            List<ListingFilter> filters
    ) {}

    /**
     * One declared filter of a listing.
     *
     * @param param     the query param (for a range, the field; its bounds are fromParam/toParam)
     * @param type      enum, string, boolean, number, date, dateTime, dateRange, numberRange…
     * @param multiple  several values allowed, comma-joined
     * @param values    the allowed values (enums), null when free
     * @param fromParam a range's lower bound param ({@code arrival_from}), inclusive
     * @param toParam   a range's upper bound param ({@code arrival_to}), inclusive
     */
    public record ListingFilter(
            String param,
            String label,
            String type,
            boolean multiple,
            List<String> values,
            String fromParam,
            String toParam
    ) {}

    public record NavigationDetail(
            String route,
            String consumedRoute,
            String actionId,
            String baseUrl,
            String serverSideType,
            String uriPrefix
    ) {}
}
