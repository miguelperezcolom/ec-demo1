package io.mateu.ecdemo1.contenthub.application.search;

import io.mateu.ecdemo1.contenthub.application.out.AnswerGenerator;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.Embeddings;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The search over a store that answers what the test says, as the visibility function would have filtered it. */
class IntranetSearchTest {

    static final EmployeeAttributes READER = EmployeeAttributes.NONE;
    static final String TOKEN = "Bearer t";

    final List<ContentIndex.Passage> passages = new ArrayList<>();
    final List<VisibleItem> byWords = new ArrayList<>();
    final List<VisibleItem> byId = new ArrayList<>();

    final ContentIndex index = (ContentIndex) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{ContentIndex.class}, (proxy, method, args) -> switch (method.getName()) {
                case "knowsProfile" -> true;
                case "similarVisiblePassages" -> passages;
                case "visibleItems" -> {
                    var q = (ContentQuery) args[3];
                    var found = q.itemIds() != null ? byId : byWords;
                    yield new ContentIndex.Page<>(found, found.size());
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });

    Embeddings embeddings = (texts, query) -> texts.stream().map(t -> new float[384]).toList();

    final List<List<AnswerGenerator.Source>> sent = new ArrayList<>();
    final List<String> tokens = new ArrayList<>();
    boolean agentAvailable = true;
    java.util.function.Supplier<String> agent = () -> "Son 30 días [1].";

    final AnswerGenerator generator = new AnswerGenerator() {
        @Override
        public boolean available() {
            return agentAvailable;
        }

        @Override
        public String answer(String question, List<Source> sources, String authorization) {
            sent.add(sources);
            tokens.add(authorization);
            return agent.get();
        }
    };

    IntranetSearch search() {
        return new IntranetSearch(new Visibility(index, null, Clock.systemUTC()), embeddings, generator, .3);
    }

    static VisibleItem item(String id, String body) {
        return new VisibleItem(id, 1, "ARTICLE", null, "es", "Título " + id, "Resumen " + id, body, null, "eva",
                java.time.Instant.EPOCH, null, false, false, null, 0);
    }

    static ContentIndex.Passage passage(String id, String text, double distance) {
        return new ContentIndex.Passage(id, "es", 0, "Título " + id, text, distance);
    }

    @Test
    void results_merge_words_and_meaning_with_the_matching_passage_as_caption() {
        byWords.add(item("w", "<p>las vacaciones de verano</p>"));
        passages.add(passage("m", "Título m. Resumen\nDías libres al año", .1));
        byId.add(item("m", "<p>otro</p>"));

        var results = search().search(READER, "vacaciones", 10);

        assertThat(results.semantic()).isTrue();
        assertThat(results.items()).extracting(r -> r.item().itemId()).containsExactly("w", "m");
        assertThat(results.items().get(0).caption()).contains("vacaciones");
        assertThat(results.items().get(1).caption()).isEqualTo("Días libres al año");
    }

    @Test
    void a_passage_whose_item_the_store_does_not_return_is_not_a_result() {
        passages.add(passage("gone", "x\ny", .1));
        assertThat(search().search(READER, "algo", 10).items()).isEmpty();
    }

    @Test
    void without_the_embeddings_service_the_search_is_by_words() {
        embeddings = (texts, query) -> {
            throw new IllegalStateException("down");
        };
        byWords.add(item("w", "<p>vacaciones</p>"));
        var results = search().search(READER, "vacaciones", 10);
        assertThat(results.semantic()).isFalse();
        assertThat(results.items()).extracting(r -> r.item().itemId()).containsExactly("w");
    }

    @Test
    void a_query_without_words_finds_nothing_rather_than_the_latest() {
        byWords.add(item("w", ""));
        assertThat(search().search(READER, "¿?", 10).items()).isEmpty();
    }

    @Test
    void the_agent_gets_the_passages_and_the_readers_token_and_citations_go_to_their_items() {
        passages.add(passage("a", "uno", .1));
        passages.add(passage("a", "dos", .15));
        passages.add(passage("b", "tres", .2));
        agent = () -> "Primero [3]. Luego [1, 2]. Inventado [9].";

        var answer = search().answer(READER, "¿cuántos días?", TOKEN);

        assertThat(tokens).containsExactly(TOKEN);
        assertThat(sent.getFirst()).extracting(AnswerGenerator.Source::text).containsExactly("uno", "dos", "tres");
        assertThat(answer.status()).isEqualTo(IntranetSearch.AnswerStatus.ANSWERED);
        // renumbered as first cited; two passages of one item are one citation; what was not sent is dropped
        assertThat(answer.text()).isEqualTo("Primero [1]. Luego [2]. Inventado.");
        assertThat(answer.citations()).extracting(IntranetSearch.Citation::itemId).containsExactly("b", "a");
    }

    @Test
    void only_near_passages_are_sent() {
        passages.add(passage("near", "cerca", .1));
        passages.add(passage("far", "lejos", .9));
        search().answer(READER, "¿algo?", TOKEN);
        assertThat(sent.getFirst()).extracting(AnswerGenerator.Source::itemId).containsExactly("near");
    }

    @Test
    void without_the_readers_token_nobody_is_asked() {
        passages.add(passage("a", "uno", .1));
        for (var token : new String[]{null, " "}) {
            var answer = search().answer(READER, "¿algo?", token);
            assertThat(answer.status()).isEqualTo(IntranetSearch.AnswerStatus.UNAVAILABLE);
            assertThat(answer.text()).contains("no está disponible");
        }
        assertThat(sent).isEmpty();
    }

    @Test
    void no_agent_configured_is_said_briefly() {
        agentAvailable = false;
        passages.add(passage("a", "uno", .1));
        assertThat(search().answer(READER, "¿algo?", TOKEN).status()).isEqualTo(IntranetSearch.AnswerStatus.UNAVAILABLE);
        assertThat(sent).isEmpty();
    }

    @Test
    void any_failure_of_the_agent_is_the_same_brief_notice_and_the_results_stand() {
        byWords.add(item("w", "<p>vacaciones</p>"));
        passages.add(passage("w", "t\nvacaciones", .1));
        agent = () -> {
            throw new IllegalStateException("Agent 'intranet-agent' is not available: internal detail");
        };
        var answer = search().answer(READER, "¿vacaciones?", TOKEN);
        assertThat(answer.status()).isEqualTo(IntranetSearch.AnswerStatus.FAILED);
        assertThat(answer.text()).doesNotContain("intranet-agent").contains("no está disponible");
        assertThat(answer.citations()).isEmpty();
        assertThat(search().search(READER, "vacaciones", 10).items()).hasSize(1);
    }

    @Test
    void nothing_to_read_means_no_call() {
        var answer = search().answer(READER, "¿algo?", TOKEN);
        assertThat(answer.status()).isEqualTo(IntranetSearch.AnswerStatus.NOTHING_FOUND);
        assertThat(sent).isEmpty();
    }

    @Test
    void without_embeddings_the_agent_reads_the_items_found_by_words() {
        embeddings = (texts, query) -> {
            throw new IllegalStateException("down");
        };
        byWords.add(item("w", "<p>vacaciones</p>"));
        search().answer(READER, "vacaciones", TOKEN);
        assertThat(sent.getFirst()).extracting(AnswerGenerator.Source::itemId).containsExactly("w");
        assertThat(sent.getFirst().getFirst().text()).contains("vacaciones").doesNotContain("<p>");
    }
}
