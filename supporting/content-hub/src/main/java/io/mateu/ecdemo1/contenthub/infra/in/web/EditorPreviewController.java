package io.mateu.ecdemo1.contenthub.infra.in.web;

import io.mateu.ecdemo1.contenthub.application.usecases.PreviewLinks;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * Where the visual editor's «Vista previa» goes. Only the path of the link: opened from the host the editor is on,
 * the page gets that host's renderer, which is the point. A signed link is the one the portal already draws a draft for.
 */
@RestController
@RequestMapping("/_intranet/api/editor")
@RequiredArgsConstructor
public class EditorPreviewController {

    public record PreviewLinkRequest(String viewAs) {
    }

    final Readers readers;
    final PreviewLinks previewLinks;
    final AudienceCommands audiences;

    @PostMapping("/pages/{id}/preview-link")
    public Map<String, String> previewLink(@PathVariable String id, @RequestBody PreviewLinkRequest body, HttpServletRequest request) {
        var reader = readers.real(request::getHeader);
        var viewAs = body.viewAs() == null || body.viewAs().isBlank() ? null : body.viewAs();
        var profile = viewAs == null ? null : audiences.allProfiles().get(viewAs);
        if (viewAs != null && profile == null) {
            throw new IllegalArgumentException("Ese perfil ya no está en el directorio");
        }
        var link = previewLinks.forEditor(reader, id, viewAs, profile == null ? null : profile.attributes().canonical().replace(";", " · "));
        return Map.of("url", URI.create(previewLinks.url(link)).getPath());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(SecurityException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> refused(SecurityException e) {
        return org.springframework.http.ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> wrong(IllegalArgumentException e) {
        return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
