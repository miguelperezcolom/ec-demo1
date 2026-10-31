package io.mateu.ecdemo1.contenthub.infra.in.web;

import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * The slot remote, as plain JSON until Mateu's server-resolved microfrontend (and {@code @ContentSlot})
 * ships: an application's server asks with the slot's key and the user's token; the answer is the
 * visible variant with the highest priority, its HTML already expanded — or 204, and the application
 * draws its fallback. {@code cacheKey} is the profile hash: two users with the same one get the same
 * answer.
 */
@RestController
@RequiredArgsConstructor
public class SlotController {

    final Readers readers;
    final Visibility visibility;
    final ContentRenderer renderer;

    @GetMapping({"/_intranet/api/slots/{key}", "/api/slots/{key}"})
    public ResponseEntity<Map<String, Object>> slot(@PathVariable String key, HttpServletRequest request) {
        var reader = readers.real(request::getHeader);
        var hash = reader.attributes().hash();
        return visibility.slot(reader.attributes(), key)
                .map(v -> ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePrivate())
                        .header("X-Content-Cache-Key", hash)
                        .body(Map.<String, Object>of("key", key, "variant", v.variantId(), "priority", v.priority(),
                                "title", v.title() == null ? "" : v.title(),
                                "html", renderer.body(v.html(), PageRenderer.PORTAL_BASE, hash),
                                "link", v.linkUrl() == null ? "" : v.linkUrl(), "cacheKey", hash)))
                .orElseGet(() -> ResponseEntity.noContent().header("X-Content-Cache-Key", hash).build());
    }
}
