package io.mateu.ecdemo1.frontoffice.infra.mcp;

import io.mateu.ecdemo1.frontoffice.infra.audit.AuditOutbox;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The agent's writes, in two steps: a {@code prepare…} tool checks the operation and keeps it here under
 * a token, with what it will do in words; only {@link #confirm} carries it out — and only in a LATER
 * turn of the conversation than the one that prepared it. ia-agent opens a new MCP session for every
 * prompt, so a confirmation on the session that prepared the operation is the model confirming for
 * itself, not the person: it is refused. The person reads the summary, answers, and that answer is
 * the next prompt.
 *
 * <p>Every confirmed operation is audited — carried out or refused — with the agent as the actor, for
 * the person it acted for. Tokens are single-use and expire; nothing survives a restart, which only
 * means preparing again. One replica, as the front office runs.
 */
@Component
public class PendingConfirmations {

  static final Logger log = LoggerFactory.getLogger(PendingConfirmations.class);

  /** The agent, as the audit trail names it. */
  public static final String AGENT = "reception-agent";

  static final Duration TTL = Duration.ofMinutes(15);
  static final JsonMapper JSON = JsonMapper.builder().build();
  static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

  /** An operation prepared and not confirmed yet. */
  record Prepared(String token, String action, String summary, Map<String, Object> parameters, String person,
                  String turn, Instant at, Supplier<String> execution) {}

  final Map<String, Prepared> pending = new ConcurrentHashMap<>();
  final SecureRandom random = new SecureRandom();
  final AuditOutbox audit;
  final McpCaller caller;
  final String hotel;
  final Clock clock;

  @Autowired
  public PendingConfirmations(AuditOutbox audit, McpCaller caller, @Value("${frontoffice.hotel:MRU01}") String hotel) {
    this(audit, caller, hotel, Clock.systemUTC());
  }

  PendingConfirmations(AuditOutbox audit, McpCaller caller, String hotel, Clock clock) {
    this.audit = audit;
    this.caller = caller;
    this.hotel = hotel;
    this.clock = clock;
  }

  /**
   * Keeps the operation for the person to confirm, and says so in words the agent passes on.
   *
   * @param action     the auditable kind of action, e.g. "Late check-out"
   * @param summary    what it will do, for the person to read before confirming
   * @param parameters what it is asked with, for the audit trail
   * @param execution  the use case itself; what it returns is the outcome, in words
   */
  public String prepare(String action, String summary, Map<String, Object> parameters, Supplier<String> execution) {
    purgeExpired();
    var token = newToken();
    pending.put(token, new Prepared(token, action, summary, new LinkedHashMap<>(parameters), caller.person(),
        caller.turn(), clock.instant(), execution));
    return """
        PENDIENTE DE CONFIRMACIÓN — token %s
        %s
        Muéstrale este resumen a la persona, con la referencia %s, y pregúntale si lo confirma. Nada se ha \
        hecho todavía: solo cuando responda que sí, llama a confirmAction con el token %s (si no lo tienes a \
        mano, listPendingActions). Si dice que no, cancelAction."""
        .formatted(token, summary, token, token);
  }

  /** Carries out a prepared operation the person confirmed, and audits it. */
  public String confirm(String token) {
    var key = token == null ? "" : token.trim().toUpperCase();
    var prepared = pending.get(key);
    if (prepared == null || expired(prepared)) {
      pending.remove(key);
      return "Error: no hay ninguna operación pendiente con el token " + token
          + " (caducan a los " + TTL.toMinutes() + " minutos y solo sirven una vez). Prepárala de nuevo.";
    }
    var person = caller.person();
    if (prepared.person() != null && person != null && !prepared.person().equals(person)) {
      return "Error: esa operación la preparó otra persona (" + prepared.person() + "): no puedes confirmarla tú.";
    }
    var turn = caller.turn();
    if (prepared.turn() != null && prepared.turn().equals(turn)) {
      return "Error: la persona todavía no ha confirmado. Muéstrale el resumen, pregúntale y espera su respuesta: "
          + "una operación no se confirma en el mismo mensaje en que se preparó.";
    }
    if (pending.remove(key) == null) {
      return "Error: esa operación ya se ha confirmado o cancelado.";
    }
    var by = actor(person != null ? person : prepared.person());
    try {
      var outcome = prepared.execution().get();
      record(prepared, by, true, outcome);
      return "Hecho. " + outcome;
    } catch (RuntimeException e) {
      var why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      record(prepared, by, false, why);
      return "Error: no se ha podido hacer — " + why;
    }
  }

  /** An operation waiting for the person's yes, as the agent is shown it. */
  public record PendingView(String token, String action, String summary, Instant preparedAt) {}

  /**
   * What the person calling has prepared and not confirmed yet, newest first — so the agent finds the
   * token in the turn the person says yes, when the conversation it remembers may not carry it.
   */
  public java.util.List<PendingView> pendingForCaller() {
    purgeExpired();
    var person = caller.person();
    return pending.values().stream()
        .filter(p -> person == null || p.person() == null || person.equals(p.person()))
        .sorted(java.util.Comparator.comparing(Prepared::at).reversed())
        .map(p -> new PendingView(p.token(), p.action(), p.summary(), p.at()))
        .toList();
  }

  /** Forgets a prepared operation the person did not want. */
  public String cancel(String token) {
    var prepared = pending.remove(token == null ? "" : token.trim().toUpperCase());
    return prepared == null ? "No había ninguna operación pendiente con el token " + token + "."
        : "Cancelado: " + prepared.action() + ". No se ha hecho nada.";
  }

  /** The audit trail's "by": the agent, and the person it acted for when the call said who. */
  static String actor(String person) {
    return person == null || person.isBlank() ? AGENT : AGENT + " (" + person + ")";
  }

  void record(Prepared prepared, String by, boolean succeeded, String response) {
    try {
      audit.append(new AuditedAction(java.util.UUID.randomUUID().toString(), clock.instant(), AuditOutbox.SERVICE,
          prepared.action(), hotel, by, JSON.writeValueAsString(prepared.parameters()), succeeded, response));
    } catch (RuntimeException e) {
      // The operation is done (or refused) whatever happens to its record; say so loudly.
      log.error("{} by {} could not be audited", prepared.action(), by, e);
    }
  }

  boolean expired(Prepared prepared) {
    return prepared.at().plus(TTL).isBefore(clock.instant());
  }

  void purgeExpired() {
    pending.values().removeIf(this::expired);
  }

  String newToken() {
    String token;
    do {
      var chars = new char[6];
      for (int i = 0; i < chars.length; i++) {
        chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
      }
      token = new String(chars);
    } while (pending.containsKey(token));
    return token;
  }

  int size() {
    return pending.size();
  }
}
