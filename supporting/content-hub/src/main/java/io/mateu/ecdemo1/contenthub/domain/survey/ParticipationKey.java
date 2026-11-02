package io.mateu.ecdemo1.contenthub.domain.survey;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * What records that someone answered a survey, so they cannot answer twice. In a named survey, their id.
 * In an anonymous one, an HMAC of survey and user, keyed by the service's secret and the survey's own
 * salt: the same reader always gives the same key (a second answer is refused), but the key does not
 * say who it is — and it is stored apart from the answers, which carry no key at all.
 */
public final class ParticipationKey {

    private ParticipationKey() {
    }

    public static String of(Survey survey, String userId, String secret) {
        if (!survey.anonymous()) {
            return "u:" + userId;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            var key = ((secret == null ? "" : secret) + "|" + survey.salt()).getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return "a:" + HexFormat.of().formatHex(mac.doFinal((survey.id() + "|" + userId)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
