package io.mateu.ecdemo1.contenthub.infra.out.directory;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredDirectoryEditingTest {

    @Test
    void the_link_is_for_who_holds_the_scope_and_only_if_it_is_configured() {
        var editing = new ConfiguredDirectoryEditing("https://x/personas/{id}", "people:edit");

        assertThat(editing.linkFor(Set.of("people:edit"), "ana.perez")).contains("https://x/personas/ana.perez");
        assertThat(editing.linkFor(Set.of("content:edit"), "ana.perez")).isEmpty();
        assertThat(new ConfiguredDirectoryEditing("", "people:edit").linkFor(Set.of("people:edit"), "ana")).isEmpty();
    }
}
