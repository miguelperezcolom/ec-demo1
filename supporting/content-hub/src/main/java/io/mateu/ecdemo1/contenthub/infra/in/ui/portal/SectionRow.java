package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;

public record SectionRow(@Label("Id") String id, @Label("Sección") String name, @Label("Dentro de") String path) {
}
