package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;

/**
 * A preview link as a URL of the portal, signed: the token is the link's id and an HMAC of what it shows
 * (its id, its item and its expiry), so a token that was not made here — or whose link was changed — is
 * refused before anything is shown.
 */
public interface PreviewLinkUrls {

    /** The token of the URL: {@code {id}.{signature}}. */
    String token(PreviewLink link);

    /** The whole URL, in the portal. */
    String url(PreviewLink link);

    /** Whether a token is this link's. */
    default boolean valid(PreviewLink link, String token) {
        return token != null && java.security.MessageDigest.isEqual(token(link).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** The link id a token names (what comes before its signature). */
    static String idOf(String token) {
        if (token == null) {
            return "";
        }
        var dot = token.indexOf('.');
        return dot < 0 ? token : token.substring(0, dot);
    }
}
