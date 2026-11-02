package io.mateu.ecdemo1.contenthub.application.health;

import io.mateu.core.infra.HeadlessHttpRequest;
import io.mateu.core.infra.declarative.orchestrators.crud.CapabilityCrud;
import io.mateu.dtos.RunActionRqDto;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.HomePages;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MediaDescriptions;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.out.MenuRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.ecdemo1.contenthub.application.out.SurveyRepository;
import io.mateu.ecdemo1.contenthub.application.out.TemplateRepository;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.application.out.UsageRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.health.Finding;
import io.mateu.ecdemo1.contenthub.domain.health.HealthCheck;
import io.mateu.ecdemo1.contenthub.domain.navigation.Navigation;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice.FindingPage;
import io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice.HealthListing;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.interfaces.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.mateu.ecdemo1.contenthub.application.health.HealthFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The listing's toolbar buttons the way the shells press them. «Salud del contenido» is Navigable, so Mateu
 * bridges it into a crud (CapabilityCrud) and a toolbar button arrives as {@code action-on-row-<method>}:
 * the method is invoked, not the listing's handleAction. With no-op methods the button "worked" in the
 * browser and nothing ran — this goes through the same dispatch.
 */
class HealthListingThroughMateuTest {

    HealthFixtures.InMemoryHealth store;
    ContentHealth health;
    HealthListing listing;
    Reader reader = new Reader("demo", "Demo", EmployeeAttributes.NONE, Set.of(EditorialPermission.EDIT), null);

    @BeforeEach
    void wire() {
        store = new HealthFixtures.InMemoryHealth();
        var navigation = mock(NavigationRepository.class);
        when(navigation.get(anyString())).thenReturn(Navigation.empty());
        var sources = new ContentHealth.Sources(mock(ItemRepository.class), mock(MediaRepository.class),
                mock(MediaDescriptions.class), mock(SlotRepository.class), mock(HomePages.class), navigation,
                mock(MenuRepository.class), UiSources.NONE, mock(EmployeeDirectory.class), mock(SurveyRepository.class),
                mock(NewsletterRepository.class), mock(NewsletterIssueRepository.class), mock(SiteRepository.class),
                mock(UsageRepository.class), mock(TemplateRepository.class));
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        health = new ContentHealth(sources, store, new LinkChecker((uri, method, timeout, ua) -> {
            throw new java.io.IOException("no network in tests");
        }, store, HealthSettings.defaults(), clock, d -> { }), null, HealthSettings.defaults(), Set.of(), clock,
                Runnable::run);
        var readers = mock(Readers.class);
        when(readers.of(any(HttpRequest.class))).thenAnswer(i -> reader);
        @SuppressWarnings("unchecked")
        ObjectProvider<FindingPage> pages = mock(ObjectProvider.class);
        listing = new HealthListing(health, readers, pages);
    }

    Object press(String actionId, Map<String, Object> componentState) {
        var request = new HeadlessHttpRequest(RunActionRqDto.builder().actionId(actionId).route("/editorial/salud")
                .consumedRoute("").componentState(componentState).appState(Map.of()).parameters(Map.of())
                .initiatorComponentId("ux_salud").build())
                // who the caller is, as EditorialIdentity (the app's PrincipalResolver) tells Mateu: the class's
                // @Editors is checked on every action, as in the browser
                .withAttribute("mateu.callerIdentity", new io.mateu.uidl.security.CallerIdentity(reader.userId(), List.of(),
                        List.of(), List.of(), reader.permissions().stream().map(EditorialPermission::scope).toList()));
        var crud = CapabilityCrud.bridgeIfNeeded(listing);
        assertThat(crud).as("Mateu bridges a Navigable listing into a crud").isInstanceOf(CapabilityCrud.class);
        return ((CapabilityCrud) crud).handleAction(actionId, request);
    }

    static List<?> parts(Object answer) {
        return answer instanceof Collection<?> c ? List.copyOf(c) : List.of(answer);
    }

    @Test
    void comprobar_ahora_runs_the_checks_and_says_so() {
        var answer = parts(press("action-on-row-comprobarAhora", Map.of()));

        assertThat(store.runs).as("the run is recorded").hasSize(1);
        assertThat(store.lastRun()).hasValueSatisfying(r -> {
            assertThat(r.trigger()).isEqualTo("ON_DEMAND");
            assertThat(r.by()).isEqualTo("demo");
            assertThat(r.finishedAt()).isNotNull();
            assertThat(r.error()).isNull();
        });
        assertThat(answer).anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(Message.class,
                m -> assertThat(m.text()).startsWith("Comprobación en marcha")));
        assertThat(answer).anySatisfy(p -> assertThat(p).isInstanceOf(UICommand.class));
    }

    @Test
    void without_content_edit_it_says_why_and_runs_nothing() {
        reader = new Reader("pablo", "Pablo", EmployeeAttributes.NONE, Set.of(EditorialPermission.PUBLISH), null);
        var answer = parts(press("action-on-row-comprobarAhora", Map.of()));
        assertThat(store.runs).isEmpty();
        assertThat(answer).singleElement().isInstanceOfSatisfying(Message.class,
                m -> assertThat(m.text()).contains("content:edit"));
    }

    @Test
    void marcar_como_revisados_snoozes_the_selected_rows() {
        var f = Finding.of(HealthCheck.BROKEN_LINKS, Finding.Subject.ITEM, "a", "A", "x", "roto", Finding.Fix.EDIT_ITEM);
        store.replaceFindings(List.of(f), NOW);

        var answer = parts(press("action-on-row-marcarComoRevisados",
                Map.of("crud_selected_items", List.of(Map.of("id", f.key(), "problem", "roto")))));

        assertThat(store.snoozes()).containsKey(f.key());
        assertThat(answer).anySatisfy(p -> assertThat(p).isInstanceOfSatisfying(Message.class,
                m -> assertThat(m.text()).contains("1 aviso marcado")));
    }
}
