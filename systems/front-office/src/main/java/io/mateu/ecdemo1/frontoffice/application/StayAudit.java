package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.audit.AuditOutbox;
import io.mateu.ecdemo1.frontoffice.infra.security.DeskUser;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who did what to a stay: every operation of the desk with consequences — the check-in and the
 * check-out, a room change, a charge and its void, the payment, the key, the wifi, the signature, the
 * kárdex, the ancillaries, a no-show, a walk-in, an incident — audited by the application service that
 * carries it out, so it counts the same whether a person did it on the desk's screens or the reception
 * agent did it for them. On the {@code audit} topic, as the control plane's actions (AuditedAction).
 *
 * <p>Who: the person the request's token names ({@link DeskUser}), unless the service is told (a
 * {@code by}), or the work runs {@link #as} someone — the reception agent, for the person it acts for.
 * The record carries the stay, its CRS locator and the hotel, with the parameters (anything that looks
 * like a card, a secret or a token masked, {@link #mask}), whether it was done and what it answered.
 *
 * <p>A success is written with the operation's own transaction — both saved, or neither —; a failure
 * apart, since the operation's is rolled back. A refusal the service that refused already audited
 * ({@link AuditedRefusal}) is not audited twice, nor an operation inside another one. Auditing never
 * breaks the operation: if it cannot be written, it is logged.
 */
@Component
public class StayAudit {

  static final Logger log = LoggerFactory.getLogger(StayAudit.class);
  static final JsonMapper JSON = JsonMapper.builder().build();

  /** A refusal already audited where it was decided: not audited again as the operation's failure. */
  public interface AuditedRefusal {}

  static final ThreadLocal<String> ACTOR = new ThreadLocal<>();
  static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

  final AuditOutbox audit;
  final WalkIns walkIns;
  final String hotel;
  final Clock clock;
  final TransactionTemplate joined;
  final TransactionTemplate apart;

  @Autowired
  public StayAudit(AuditOutbox audit, WalkIns walkIns, @Value("${frontoffice.hotel:MRU01}") String hotel,
                   PlatformTransactionManager transactions) {
    this(audit, walkIns, hotel, transactions, Clock.systemUTC());
  }

  StayAudit(AuditOutbox audit, WalkIns walkIns, String hotel, PlatformTransactionManager transactions, Clock clock) {
    this.audit = audit;
    this.walkIns = walkIns;
    this.hotel = hotel;
    this.clock = clock;
    this.joined = transactions == null ? null : new TransactionTemplate(transactions);
    this.apart = transactions == null ? null : new TransactionTemplate(transactions);
    if (apart != null) {
      apart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
  }

  /** Runs {@code work} on behalf of {@code actor}: what it audits names them, not the request's token. */
  public static <T> T as(String actor, Supplier<T> work) {
    var previous = ACTOR.get();
    ACTOR.set(actor);
    try {
      return work.get();
    } finally {
      if (previous == null) {
        ACTOR.remove();
      } else {
        ACTOR.set(previous);
      }
    }
  }

  /**
   * Who: whom the work runs {@link #as} — the reception agent for its person, who knows best —, else
   * {@code by} when the caller says, else the desk's user the request's token names.
   */
  public static String actor(String by) {
    var as = ACTOR.get();
    if (as != null && !as.isBlank()) {
      return as;
    }
    return by != null && !by.isBlank() ? by : DeskUser.name();
  }

  /**
   * Carries out an operation on a stay and audits it: done, with {@code outcome}'s words for what it
   * answered, or failed, with why.
   *
   * @param action     the auditable kind of action, e.g. "Room change"
   * @param by         who, when the caller knows; null for {@link #actor}'s answer
   * @param parameters what it was asked with
   * @param outcome    what it answered, in words; null for "OK"
   */
  public <T> T run(String action, String stayId, String by, Map<String, ?> parameters, Supplier<T> work,
                   Function<T, String> outcome) {
    if (DEPTH.get()[0] > 0) {
      return work.get(); // part of an operation already being audited
    }
    DEPTH.get()[0]++;
    T result;
    try {
      result = work.get();
    } catch (RuntimeException e) {
      DEPTH.get()[0]--;
      if (!(e instanceof AuditedRefusal)) {
        failed(action, stayId, by, parameters, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      }
      throw e;
    }
    DEPTH.get()[0]--;
    String said;
    try {
      said = outcome == null ? "OK" : outcome.apply(result);
    } catch (RuntimeException e) {
      said = "OK";
    }
    done(action, stayId, by, parameters, said);
    return result;
  }

  /** Audits an operation carried out: with the transaction under way, if there is one. */
  public void done(String action, String stayId, String by, Map<String, ?> parameters, String response) {
    write(joined, action, stayId, by, parameters, true, response);
  }

  /** Audits an operation that was not carried out — refused, or failed —: apart from its rolled-back transaction. */
  public void failed(String action, String stayId, String by, Map<String, ?> parameters, String why) {
    write(apart, action, stayId, by, parameters, false, why);
  }

  void write(TransactionTemplate tx, String action, String stayId, String by, Map<String, ?> parameters,
             boolean succeeded, String response) {
    try {
      var record = record(action, stayId, by, parameters, succeeded, response);
      if (tx == null) {
        audit.append(record);
      } else {
        tx.executeWithoutResult(s -> audit.append(record));
      }
    } catch (RuntimeException e) {
      log.error("{} of {} could not be audited", action, stayId, e);
    }
  }

  AuditedAction record(String action, String stayId, String by, Map<String, ?> parameters, boolean succeeded,
                       String response) {
    var params = new LinkedHashMap<String, Object>();
    if (stayId != null) {
      params.put("stayId", stayId);
      params.put("locator", locatorOf(stayId));
    }
    if (parameters != null) {
      mask(parameters).forEach(params::putIfAbsent);
    }
    return new AuditedAction(UUID.randomUUID().toString(), clock.instant(), AuditOutbox.SERVICE, action, hotel,
        actor(by), JSON.writeValueAsString(params), succeeded, cut(response));
  }

  /** The CRS's locator of a stay: its id, or — for a walk-in — the one the CRS gave it. */
  String locatorOf(String stayId) {
    try {
      return walkIns == null ? stayId
          : walkIns.of(stayId).map(w -> w.locator() == null ? stayId : w.locator()).orElse(stayId);
    } catch (RuntimeException e) {
      return stayId;
    }
  }

  static final Pattern SENSITIVE_KEY = Pattern.compile(
      "(?i)(.*card.*|.*tarjeta.*|pan|cvv|cvc|.*password.*|.*passwd.*|.*secret.*|.*token.*|iban|.*expiry.*|.*caducidad.*|pin)");
  static final Pattern CARD_NUMBER = Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b");

  /**
   * The parameters as the audit trail keeps them: the value of a key that names a card, a secret, a token
   * or a PIN replaced, and anything that looks like a card number cut to its last four digits.
   */
  static Map<String, Object> mask(Map<String, ?> parameters) {
    var masked = new LinkedHashMap<String, Object>();
    parameters.forEach((k, v) -> {
      if (v == null) {
        masked.put(k, null);
      } else if (k != null && SENSITIVE_KEY.matcher(k).matches()) {
        masked.put(k, "***");
      } else if (v instanceof CharSequence s) {
        masked.put(k, maskCardNumbers(s.toString()));
      } else if (v instanceof Map<?, ?> m) {
        var inner = new LinkedHashMap<String, Object>();
        m.forEach((ik, iv) -> inner.put(String.valueOf(ik), iv));
        masked.put(k, mask(inner));
      } else {
        masked.put(k, v);
      }
    });
    return masked;
  }

  static String maskCardNumbers(String s) {
    var m = CARD_NUMBER.matcher(s);
    var out = new StringBuilder();
    while (m.find()) {
      var digits = m.group().replaceAll("[^0-9]", "");
      m.appendReplacement(out, "****" + digits.substring(digits.length() - 4));
    }
    m.appendTail(out);
    return out.toString();
  }

  static String cut(String s) {
    return s == null || s.length() <= 1000 ? s : s.substring(0, 999) + "…";
  }

  /** A map of parameters, skipping null keys' pairs: {@code params("pax", 2, "room", "5167")}. */
  public static Map<String, Object> params(Object... keysAndValues) {
    var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
      map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
    }
    return map;
  }
}
