package io.mateu.ecdemo1.iaagent.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** How a prompt reached the data, as its metrics say it: what compares searching with SQL. */
class DataPathTest {

  @Test
  void theToolsAPromptCalledSayHowItReachedTheData() {
    assertEquals("none", AgentObservability.dataPath(Set.of()));
    assertEquals("search", AgentObservability.dataPath(Set.of("searchStays")));
    assertEquals("search", AgentObservability.dataPath(Set.of("getStay", "searchStays")));
    assertEquals("sql", AgentObservability.dataPath(Set.of("describeFrontOfficeData", "queryFrontOfficeData")));
    assertEquals("search+sql", AgentObservability.dataPath(Set.of("searchBookings", "queryCrsData")));
    assertEquals("other", AgentObservability.dataPath(Set.of("listArrivals", "getGuest")));
  }

  @Test
  void eachPromptRecordsItsToolCallsByAgentAndPath() {
    var meters = new SimpleMeterRegistry();
    var context = prompt();
    context.addLowCardinalityKeyValue(KeyValue.of(AgentObservability.AGENT_ID, "reception"));
    var stats = AgentObservability.attachStats(context);
    stats.toolCalled("searchStays");
    stats.toolCalled("getStay");

    AgentObservability.onPrompt(context, meters);

    assertEquals("search", context.getLowCardinalityKeyValue(AgentObservability.DATA_PATH).getValue());
    var calls = meters.get(AgentObservability.PROMPT_TOOL_CALLS)
        .tags(AgentObservability.AGENT_ID, "reception", AgentObservability.DATA_PATH, "search").summary();
    assertEquals(1, calls.count());
    assertEquals(2.0, calls.totalAmount());
    assertEquals(1, meters.get(AgentObservability.PROMPT_TOKENS).summary().count());
  }

  @Test
  void aPromptWithNoStatsStillCarriesTheLabel() {
    var context = prompt();

    AgentObservability.onPrompt(context, new SimpleMeterRegistry());

    assertEquals("none", context.getLowCardinalityKeyValue(AgentObservability.DATA_PATH).getValue());
  }

  static Observation.Context prompt() {
    var context = new Observation.Context();
    context.setName(AgentObservability.PROMPT);
    return context;
  }
}
