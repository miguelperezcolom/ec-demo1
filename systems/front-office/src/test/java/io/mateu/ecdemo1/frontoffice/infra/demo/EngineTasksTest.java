package io.mateu.ecdemo1.frontoffice.infra.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mateu.ecdemo1.demoreset.DemoReset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The engine's protocol as the front office's worker speaks it: JSON in, the replies worker-kafka would send out. */
class EngineTasksTest {

  final DemoReset reset = mock(DemoReset.class);
  final EngineTasks tasks = new EngineTasks(reset);

  static byte[] task(String taskId) {
    return ("""
        {"type":"task-execution-requested","taskExecutionId":"tx-1","processId":"p-1","workflowDefinitionId":"reset-demo",
         "stepId":"reset-front-office","taskId":"%s","variables":[{"name":"processKey","value":"reset-demo:1"}]}"""
        .formatted(taskId)).getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void resetAtOneIsRunAndAnsweredCompletedKeyedByTheProcess() {
    var replies = tasks.handle(task("reset@1"));

    verify(reset).run();
    assertThat(replies).singleElement().satisfies(r -> {
      assertThat(r.key()).isEqualTo("p-1");
      var json = r.json();
      assertThat(json.path("type").asString()).isEqualTo("task-status-changed");
      assertThat(json.path("taskExecutionId").asString()).isEqualTo("tx-1");
      assertThat(json.path("status").asString()).isEqualTo("COMPLETED");
      assertThat(json.path("processId").asString()).isEqualTo("p-1");
      assertThat(json.path("nonRetryable").asBoolean(true)).isFalse();
      assertThat(json.path("variables").isArray()).isTrue();
      assertThat(json.path("variables").size()).isZero();
    });
  }

  @Test
  void aBareResetIsTheSameTask() {
    assertThat(tasks.handle(task("reset")).getFirst().json().path("status").asString()).isEqualTo("COMPLETED");
  }

  @Test
  void aFailedResetSaysWhyThenError() {
    when(reset.run()).thenThrow(new IllegalStateException("relation \"guest\" is locked"));

    var replies = tasks.handle(task("reset@1"));

    assertThat(replies).extracting(r -> r.json().path("type").asString())
        .containsExactly("task-log-emitted", "task-status-changed");
    var why = replies.get(0).json();
    assertThat(why.path("messageType").asString()).isEqualTo("Error");
    assertThat(why.path("taskExecutionId").asString()).isEqualTo("tx-1");
    assertThat(why.path("message").asString()).isEqualTo("RESET_FAILED: front-office: relation \"guest\" is locked");
    assertThat(replies.get(1).json().path("status").asString()).isEqualTo("ERROR");
    assertThat(replies).extracting(EngineTasks.Reply::key).containsOnly("p-1");
  }

  @Test
  void aTaskNotServedHereIsAnsweredErrorAtOnce() {
    var replies = tasks.handle(task("check-in-reservation@1"));

    verify(reset, never()).run();
    assertThat(replies.get(0).json().path("message").asString()).startsWith("UNKNOWN_TASK").contains("check-in-reservation@1");
    assertThat(replies.get(1).json().path("status").asString()).isEqualTo("ERROR");
  }

  @Test
  void aCancellationOrAnUnreadableMessageIsNotAnswered() {
    assertThat(tasks.handle("""
        {"type":"task-cancellation-requested","taskExecutionId":"tx-1","processId":"p-1"}""".getBytes(StandardCharsets.UTF_8)))
        .isEmpty();
    assertThat(tasks.handle("not json".getBytes(StandardCharsets.UTF_8))).isEmpty();
    verify(reset, never()).run();
  }

  @Test
  void itServesResetAtOneOnItsOwnTopic() {
    assertThat(EngineTasks.SERVED).isEqualTo(List.of("reset@1 front-office"));
  }
}
