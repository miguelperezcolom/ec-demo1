package io.mateu.ecdemo1.contenthub.infra.in.web;

import io.mateu.ecdemo1.contenthub.application.out.PreviewLinkRepository;
import io.mateu.ecdemo1.contenthub.application.out.PreviewLinkUrls;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The reviewer preview links, signed as the newsletter's are ({@link NewsletterLinksSigner}: HMAC-SHA256,
 * base64url, 22 characters), over {@code preview|id|item|expiry}: {@code {portal}/vista-previa/{id}.{sig}}.
 * Unlike the newsletter's, a restart must not break them (they last days): with no {@code MEDIA_SECRET},
 * the key is one made once and kept in the database.
 *
 * <p>The URL is a page of the portal, so the gateway asks for a login before anything gets here; the
 * signature only says that this service made the link — whether it still works (expiry, revocation,
 * publication) is the link's own record.
 */
@Component
public class PreviewLinksSigner implements PreviewLinkUrls {

    final String configured;
    final PreviewLinkRepository repository;
    final String base;
    volatile byte[] secret;

    public PreviewLinksSigner(@Value("${content-hub.media.secret:}") String secret, PreviewLinkRepository repository,
                              @Value("${content-hub.portal-url:http://ec1.localhost:8800/intranet}") String portalUrl) {
        this.configured = secret;
        this.repository = repository;
        this.base = portalUrl.replaceFirst("/+$", "") + "/vista-previa/";
    }

    @Override
    public String token(PreviewLink link) {
        return link.id() + "." + sign("preview|" + link.id() + "|" + link.itemId() + "|" + link.expiresAt().getEpochSecond());
    }

    @Override
    public String url(PreviewLink link) {
        return base + token(link);
    }

    byte[] secret() {
        if (secret == null) {
            var key = configured == null || configured.isBlank() ? repository.signingKey() : configured;
            secret = key.getBytes(StandardCharsets.UTF_8);
        }
        return secret;
    }

    String sign(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret(), "HmacSHA256"));
            var sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig).substring(0, 22);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
