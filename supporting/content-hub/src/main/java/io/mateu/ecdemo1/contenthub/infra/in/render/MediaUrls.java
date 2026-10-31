package io.mateu.ecdemo1.contenthub.infra.in.render;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * Signed URLs for media. The browser fetches an {@code <img>} or a {@code <video>} by itself, without the
 * bearer token, so the gateway lets {@code /_intranet/media/**} through and this signature stands in:
 * the page that was drawn for a profile carries URLs signed for that profile's hash, valid for a while,
 * and the media endpoint checks the signature and then the visibility again.
 *
 * <p>{@link #EDITOR} signs for the backoffice's previews: an editor sees the whole library.
 */
@Component
public class MediaUrls {

    public static final String EDITOR = "EDITOR";
    static final Duration VALIDITY = Duration.ofHours(2);

    final byte[] secret;
    final Clock clock;

    public MediaUrls(@Value("${content-hub.media.secret:}") String secret, Clock clock) {
        if (secret == null || secret.isBlank()) {
            var random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
        } else {
            this.secret = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.clock = clock;
    }

    /** @param base "/_intranet" for the portal, "/_content-hub" for the backoffice */
    public String url(String base, String mediaId, String hash) {
        var expires = clock.instant().plus(VALIDITY).getEpochSecond();
        return base + "/media/" + mediaId + "?h=" + hash + "&e=" + expires + "&s=" + sign(mediaId, hash, expires);
    }

    public boolean valid(String mediaId, String hash, long expires, String signature) {
        if (signature == null || hash == null || expires < clock.instant().getEpochSecond()) {
            return false;
        }
        return MessageDigest.isEqual(sign(mediaId, hash, expires).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    String sign(String mediaId, String hash, long expires) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            var sig = mac.doFinal((mediaId + "|" + hash + "|" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig).substring(0, 22);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
