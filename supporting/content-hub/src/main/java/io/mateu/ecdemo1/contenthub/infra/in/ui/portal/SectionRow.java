package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.PrimaryColumn;
import io.mateu.uidl.annotations.Hidden;

public record SectionRow(@Hidden String id, @Label("Sección") @PrimaryColumn(caption = "path") String name, @Hidden @Label("Dentro de") String path) {
}
