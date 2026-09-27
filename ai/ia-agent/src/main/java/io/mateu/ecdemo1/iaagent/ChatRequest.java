package io.mateu.ecdemo1.iaagent;

import java.util.List;

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
 */
public record ChatRequest(
        String message,
        String sessionId,
        List<MenuEntry> menuContext,
        String currentRoute,
        String locale
) {
    public record MenuEntry(
            List<String> path,
            NavigationDetail navigation
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
