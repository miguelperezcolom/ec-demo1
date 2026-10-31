package io.mateu.ecdemo1.contenthub.infra.in.web;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.infra.in.render.MediaUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/**
 * Serves media on the URLs {@link MediaUrls} signed, and the embed script. A signature proves the page
 * was drawn for that profile; the visibility is checked again here — the item may have been retired or
 * expired since. A {@link Resource} body, so Spring answers Range requests and videos can seek.
 */
@RestController
@RequiredArgsConstructor
public class MediaController {

    final MediaUrls urls;
    final MediaRepository media;
    final ContentIndex index;
    final Clock clock;

    @GetMapping("/_intranet/media/{id}")
    public ResponseEntity<Resource> media(@PathVariable String id, @RequestParam("h") String hash,
                                          @RequestParam("e") long expires, @RequestParam("s") String signature) {
        if (!urls.valid(id, hash, expires, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!MediaUrls.EDITOR.equals(hash) && !index.isMediaVisible(id, hash, clock.instant())) {
            return ResponseEntity.notFound().build();
        }
        var asset = media.find(id).orElse(null);
        var bytes = media.bytes(id).orElse(null);
        if (asset == null || bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", ContentDisposition.inline()
                        .filename(asset.fileName() == null ? id : asset.fileName(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping(value = "/_intranet/embed.js", produces = "text/javascript")
    public ResponseEntity<Resource> embedScript() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/javascript"))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(new ClassPathResource("static/embed.js"));
    }
}
