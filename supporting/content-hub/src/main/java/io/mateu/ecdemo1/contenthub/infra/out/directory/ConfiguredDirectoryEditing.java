package io.mateu.ecdemo1.contenthub.infra.out.directory;

import io.mateu.ecdemo1.contenthub.application.out.DirectoryEditing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/** A URL template with {@code {id}} and the scope it takes. No template: nobody edits from here. */
@Component
public class ConfiguredDirectoryEditing implements DirectoryEditing {

    final String urlTemplate;
    final String scope;

    public ConfiguredDirectoryEditing(@Value("${content-hub.directory.edit-url:}") String urlTemplate,
                                      @Value("${content-hub.directory.edit-scope:people:edit}") String scope) {
        this.urlTemplate = urlTemplate;
        this.scope = scope;
    }

    @Override
    public Optional<String> linkFor(Set<String> scopes, String userId) {
        if (urlTemplate.isBlank() || !scopes.contains(scope)) {
            return Optional.empty();
        }
        return Optional.of(urlTemplate.replace("{id}", URLEncoder.encode(userId, StandardCharsets.UTF_8)));
    }
}
