package io.mateu.ecdemo1.contenthub.infra.in.web;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterTracking;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Base64;

/**
 * Where a newsletter's pixel and links land (the gateway lets {@code /_intranet/n/**} through without a
 * token: an email client fetches the pixel, a click comes from the mail app). A valid signature counts
 * the open or the click for the edition and group; a click always goes on to the item in the portal —
 * only ever there, so this is no open redirect — which then asks for a session as usual.
 */
@RestController
public class NewsletterTrackingController {

    static final byte[] PIXEL = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    final NewsletterLinksSigner signer;
    final NewsletterTracking tracking;
    final String portalUrl;

    public NewsletterTrackingController(NewsletterLinksSigner signer, NewsletterTracking tracking,
                                        @Value("${content-hub.portal-url:http://ec1.localhost:8800/intranet}") String portalUrl) {
        this.signer = signer;
        this.tracking = tracking;
        this.portalUrl = portalUrl;
    }

    @GetMapping("/_intranet/n/o/{edition}/{group}/{signature}.gif")
    public ResponseEntity<byte[]> open(@PathVariable String edition, @PathVariable String group,
                                       @PathVariable String signature) {
        if (signer.valid(edition, group, null, signature)) {
            tracking.record(new NewsletterTracking.Event(edition, group, NewsletterTracking.Kind.OPEN, null));
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_GIF).cacheControl(CacheControl.noStore()).body(PIXEL);
    }

    @GetMapping("/_intranet/n/c/{edition}/{group}/{item}/{signature}")
    public ResponseEntity<Void> click(@PathVariable String edition, @PathVariable String group,
                                      @PathVariable String item, @PathVariable String signature) {
        if (!item.matches("[A-Za-z0-9._-]{1,120}")) {
            return ResponseEntity.notFound().build();
        }
        if (signer.valid(edition, group, item, signature)) {
            tracking.record(new NewsletterTracking.Event(edition, group, NewsletterTracking.Kind.CLICK, item));
        }
        var target = portalUrl.replaceFirst("/intranet/?$", "") + Ui.itemLink(item);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(target).toString()).build();
    }
}
