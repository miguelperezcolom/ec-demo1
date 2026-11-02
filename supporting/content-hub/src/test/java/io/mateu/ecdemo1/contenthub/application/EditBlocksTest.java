package io.mateu.ecdemo1.contenthub.application;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The visual editor's save: the page draft's blocks replaced, the rest of the draft untouched. */
class EditBlocksTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.EDIT), null);
    static final Reader PUBLISHER = new Reader("pablo", "Pablo", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.PUBLISH), null);

    final Map<String, Item> stored = new HashMap<>();
    ItemCommands commands;

    @BeforeEach
    void setUp() {
        var items = mock(ItemRepository.class);
        when(items.find(any())).thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
        doAnswer(i -> stored.put(i.<Item>getArgument(0).id(), i.getArgument(0))).when(items).save(any());
        commands = new ItemCommands(items, mock(DomainEvents.class), mock(EmployeeDirectory.class), mock(NamedAudiences.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var pageAudience = Audience.in(Attribute.HOTEL, "TFS01");
        stored.put("p1", Item.create("p1", ItemType.PAGE, "eva", "es", new ItemContent("s1", pageAudience,
                Map.of("es", new Translation("Recepción", "Resumen", "<p>cuerpo</p>")),
                List.of(block("b1", "Viejo", null)), null, null, false, false, null, null), NOW.minusSeconds(60)));
        stored.put("a1", Item.create("a1", ItemType.ARTICLE, "eva", "es", new ItemContent("s1", Audience.EVERYONE,
                Map.of("es", new Translation("Noticia", "", "")), List.of(), null, null, false, false, null, null), NOW));
    }

    static Block block(String id, String title, Audience audience) {
        return new Block(id, BlockType.HTML, audience, title, "<p>" + title + "</p>", null, null, null, null, null);
    }

    @Test
    void replaces_the_blocks_in_order_and_keeps_the_rest_of_the_draft() {
        var narrowed = Audience.in(Attribute.DEPARTMENT, "RECEPCION");
        commands.editBlocks(EDITOR, "p1", List.of(
                new Block("b2", BlockType.HTML, narrowed, "Nuevo", "<p>hola<script>alert(1)</script> [[media:m1]]</p>", null, null, null, null, null),
                block("b1", "Viejo", null)));
        var page = stored.get("p1");
        var draft = page.draft();
        assertThat(draft.blocks()).extracting(Block::id).containsExactly("b2", "b1");
        assertThat(draft.blocks().getFirst().html()).doesNotContain("script").contains("[[media:m1]]");
        assertThat(draft.blocks().getFirst().audience()).isEqualTo(narrowed);
        assertThat(draft.audience()).as("the page's audience stays").isEqualTo(Audience.in(Attribute.HOTEL, "TFS01"));
        assertThat(draft.translations().get("es").title()).isEqualTo("Recepción");
        assertThat(page.updatedAt()).isEqualTo(NOW);
        assertThat(page.status()).isEqualTo(ItemStatus.DRAFT);
    }

    @Test
    void a_block_cannot_bring_an_audience_that_is_not_a_narrowing() {
        assertThatThrownBy(() -> commands.editBlocks(EDITOR, "p1",
                List.of(block("b1", "Todos", new Audience.Named("todo-el-mundo")))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("estrechar");
        assertThatThrownBy(() -> commands.editBlocks(EDITOR, "p1", List.of(block("b1", "OR",
                Audience.anyOf(Audience.in(Attribute.HOTEL, "CUN01"), Audience.in(Attribute.DEPARTMENT, "RECEPCION"))))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stored.get("p1").draft().blocks()).extracting(Block::title).containsExactly("Viejo");
    }

    @Test
    void only_pages_have_blocks_and_only_editors_change_them() {
        assertThatThrownBy(() -> commands.editBlocks(EDITOR, "a1", List.of(block("b1", "x", null))))
                .hasMessageContaining("páginas");
        assertThatThrownBy(() -> commands.editBlocks(PUBLISHER, "p1", List.of()))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> commands.editBlocks(EDITOR, "nope", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void two_blocks_with_the_same_id_are_refused_when_published_as_the_form_does() {
        commands.editBlocks(EDITOR, "p1", List.of(block("b1", "uno", null), block("b1", "dos", null)));
        assertThatThrownBy(() -> commands.publish(new Reader("pablo", "Pablo", EmployeeAttributes.NONE,
                EnumSet.of(EditorialPermission.PUBLISH), null), "p1", false))
                .hasMessageContaining("mismo id");
    }
}
