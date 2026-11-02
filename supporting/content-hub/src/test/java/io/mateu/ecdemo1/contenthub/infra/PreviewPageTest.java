package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.PreviewLinkRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.PreviewLinks;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PreviewPage;
import io.mateu.ecdemo1.contenthub.infra.in.web.PreviewLinksSigner;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.EmptyState;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The portal page a preview link opens (/intranet/vista-previa/{token}): the draft, marked as such, for
 * anyone logged in; nothing without a login, a forged token, or a link that expired, was revoked or whose
 * item was published.
 */
class PreviewPageTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.EDIT), null);

    final Map<String, Item> items = new LinkedHashMap<>();
    final Map<String, PreviewLink> links = new LinkedHashMap<>();
    Instant now = NOW;
    PreviewLinks previews;
    PreviewLinksSigner signer;
    Readers readers;
    AudienceCommands audiences;
    NamedAudiences named;
    PageRenderer pages;

    @BeforeEach
    void setUp() {
        var itemRepo = mock(ItemRepository.class);
        when(itemRepo.find(any())).thenAnswer(i -> Optional.ofNullable(items.get(i.<String>getArgument(0))));
        var repo = mock(PreviewLinkRepository.class);
        when(repo.find(any())).thenAnswer(i -> Optional.ofNullable(links.get(i.<String>getArgument(0))));
        doAnswer(i -> links.put(i.<PreviewLink>getArgument(0).id(), i.getArgument(0))).when(repo).save(any());
        signer = new PreviewLinksSigner("secret", repo, "http://ec1.localhost:8800/intranet");
        var clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
        previews = new PreviewLinks(repo, itemRepo, signer, clock);
        readers = new Readers(mock(EmployeeDirectory.class), mock(ContentIndex.class));
        audiences = mock(AudienceCommands.class);
        when(audiences.allProfiles()).thenReturn(Map.of("h-cun", new AudienceCommands.Profile("h-cun",
                new EmployeeAttributes(Map.of(Attribute.HOTEL, "CUN01"), "es"), 3)));
        named = mock(NamedAudiences.class);
        when(named.policy()).thenReturn(new VisibilityPolicy(id -> null));
        when(named.labels()).thenReturn(Function.identity());
        pages = mock(PageRenderer.class);
        when(pages.draftHtml(any(), any())).thenAnswer(i -> "<p>drawn " + i.<Block>getArgument(1).id() + "</p>");
        items.put("p1", Item.create("p1", ItemType.PAGE, "eva", "es", new ItemContent("s1", Audience.EVERYONE,
                Map.of("es", new Translation("Bienvenida a Tenerife", "Resumen", "")),
                List.of(new Block("todos", BlockType.HTML, null, "Para todos", "<p>a</p>", null, null, null, null, null),
                        new Block("tfs", BlockType.HTML, Audience.in(Attribute.HOTEL, "TFS01"), "Solo TFS", "<p>b</p>",
                                null, null, null, null, null)),
                null, null, false, false, null, null), NOW));
    }

    static String bearer(String user) {
        var enc = Base64.getUrlEncoder().withoutPadding();
        return "Bearer " + enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(("{\"preferred_username\":\"" + user + "\"}").getBytes(StandardCharsets.UTF_8)) + ".x";
    }

    Form open(String token, String authorization) {
        var request = mock(HttpRequest.class);
        when(request.lastPathItem()).thenReturn(token);
        when(request.getHeaderValue(eq("Authorization"))).thenReturn(authorization);
        var page = new PreviewPage(readers, previews, audiences, named, pages, mock(ContentRenderer.class));
        return (Form) page.component(request);
    }

    String token(PreviewLink link) {
        return signer.token(link);
    }

    static String emptyTitle(Form form) {
        return form.content().stream().filter(c -> c instanceof EmptyState).map(c -> ((EmptyState) c).title())
                .findFirst().orElse(null);
    }

    static List<String> notices(Form form) {
        return form.content().stream().filter(c -> c instanceof Notice).map(c -> ((Notice) c).text()).toList();
    }

    static List<String> blocks(Form form) {
        return form.content().stream().filter(c -> c instanceof Element).map(String::valueOf)
                .filter(s -> s.contains("drawn")).toList();
    }

    @Test
    void a_logged_in_reader_sees_the_draft_marked_as_a_preview_and_the_view_is_recorded() {
        var link = previews.create(EDITOR, "p1", 7, null, null);
        var form = open(token(link), bearer("ana"));
        assertThat(form.title()).isEqualTo("Bienvenida a Tenerife");
        assertThat(form.overline()).isEqualTo(PreviewPage.BANNER);
        assertThat(notices(form).getFirst()).startsWith("Borrador — vista previa");
        // without a profile, every block, the narrowed one saying for whom
        assertThat(blocks(form)).hasSize(2);
        assertThat(blocks(form).get(1)).contains("Este bloque solo lo ve");
        assertThat(links.get(link.id()).views()).extracting(PreviewLink.View::userId).containsExactly("ana");
    }

    @Test
    void as_a_profile_it_leaves_out_what_that_profile_would_not_see() {
        var link = previews.create(EDITOR, "p1", 7, "h-cun", "HOTEL=CUN01");
        var form = open(token(link), bearer("ana"));
        assertThat(blocks(form)).hasSize(1).first().asString().contains("drawn todos");
        assertThat(notices(form)).anyMatch(n -> n.contains("HOTEL=CUN01"));
    }

    @Test
    void without_a_login_it_shows_nothing_and_counts_nothing() {
        var link = previews.create(EDITOR, "p1", 7, null, null);
        var form = open(token(link), null);
        assertThat(emptyTitle(form)).isEqualTo("Inicia sesión");
        assertThat(form.content()).noneMatch(c -> c instanceof Element);
        assertThat(links.get(link.id()).viewCount()).isZero();
    }

    @Test
    void a_forged_or_unknown_token_shows_nothing() {
        var link = previews.create(EDITOR, "p1", 7, null, null);
        assertThat(emptyTitle(open(link.id() + ".forgedforgedforgedforg", bearer("ana")))).contains("no válido");
        assertThat(emptyTitle(open("whatever", bearer("ana")))).contains("no válido");
    }

    @Test
    void expired_revoked_and_published_links_say_why() {
        var expired = previews.create(EDITOR, "p1", 1, null, null);
        var revoked = previews.create(EDITOR, "p1", 7, null, null);
        previews.revoke(EDITOR, revoked.id());
        var published = previews.create(EDITOR, "p1", 7, null, null);
        now = NOW.plus(Duration.ofDays(2));
        assertThat(emptyTitle(open(token(expired), bearer("ana")))).contains("caducado");
        assertThat(emptyTitle(open(token(revoked), bearer("ana")))).contains("revocado");
        items.get("p1").publish("pablo", true, false, now);
        assertThat(emptyTitle(open(token(published), bearer("ana")))).contains("ya está publicado");
        assertThat(links.values()).allSatisfy(l -> assertThat(l.viewCount()).isZero());
    }
}
