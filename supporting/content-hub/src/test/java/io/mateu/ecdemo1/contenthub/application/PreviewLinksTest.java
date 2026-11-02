package io.mateu.ecdemo1.contenthub.application;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.PreviewLinkRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.PreviewLinks;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLinkStatus;
import io.mateu.ecdemo1.contenthub.infra.in.web.PreviewLinksSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
 * Preview links: editors make and revoke them, anyone logged in opens them with a signed token; a view is
 * recorded only when the draft is shown.
 */
class PreviewLinksTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.EDIT), null);
    static final Reader PUBLISHER = new Reader("pablo", "Pablo", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.PUBLISH), null);
    static final Reader READER = new Reader("ana", "Ana", EmployeeAttributes.NONE, EnumSet.noneOf(EditorialPermission.class), null);
    static final Reader ANONYMOUS = new Reader("anonymous", "anonymous", EmployeeAttributes.NONE,
            EnumSet.noneOf(EditorialPermission.class), null);

    final Map<String, Item> items = new LinkedHashMap<>();
    final Map<String, PreviewLink> links = new LinkedHashMap<>();
    final MutableClock clock = new MutableClock(NOW);
    PreviewLinks previews;
    PreviewLinksSigner signer;

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setUp() {
        var itemRepo = mock(ItemRepository.class);
        when(itemRepo.find(any())).thenAnswer(i -> Optional.ofNullable(items.get(i.<String>getArgument(0))));
        var repo = mock(PreviewLinkRepository.class);
        when(repo.find(any())).thenAnswer(i -> Optional.ofNullable(links.get(i.<String>getArgument(0))));
        when(repo.ofItem(any())).thenAnswer(i -> links.values().stream().filter(l -> l.itemId().equals(i.getArgument(0))).toList());
        doAnswer(i -> links.put(i.<PreviewLink>getArgument(0).id(), i.getArgument(0))).when(repo).save(any());
        when(repo.signingKey()).thenReturn("kept-key");
        signer = new PreviewLinksSigner("", repo, "http://ec1.localhost:8800/intranet");
        previews = new PreviewLinks(repo, itemRepo, signer, clock);
        items.put("i1", Item.create("i1", ItemType.NOTICE, "eva", "es", new ItemContent("s1", null,
                Map.of("es", new Translation("Cierre de la piscina", "", "<p>x</p>")), List.of(), null, null, false, false,
                null, null), NOW));
    }

    String token(PreviewLink link) {
        return previews.url(link).substring(previews.url(link).lastIndexOf('/') + 1);
    }

    @Test
    void an_editor_or_a_publisher_makes_one_a_reader_does_not() {
        var link = previews.create(EDITOR, "i1", 7, null, null);
        assertThat(previews.url(link)).startsWith("http://ec1.localhost:8800/intranet/vista-previa/" + link.id() + ".");
        assertThat(previews.create(PUBLISHER, "i1", 2, "h1", "HOTEL=TFS01").viewAsLabel()).isEqualTo("HOTEL=TFS01");
        assertThatThrownBy(() -> previews.create(READER, "i1", 7, null, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> previews.revoke(READER, link.id())).isInstanceOf(SecurityException.class);
        assertThat(previews.ofItem("i1")).hasSize(2);
    }

    @Test
    void anyone_logged_in_opens_it_and_the_view_is_recorded() {
        var link = previews.create(EDITOR, "i1", 7, null, null);
        var opened = previews.open(READER, token(link), true);
        assertThat(opened.shown()).isTrue();
        assertThat(opened.item().id()).isEqualTo("i1");
        previews.open(READER, token(link), false); // a redraw, not a visit
        assertThat(links.get(link.id()).viewCount()).isEqualTo(1);
        assertThat(links.get(link.id()).views()).extracting(PreviewLink.View::userId).containsExactly("ana");
    }

    @Test
    void without_a_login_nothing_is_shown() {
        var link = previews.create(EDITOR, "i1", 7, null, null);
        assertThatThrownBy(() -> previews.open(ANONYMOUS, token(link), true)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> previews.open(null, token(link), true)).isInstanceOf(SecurityException.class);
        assertThat(links.get(link.id()).viewCount()).isZero();
    }

    @Test
    void a_token_not_signed_here_shows_nothing() {
        var link = previews.create(EDITOR, "i1", 7, null, null);
        assertThat(previews.open(READER, link.id(), true)).isNull();
        assertThat(previews.open(READER, link.id() + ".AAAAAAAAAAAAAAAAAAAAAA", true)).isNull();
        assertThat(previews.open(READER, "nope.x", true)).isNull();
        assertThat(previews.open(READER, null, true)).isNull();
        // another key signs differently
        var other = new PreviewLinksSigner("another-secret", mock(PreviewLinkRepository.class), "http://x/intranet");
        assertThat(previews.open(READER, other.token(link), true)).isNull();
    }

    @Test
    void expired_revoked_or_published_it_says_why_and_counts_nothing() {
        var expiring = previews.create(EDITOR, "i1", 1, null, null);
        var revoked = previews.create(EDITOR, "i1", 7, null, null);
        previews.revoke(PUBLISHER, revoked.id());
        clock.now = NOW.plus(Duration.ofDays(2));
        assertThat(previews.open(READER, token(expiring), true).status()).isEqualTo(PreviewLinkStatus.EXPIRED);
        assertThat(previews.open(READER, token(revoked), true).status()).isEqualTo(PreviewLinkStatus.REVOKED);

        var beforePublishing = previews.create(EDITOR, "i1", 7, null, null);
        items.get("i1").publish("pablo", true, false, clock.now.plusSeconds(1));
        clock.now = clock.now.plusSeconds(2);
        var opened = previews.open(READER, token(beforePublishing), true);
        assertThat(opened.status()).isEqualTo(PreviewLinkStatus.PUBLISHED);
        assertThat(opened.shown()).isFalse();
        assertThat(links.values()).allSatisfy(l -> assertThat(l.viewCount()).isZero());
    }

    @Test
    void with_no_secret_configured_it_signs_with_the_kept_key_so_links_outlive_a_restart() {
        var link = previews.create(EDITOR, "i1", 7, null, null);
        var repo = mock(PreviewLinkRepository.class);
        when(repo.signingKey()).thenReturn("kept-key");
        var afterRestart = new PreviewLinksSigner(" ", repo, "http://ec1.localhost:8800/intranet");
        assertThat(afterRestart.token(link)).isEqualTo(signer.token(link));
    }
}
