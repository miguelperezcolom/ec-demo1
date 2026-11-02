package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.directory.DirectoryUpdates;
import io.mateu.ecdemo1.contenthub.application.directory.EmployeeMessage;
import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.events.ProjectionListener;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.Collaboration;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.Surveys;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.application.visibility.Projection;
import io.mateu.ecdemo1.contenthub.application.visibility.ProjectionCache;
import io.mateu.ecdemo1.contenthub.application.visibility.SurveyProjection;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.collaboration.Discussion;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.shared.CollaborationEvents;
import io.mateu.ecdemo1.contenthub.domain.shared.DomainEvent;
import io.mateu.ecdemo1.contenthub.domain.survey.Answer;
import io.mateu.ecdemo1.contenthub.domain.survey.Question;
import io.mateu.ecdemo1.contenthub.domain.survey.QuestionType;
import io.mateu.ecdemo1.contenthub.domain.survey.Survey;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyResults;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgContentIndex;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgDiscussionRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgEmployeeDirectory;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgItemRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgLikesRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgSlotRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgSurveyRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgSurveyResponseRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comments, likes and surveys against a real PostgreSQL: the use cases wired by hand over the real
 * adapters, the projection applied inline, as in {@link ConsumersTest}. What matters most: nobody
 * comments on, likes or answers anything the visibility function does not let them see, and an anonymous
 * answer leaves nothing in the database that says whose it is.
 */
class CollaborationConsumersTest {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcTemplate jdbc;

    static final Reader PUBLISHER = new Reader("pablo", "Pablo", EmployeeAttributes.NONE,
            EnumSet.allOf(EditorialPermission.class), null);
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.EDIT), null);

    static final Audience RECEPCION_CANARIAS = Audience.allOf(Audience.in(Attribute.DEPARTMENT, "RECEPCION"),
            Audience.in(Attribute.REGION, "CANARIAS"));

    static final Reader ANA = new Reader("ana", "Ana", attributes("TFS01", "CANARIAS", "RECEPCION"), Set.of(), null);
    static final Reader KLAUS = new Reader("klaus", "Klaus", attributes("PMI01", "BALEARES", "COCINA"), Set.of(), null);

    static EmployeeAttributes attributes(String hotel, String region, String department) {
        return new EmployeeAttributes(Map.of(Attribute.HOTEL, hotel, Attribute.REGION, region, Attribute.DEPARTMENT, department), "es");
    }

    MutableClock clock;
    ItemCommands items;
    Collaboration collaboration;
    Surveys surveys;
    AudienceCommands audiences;
    final List<DomainEvent> published = new ArrayList<>();

    @BeforeAll
    static void start() {
        PG.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        jdbc.execute((java.sql.Connection c) -> {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("schema.sql"));
            return null;
        });
    }

    @AfterAll
    static void stop() {
        PG.stop();
    }

    @BeforeEach
    void wire() {
        for (var t : List.of("ch_item", "ch_named_audience", "ch_employee", "ch_profile", "ch_item_doc", "ch_block_doc",
                "ch_item_media", "ch_projection_state", "ch_chunk", "ch_discussion", "ch_likes", "ch_survey",
                "ch_survey_participation", "ch_survey_response", "ch_survey_doc")) {
            jdbc.update("delete from " + t);
        }
        published.clear();
        clock = new MutableClock(Instant.parse("2026-11-02T10:00:00Z"));
        var itemRepo = new PgItemRepository(jdbc);
        var audienceRepo = new PgNamedAudienceRepository(jdbc);
        var employees = new PgEmployeeDirectory(jdbc);
        var index = new PgContentIndex(jdbc);
        var named = new NamedAudiences(audienceRepo);
        var cache = new ProjectionCache();
        var surveyRepo = new PgSurveyRepository(jdbc);
        var projection = new Projection(index, itemRepo, new PgSlotRepository(jdbc), employees, named, cache);
        var listener = new ProjectionListener(projection, cache);
        var surveyProjection = new SurveyProjection(index, surveyRepo, named);
        var events = new DomainEvents(published::add, e -> {
            listener.on((DomainEvent) e);
            if (e instanceof CollaborationEvents.Event c) {
                surveyProjection.on(c);
            }
        });
        var visibility = new Visibility(index, projection, clock);
        items = new ItemCommands(itemRepo, events, employees, named, clock);
        audiences = new AudienceCommands(audienceRepo, named, employees, events);
        collaboration = new Collaboration(visibility, new PgDiscussionRepository(jdbc), new PgLikesRepository(jdbc), events, clock);
        surveys = new Surveys(surveyRepo, new PgSurveyResponseRepository(jdbc), visibility, events, clock, "the-secret");

        var directory = new DirectoryUpdates(employees, projection, events);
        employee(directory, "ana", "TFS01", "CANARIAS", "RECEPCION");
        employee(directory, "klaus", "PMI01", "BALEARES", "COCINA");
    }

    void employee(DirectoryUpdates directory, String id, String hotel, String region, String department) {
        directory.apply(new EmployeeMessage(id + "-" + System.nanoTime(), EmployeeMessage.UPSERTED, id, id, id + "@example.com",
                true, null, null, Map.of("hotel", hotel, "region", region, "department", department, "language", "es"), null, null));
    }

    String item(Audience audience) {
        var id = items.create(EDITOR, ItemType.ARTICLE, "es", new ItemContent(null, audience,
                Map.of("es", new Translation("Título", "Resumen", "<p>x</p>")), List.of(), null, null, false, false, null, null));
        items.publish(PUBLISHER, id, false);
        return id;
    }

    // ── comments and likes ──────────────────────────────────────────────────────────────────────

    @Test
    void nobody_comments_on_likes_or_reads_the_comments_of_an_item_they_cannot_see() {
        var id = item(RECEPCION_CANARIAS);
        collaboration.comment(ANA, id, "Hola");

        assertThatThrownBy(() -> collaboration.comment(KLAUS, id, "Hallo")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> collaboration.toggleLike(KLAUS, id)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> collaboration.social(KLAUS, id)).isInstanceOf(SecurityException.class);
        assertThat(collaboration.social(ANA, id).comments()).hasSize(1);
    }

    @Test
    void an_item_retired_or_expired_takes_no_more_comments() {
        var id = item(Audience.EVERYONE);
        items.retire(PUBLISHER, id);
        assertThatThrownBy(() -> collaboration.comment(ANA, id, "Hola")).isInstanceOf(SecurityException.class);
    }

    @Test
    void comments_are_stored_and_announced_through_the_outbox() {
        var id = item(Audience.EVERYONE);
        published.clear();
        var c = collaboration.comment(ANA, id, "Hola");
        collaboration.comment(KLAUS, id, "Hallo");

        var social = collaboration.social(KLAUS, id);
        assertThat(social.comments()).extracting(Discussion.Comment::text).containsExactly("Hola", "Hallo");
        assertThat(social.comments().getFirst().authorName()).isEqualTo("Ana");
        assertThat(social.comments().getFirst().at()).isEqualTo(clock.instant());
        assertThat(social.open()).isTrue();
        assertThat(published).contains(new CollaborationEvents.CommentPosted(id, c.id(), "ana"));
    }

    @Test
    void an_editor_hides_a_comment_and_who_and_when_stay() {
        var id = item(Audience.EVERYONE);
        var c = collaboration.comment(ANA, id, "Algo feo");
        clock.advance(Duration.ofMinutes(5));

        assertThatThrownBy(() -> collaboration.hide(ANA, id, c.id())).isInstanceOf(SecurityException.class);
        collaboration.hide(EDITOR, id, c.id());

        assertThat(collaboration.social(ANA, id).comments()).isEmpty();
        var moderated = collaboration.allComments(EDITOR);
        assertThat(moderated).hasSize(1);
        assertThat(moderated.getFirst().comment().hiddenBy()).isEqualTo("eva");
        assertThat(moderated.getFirst().comment().hiddenAt()).isEqualTo(clock.instant());
        assertThat(collaboration.moderated(EDITOR, id, c.id())).isPresent();

        collaboration.restore(EDITOR, id, c.id());
        assertThat(collaboration.social(ANA, id).comments()).hasSize(1);
        assertThatThrownBy(() -> collaboration.allComments(ANA)).isInstanceOf(SecurityException.class);
    }

    @Test
    void an_editor_closes_an_item_to_comments() {
        var id = item(Audience.EVERYONE);
        collaboration.comment(ANA, id, "Hola");
        assertThatThrownBy(() -> collaboration.closeComments(ANA, id, true)).isInstanceOf(SecurityException.class);

        collaboration.closeComments(EDITOR, id, true);

        assertThat(collaboration.commentsClosed(EDITOR, id)).isTrue();
        assertThat(collaboration.social(ANA, id).open()).isFalse();
        assertThat(collaboration.social(ANA, id).comments()).hasSize(1);
        assertThatThrownBy(() -> collaboration.comment(ANA, id, "Otra")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void one_like_per_reader_stored() {
        var id = item(Audience.EVERYONE);
        assertThat(collaboration.toggleLike(ANA, id)).isTrue();
        assertThat(collaboration.toggleLike(KLAUS, id)).isTrue();
        var social = collaboration.social(ANA, id);
        assertThat(social.likes()).isEqualTo(2);
        assertThat(social.liked()).isTrue();
        assertThat(collaboration.toggleLike(ANA, id)).isFalse();
        assertThat(collaboration.social(ANA, id).liked()).isFalse();
        assertThat(collaboration.social(ANA, id).likes()).isEqualTo(1);
    }

    @Test
    void previewing_as_someone_else_neither_comments_nor_likes_nor_answers() {
        var id = item(Audience.EVERYONE);
        var preview = new Reader("eva", "Eva", ANA.attributes(), EnumSet.of(EditorialPermission.EDIT), "recepcion");
        assertThatThrownBy(() -> collaboration.comment(preview, id, "Hola")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> collaboration.toggleLike(preview, id)).isInstanceOf(IllegalStateException.class);

        var survey = openSurvey(Audience.EVERYONE, false);
        assertThatThrownBy(() -> surveys.answer(preview, survey, valid())).isInstanceOf(IllegalStateException.class);
        assertThat(surveys.pending(preview)).isEmpty();
    }

    // ── surveys ─────────────────────────────────────────────────────────────────────────────────

    static final List<Question> QUESTIONS = List.of(
            new Question("r", "¿Qué tal?", QuestionType.RATING, List.of(), true),
            new Question("s", "¿Sí o no?", QuestionType.SINGLE_CHOICE, List.of("Sí", "No"), true),
            new Question("t", "¿Algo más?", QuestionType.FREE_TEXT, List.of(), false));

    static Map<String, Answer> valid() {
        return Map.of("r", Answer.rating(4), "s", Answer.choice("Sí"), "t", Answer.text("Todo bien"));
    }

    Survey.Definition definition(Audience audience, boolean anonymous, Instant opens, Instant closes) {
        return new Survey.Definition("Clima", "Dos minutos", audience, opens, closes, anonymous, QUESTIONS);
    }

    String openSurvey(Audience audience, boolean anonymous) {
        var id = surveys.create(EDITOR, definition(audience, anonymous, null, null));
        surveys.publish(PUBLISHER, id);
        return id;
    }

    @Test
    void only_its_audience_sees_or_answers_a_survey() {
        var id = openSurvey(RECEPCION_CANARIAS, false);

        assertThat(surveys.open(ANA)).extracting(o -> o.survey().surveyId()).containsExactly(id);
        assertThat(surveys.pending(ANA)).hasSize(1);
        assertThat(surveys.forReader(ANA, id)).isPresent();

        assertThat(surveys.open(KLAUS)).isEmpty();
        assertThat(surveys.forReader(KLAUS, id)).isEmpty();
        assertThatThrownBy(() -> surveys.answer(KLAUS, id, valid())).isInstanceOf(SecurityException.class);
    }

    @Test
    void a_draft_is_seen_by_nobody_and_writing_and_publishing_need_their_permissions() {
        assertThatThrownBy(() -> surveys.create(ANA, definition(Audience.EVERYONE, false, null, null)))
                .isInstanceOf(SecurityException.class);
        var id = surveys.create(EDITOR, definition(Audience.EVERYONE, false, null, null));
        assertThat(surveys.open(ANA)).isEmpty();
        assertThatThrownBy(() -> surveys.answer(ANA, id, valid())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> surveys.publish(EDITOR, id)).isInstanceOf(SecurityException.class);
        surveys.publish(PUBLISHER, id);
        assertThat(surveys.open(ANA)).hasSize(1);
    }

    @Test
    void it_opens_and_closes_by_its_dates_and_by_hand() {
        var now = clock.instant();
        var id = surveys.create(EDITOR, definition(Audience.EVERYONE, false, now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(2))));
        surveys.publish(PUBLISHER, id);
        assertThat(surveys.open(ANA)).isEmpty();

        clock.advance(Duration.ofMinutes(61));
        assertThat(surveys.open(ANA)).hasSize(1);

        clock.advance(Duration.ofMinutes(60));
        assertThat(surveys.open(ANA)).isEmpty();
        assertThatThrownBy(() -> surveys.answer(ANA, id, valid())).isInstanceOf(SecurityException.class);

        var other = openSurvey(Audience.EVERYONE, false);
        assertThat(surveys.open(ANA)).hasSize(1);
        surveys.close(PUBLISHER, other);
        assertThat(surveys.open(ANA)).isEmpty();
        assertThatThrownBy(() -> surveys.answer(ANA, other, valid())).isInstanceOf(SecurityException.class);
    }

    @Test
    void its_audience_can_change_after_publishing_and_a_new_profile_or_a_redefined_named_audience_are_followed() {
        var id = openSurvey(RECEPCION_CANARIAS, false);
        var d = definition(Audience.in(Attribute.DEPARTMENT, "COCINA"), false, null, null);
        surveys.edit(EDITOR, id, d);
        assertThat(surveys.open(KLAUS)).hasSize(1);
        assertThat(surveys.open(ANA)).isEmpty();

        // a profile nobody has had yet is materialised when it first asks
        var newcomer = new Reader("nuevo", "Nuevo", attributes("LPA01", "CANARIAS", "COCINA"), Set.of(), null);
        assertThat(surveys.open(newcomer)).hasSize(1);

        // named: redefining it moves the survey with it (a rebuild of the projection, which covers surveys)
        audiences.define(PUBLISHER, "grupo", "Grupo", Audience.in(Attribute.DEPARTMENT, "RECEPCION"));
        var named = openSurvey(new Audience.Named("grupo"), false);
        assertThat(surveys.open(ANA)).extracting(o -> o.survey().surveyId()).containsExactly(named);
        audiences.redefine(PUBLISHER, "grupo", "Grupo", Audience.in(Attribute.DEPARTMENT, "COCINA"));
        assertThat(surveys.open(ANA)).isEmpty();
        assertThat(surveys.open(KLAUS)).extracting(o -> o.survey().surveyId()).contains(named);
    }

    @Test
    void one_answer_per_reader_named_says_who() {
        var id = openSurvey(Audience.EVERYONE, false);
        surveys.answer(ANA, id, valid());

        assertThat(surveys.open(ANA).getFirst().answered()).isTrue();
        assertThat(surveys.pending(ANA)).isEmpty();
        assertThat(surveys.open(KLAUS).getFirst().answered()).isFalse();
        assertThatThrownBy(() -> surveys.answer(ANA, id, valid())).isInstanceOf(IllegalStateException.class);

        var responses = surveys.responses(EDITOR, id);
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().respondentId()).isEqualTo("ana");
        assertThat(surveys.responseCount(id)).isEqualTo(1);
    }

    @Test
    void required_questions_are_enforced_on_the_way_in() {
        var id = openSurvey(Audience.EVERYONE, false);
        assertThatThrownBy(() -> surveys.answer(ANA, id, Map.of("r", Answer.rating(3))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(surveys.responseCount(id)).isZero();
        assertThat(surveys.open(ANA).getFirst().answered()).isFalse();
    }

    @Test
    void an_anonymous_answer_leaves_nothing_that_says_whose_it_is_and_still_cannot_be_given_twice() {
        var id = openSurvey(Audience.EVERYONE, true);
        published.clear();
        surveys.answer(ANA, id, valid());

        assertThat(surveys.open(ANA).getFirst().answered()).isTrue();
        assertThatThrownBy(() -> surveys.answer(ANA, id, valid())).isInstanceOf(IllegalStateException.class);
        assertThat(surveys.open(KLAUS).getFirst().answered()).isFalse();

        // the response says nothing about them, the record of who answered does not name them
        var responseDoc = jdbc.queryForObject("select doc::text from ch_survey_response where survey_id = ?", String.class, id);
        assertThat(responseDoc).doesNotContain("ana").doesNotContain("Ana");
        var respondent = jdbc.queryForObject("select respondent from ch_survey_participation where survey_id = ?", String.class, id);
        assertThat(respondent).startsWith("a:").doesNotContain("ana");
        assertThat(jdbc.queryForObject("select count(*) from ch_survey_response r where r.doc::text like '%' || ? || '%'",
                Long.class, respondent)).isZero();
        // nor do the events that leave the service
        assertThat(published).containsExactly(new CollaborationEvents.SurveyAnswered(id));
        assertThat(surveys.responses(EDITOR, id).getFirst().respondentId()).isNull();
    }

    @Test
    void results_come_from_what_was_stored() {
        var id = openSurvey(Audience.EVERYONE, true);
        surveys.answer(ANA, id, valid());
        surveys.answer(KLAUS, id, Map.of("r", Answer.rating(2), "s", Answer.choice("No")));

        var results = SurveyResults.of(new PgSurveyRepository(jdbc).find(id).orElseThrow(), surveys.responses(EDITOR, id));

        assertThat(results.responses()).isEqualTo(2);
        assertThat(results.questions().get(0).average()).isEqualTo(3.0);
        assertThat(results.questions().get(1).counts()).containsExactly(new SurveyResults.Count("Sí", 1, 50),
                new SurveyResults.Count("No", 1, 50));
        assertThat(results.questions().get(2).texts()).containsExactly("Todo bien");
    }

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

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
    }
}
