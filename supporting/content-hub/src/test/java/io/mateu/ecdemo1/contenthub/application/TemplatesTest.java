package io.mateu.ecdemo1.contenthub.application;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.TemplateRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.Templates;
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
import io.mateu.ecdemo1.contenthub.domain.template.PageTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Templates: publishers define them (by hand or from a page), editors make pages from them — an ordinary
 * PAGE draft with the template's blocks and default audience.
 */
class TemplatesTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.EDIT), null);
    static final Reader PUBLISHER = new Reader("pablo", "Pablo", EmployeeAttributes.NONE,
            EnumSet.of(EditorialPermission.EDIT, EditorialPermission.PUBLISH), null);
    static final Reader ADMIN = new Reader("demo", "Demo", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.ADMIN), null);
    static final Reader NOBODY = new Reader("ana", "Ana", EmployeeAttributes.NONE, EnumSet.noneOf(EditorialPermission.class), null);

    final Map<String, Item> items = new LinkedHashMap<>();
    final Map<String, PageTemplate> stored = new LinkedHashMap<>();
    Templates templates;

    @BeforeEach
    void setUp() {
        var itemRepo = mock(ItemRepository.class);
        when(itemRepo.find(any())).thenAnswer(i -> Optional.ofNullable(items.get(i.<String>getArgument(0))));
        doAnswer(i -> items.put(i.<Item>getArgument(0).id(), i.getArgument(0))).when(itemRepo).save(any());
        var repo = mock(TemplateRepository.class);
        when(repo.find(any())).thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
        when(repo.findAll()).thenAnswer(i -> List.copyOf(stored.values()));
        doAnswer(i -> stored.put(i.<PageTemplate>getArgument(0).id(), i.getArgument(0))).when(repo).save(any());
        doAnswer(i -> stored.remove(i.<String>getArgument(0))).when(repo).delete(any());
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var commands = new ItemCommands(itemRepo, mock(DomainEvents.class), mock(EmployeeDirectory.class),
                mock(NamedAudiences.class), clock);
        templates = new Templates(repo, itemRepo, commands, clock);
        stored.put("t1", PageTemplate.create("t1", "Bienvenida", "Para quien llega", List.of(
                new Block("b1", BlockType.BANNER, null, "¡Hola!", "<p>Bienvenido<script>x</script></p>", null, null, null, null, null),
                new Block("b2", BlockType.LATEST, Audience.in(Attribute.HOTEL, "TFS01"), null, null, null, null, 5, null, null)),
                Audience.in(Attribute.DEPARTMENT, "RECEPCION"), "demo", NOW));
    }

    @Test
    void an_editor_makes_a_page_draft_from_a_template() {
        var id = templates.newPage(EDITOR, "t1", "Bienvenida a Tenerife", "personas", null);
        var page = items.get(id);
        assertThat(page.type()).isEqualTo(ItemType.PAGE);
        assertThat(page.status()).isEqualTo(ItemStatus.DRAFT);
        assertThat(page.author()).isEqualTo("eva");
        assertThat(page.draft().blocks()).extracting(Block::id).containsExactly("b1", "b2");
        assertThat(page.draft().blocks().getFirst().html()).as("sanitised like any draft").doesNotContain("script");
        assertThat(page.draft().audience()).isEqualTo(Audience.in(Attribute.DEPARTMENT, "RECEPCION"));
        assertThat(page.draft().sectionId()).isEqualTo("personas");
        assertThat(page.draft().translations().get("es").title()).isEqualTo("Bienvenida a Tenerife");
        // the template is copied, not linked
        assertThat(stored.get("t1").blocks()).hasSize(2);
    }

    @Test
    void making_a_page_needs_content_edit_and_an_existing_template() {
        assertThatThrownBy(() -> templates.newPage(NOBODY, "t1", "x", null, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> templates.newPage(EDITOR, "nope", "x", null, null)).hasMessageContaining("plantilla");
        assertThatThrownBy(() -> templates.newPage(EDITOR, "t1", " ", null, null)).hasMessageContaining("título");
        assertThat(items).isEmpty();
    }

    @Test
    void only_publishers_and_admins_define_templates() {
        assertThatThrownBy(() -> templates.create(EDITOR, "x", "", List.of(), null, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> templates.edit(EDITOR, "t1", "x", "", List.of(), null, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> templates.delete(EDITOR, "t1")).isInstanceOf(SecurityException.class);
        var id = templates.create(PUBLISHER, "Landing", "Campañas", List.of(), null, null);
        assertThat(stored.get(id).name()).isEqualTo("Landing");
        templates.edit(ADMIN, id, "Landing de campaña", "Campañas", List.of(), null, null);
        assertThat(stored.get(id).name()).isEqualTo("Landing de campaña");
        assertThat(stored.get(id).updatedBy()).isEqualTo("demo");
        templates.delete(PUBLISHER, id);
        assertThat(stored).doesNotContainKey(id);
    }

    @Test
    void a_publisher_saves_a_page_as_a_template() {
        items.put("p1", Item.create("p1", ItemType.PAGE, "eva", "es", new ItemContent("s1", Audience.in(Attribute.HOTEL, "CUN01"),
                Map.of("es", new Translation("Cancún", "", "")),
                List.of(new Block("x1", BlockType.QUICK_LINKS, null, "Enlaces", null, null, null, null,
                        List.of(new Block.Link("Buscar", "/intranet/buscar")), null)), null, null, false, false, null, null), NOW));
        assertThatThrownBy(() -> templates.saveFromPage(EDITOR, "p1", "x", "")).isInstanceOf(SecurityException.class);
        var id = templates.saveFromPage(PUBLISHER, "p1", "Plantilla: Cancún", "De la página de Cancún");
        var t = stored.get(id);
        assertThat(t.blocks()).extracting(Block::id).containsExactly("x1");
        assertThat(t.defaultAudience()).isEqualTo(Audience.in(Attribute.HOTEL, "CUN01"));
        // and back: a page from it carries those blocks
        var page = items.get(templates.newPage(EDITOR, id, "Cozumel", null, null));
        assertThat(page.draft().blocks().getFirst().links()).extracting(Block.Link::url).containsExactly("/intranet/buscar");
    }

    @Test
    void a_template_of_a_site_makes_pages_only_there_and_a_global_one_everywhere() {
        var own = templates.create(PUBLISHER, "Número especial", "", List.of(), null, "boletin-rrhh");
        assertThat(templates.availableIn("boletin-rrhh")).extracting(PageTemplate::id).containsExactlyInAnyOrder("t1", own);
        assertThat(templates.availableIn(null)).extracting(PageTemplate::id).containsExactly("t1");
        var page = items.get(templates.newPage(EDITOR, own, "Especial verano", null, "boletin-rrhh"));
        assertThat(page.siteId()).isEqualTo("boletin-rrhh");
        assertThat(items.get(templates.newPage(EDITOR, "t1", "Global", null, "boletin-rrhh")).siteId()).isEqualTo("boletin-rrhh");
        assertThat(items.get(templates.newPage(EDITOR, "t1", "En la intranet", null, null)).siteId()).isEqualTo("intranet");
        assertThatThrownBy(() -> templates.newPage(EDITOR, own, "x", null, "intranet")).hasMessageContaining("boletin-rrhh");
        // a newsletter page saved as a template is that newsletter's
        var fromNewsletter = templates.saveFromPage(PUBLISHER, page.id(), "De boletín", "");
        assertThat(stored.get(fromNewsletter).siteId()).isEqualTo("boletin-rrhh");
    }

    @Test
    void the_seed_does_not_overwrite_a_template_someone_changed() {
        templates.edit(PUBLISHER, "t1", "Bienvenida (cambiada)", "", List.of(), null, null);
        templates.seed(PageTemplate.create("t1", "Bienvenida", "", List.of(), null, "demo", NOW));
        templates.seed(PageTemplate.create("t2", "Otra", "", List.of(), null, "demo", NOW));
        assertThat(stored.get("t1").name()).isEqualTo("Bienvenida (cambiada)");
        assertThat(stored).containsKey("t2");
    }
}
