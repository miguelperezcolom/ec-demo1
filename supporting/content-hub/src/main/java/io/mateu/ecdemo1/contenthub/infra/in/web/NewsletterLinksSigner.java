package io.mateu.ecdemo1.contenthub.infra.in.web;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterLinks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * The newsletter's tracked links, signed so that nobody counts opens or clicks that did not happen:
 * {@code /_intranet/n/o/{edition}/{group}/{sig}.gif} and {@code /_intranet/n/c/{edition}/{group}/{item}/{sig}}.
 * They do not expire (an email is read weeks later). With no stable secret configured
 * ({@code content-hub.media.secret}) a restart invalidates the old ones: their clicks still reach the
 * item, uncounted.
 */
@Component
public class NewsletterLinksSigner implements NewsletterLinks {

    final byte[] secret;
    final String base;

    public NewsletterLinksSigner(@Value("${content-hub.media.secret:}") String secret,
                                 @Value("${content-hub.portal-url:http://ec1.localhost:8800/intranet}") String portalUrl) {
        this.secret = (secret == null || secret.isBlank() ? "content-hub-newsletter" + System.nanoTime() : secret)
                .getBytes(StandardCharsets.UTF_8);
        // the portal's host: the gateway routes /_intranet/** there to this service
        this.base = portalUrl.replaceFirst("/intranet/?$", "") + "/_intranet/n";
    }

    @Override
    public String open(String editionId, String group) {
        return base + "/o/" + enc(editionId) + "/" + enc(group) + "/" + sign(editionId, group, "") + ".gif";
    }

    @Override
    public String click(String editionId, String group, String itemId, String direct) {
        return base + "/c/" + enc(editionId) + "/" + enc(group) + "/" + enc(itemId) + "/" + sign(editionId, group, itemId);
    }

    public boolean valid(String editionId, String group, String itemId, String signature) {
        return signature != null && MessageDigest.isEqual(sign(editionId, group, itemId == null ? "" : itemId)
                .getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    String sign(String editionId, String group, String itemId) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            var sig = mac.doFinal(("newsletter|" + editionId + "|" + group + "|" + itemId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig).substring(0, 22);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
