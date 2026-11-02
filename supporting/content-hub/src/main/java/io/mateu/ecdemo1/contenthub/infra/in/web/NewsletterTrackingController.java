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
    /** Where an item lives (a newsletter's own items, under its site); null in tests: the intranet's page. */
    final io.mateu.ecdemo1.contenthub.application.out.ItemRepository items;
    final io.mateu.ecdemo1.contenthub.application.out.SiteRepository sites;
    final io.mateu.ecdemo1.contenthub.application.out.MediaRepository media;

    public NewsletterTrackingController(NewsletterLinksSigner signer, NewsletterTracking tracking,
                                        @Value("${content-hub.portal-url:http://ec1.localhost:8800/intranet}") String portalUrl) {
        this(signer, tracking, portalUrl, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NewsletterTrackingController(NewsletterLinksSigner signer, NewsletterTracking tracking,
                                        @Value("${content-hub.portal-url:http://ec1.localhost:8800/intranet}") String portalUrl,
                                        io.mateu.ecdemo1.contenthub.application.out.ItemRepository items,
                                        io.mateu.ecdemo1.contenthub.application.out.SiteRepository sites,
                                        io.mateu.ecdemo1.contenthub.application.out.MediaRepository media) {
        this.signer = signer;
        this.tracking = tracking;
        this.portalUrl = portalUrl;
        this.items = items;
        this.sites = sites;
        this.media = media;
    }

    /**
     * A newsletter's logo, for its email and its web version: the image of the library the newsletter names.
     * No signature (an email is read weeks later, and the logo is the newsletter's public face); only a
     * newsletter's logo, nothing else of the library.
     */
    @GetMapping("/_intranet/n/logo/{site}")
    public ResponseEntity<byte[]> logo(@PathVariable String site) {
        if (sites == null || media == null || !site.matches("[a-z0-9-]{1,60}")) {
            return ResponseEntity.notFound().build();
        }
        var mediaId = sites.find(site).filter(s -> s.kind() == io.mateu.ecdemo1.contenthub.domain.site.Site.Kind.NEWSLETTER)
                .map(s -> s.definition().logoMediaId()).orElse(null);
        var asset = mediaId == null ? null : media.find(mediaId).orElse(null);
        var bytes = asset == null || asset.kind() != io.mateu.ecdemo1.contenthub.domain.media.MediaKind.IMAGE ? null
                : media.bytes(mediaId).orElse(null);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.contentType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1))).body(bytes);
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
        var site = items == null ? null : items.find(item).map(i -> i.siteId()).orElse(null);
        var path = site == null || io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET.equals(site) ? Ui.itemLink(item)
                : Ui.PORTAL + "/n/" + site + "/p/" + item;
        var target = portalUrl.replaceFirst("/intranet/?$", "") + path;
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(target).toString()).build();
    }
}
