package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.dtos.UIFragmentDto;
import io.mateu.ecdemo1.mapping.config.MappingProperties;
import io.mateu.ecdemo1.mapping.proposals.AgentProposals;
import io.mateu.uidl.data.LongTask;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Ask the agent» shows how it goes: while the agent thinks — it answers all at once, minutes later —
 * the dialog counts the seconds and its bar closes in on the end of the hotel's stretch, without
 * reaching it; then the agent's answer, and the bar at the end of the stretch.
 */
class AskTheAgentProgressTest {

    final AgentProposals agent = new AgentProposals(new MappingProperties(null, null, null, null, null, Duration.ofSeconds(1))) {
        @Override
        public String requestProposal(String hotelCode, String authorization) {
            try {
                Thread.sleep(2_500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "Propuestas registradas: 45\nDetalle…";
        }
    };

    final DictionaryCrud crud = new DictionaryCrud(null, null, null, null, agent);

    @Test
    void whileTheAgentThinksTheSecondsCountAndTheBarMovesThenItsAnswer() {
        List<Map<String, Object>> states = LongTask.create("Asking the mapping agent").withProgressBar()
                .run(progress -> crud.asking(progress, "MRU01", 1, 2, "Bearer x"))
                .collectList().block(Duration.ofSeconds(20)).stream()
                .filter(UIFragmentDto.class::isInstance).map(f -> ((UIFragmentDto) f).state())
                .filter(Objects::nonNull).map(s -> (Map<String, Object>) s)
                .filter(s -> s.containsKey("progressText")).toList();

        var waiting = states.stream().filter(s -> String.valueOf(s.get("progressText")).startsWith("The agent is proposing")).toList();
        assertThat(waiting).hasSizeGreaterThanOrEqualTo(2);
        assertThat(waiting.getFirst().get("progressText")).isEqualTo("The agent is proposing the mapping of MRU01… 0 s");
        // the second of two hotels: its stretch is 0.5–1.0, and the waiting never reaches its end
        assertThat(waiting).allSatisfy(s -> assertThat((Double) s.get("progressValue")).isBetween(0.5, 0.95));
        var answered = states.stream().filter(s -> String.valueOf(s.get("progressText")).startsWith("MRU01: ")).findFirst().orElseThrow();
        assertThat(answered.get("progressText")).isEqualTo("MRU01: Propuestas registradas: 45");
        assertThat(answered.get("progressValue")).isEqualTo(1.0);
    }
}
