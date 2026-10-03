package io.mateu.ecdemo1.frontoffice.infra.demo;

import io.mateu.ecdemo1.demoreset.DemoReset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The engine's tasks the front office serves, in the engine's own words — what worker-kafka does for
 * the other services, by hand: the front office runs on Boot 4 and plain spring-kafka, and the engine's
 * worker runtime is Spring Cloud Stream on Boot 3.
 *
 * <p>A {@code task-execution-requested} for a task served here is run, and answered with a
 * {@code task-status-changed} COMPLETED — or ERROR, after a {@code task-log-emitted} saying why. One
 * for a task not served here is answered ERROR too: the engine would otherwise wait for its timeout.
 * Anything else on the topic (a {@code task-cancellation-requested}) is not answered.
 */
public class EngineTasks {

  static final Logger log = LoggerFactory.getLogger(EngineTasks.class);

  public static final String TOPIC = "front-office";
  public static final String REPLIES = "upstream";
  public static final String RESET = "reset";
  public static final int RESET_VERSION = 1;

  /** What is served, {@code <id>@<version> <topic>} — contracts/workers/front-office.tasks. */
  public static final List<String> SERVED = List.of(RESET + "@" + RESET_VERSION + " " + TOPIC);

  static final JsonMapper JSON = JsonMapper.builder().build();

  /** A reply to the engine: keyed by the process, so its events stay in order on one partition. */
  public record Reply(String key, byte[] payload) {
    public JsonNode json() {
      return JSON.readTree(payload);
    }
  }

  final DemoReset reset;

  public EngineTasks(DemoReset reset) {
    this.reset = reset;
  }

  public List<Reply> handle(byte[] payload) {
    JsonNode event;
    try {
      event = JSON.readTree(payload);
    } catch (RuntimeException e) {
      log.error("Unreadable message on {}, skipped", TOPIC);
      return List.of();
    }
    if (!"task-execution-requested".equals(event.path("type").asString(""))) {
      return List.of();
    }
    var taskExecutionId = event.path("taskExecutionId").asString(null);
    var processId = event.path("processId").asString(null);
    var taskId = event.path("taskId").asString("");
    if (!(taskId.equals(RESET) || taskId.equals(RESET + "@" + RESET_VERSION))) {
      return failed(taskExecutionId, processId, "UNKNOWN_TASK: the front office does not serve " + taskId
          + " (it serves " + String.join(", ", SERVED) + ")");
    }
    try {
      reset.run();
      return List.of(status(taskExecutionId, processId, "COMPLETED"));
    } catch (RuntimeException e) {
      log.warn("Step {} of process {} failed: {}", event.path("stepId").asString(null), processId, e.getMessage());
      return failed(taskExecutionId, processId,
          "RESET_FAILED: front-office: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
    }
  }

  List<Reply> failed(String taskExecutionId, String processId, String reason) {
    var logLine = JSON.createObjectNode()
        .put("type", "task-log-emitted")
        .put("taskExecutionId", taskExecutionId)
        .put("messageType", "Error")
        .put("message", reason);
    return List.of(new Reply(processId, bytes(logLine)), status(taskExecutionId, processId, "ERROR"));
  }

  Reply status(String taskExecutionId, String processId, String status) {
    var reply = JSON.createObjectNode()
        .put("type", "task-status-changed")
        .put("taskExecutionId", taskExecutionId)
        .put("status", status)
        .put("processId", processId)
        .put("nonRetryable", false);
    reply.putArray("variables");
    return new Reply(processId, bytes(reply));
  }

  static byte[] bytes(JsonNode node) {
    return JSON.writeValueAsString(node).getBytes(StandardCharsets.UTF_8);
  }
}
