package io.mateu.ecdemo1.communication.send;

import io.mateu.ecdemo1.communication.store.PushSubscription;
import io.mateu.ecdemo1.communication.store.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * "Enviarme una prueba": a notification straight to a person's browser, to see that Web Push reaches
 * it — without the inbox, the recipients or anybody else. Only to the person's own browsers: whoever
 * asks can only test their own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestPushes {

    public enum Outcome {
        /** The push service took it: the browser shows it. */
        SENT,
        /** This deployment has no VAPID keys. */
        NOT_CONFIGURED,
        /** No such browser of this person: it never subscribed, or it unsubscribed. */
        NOT_FOUND,
        /** The push service no longer has it: the browser dropped the subscription; so is the row. */
        GONE,
        /** The push service refused it, or could not be reached. */
        FAILED
    }

    public record Result(Outcome outcome, String browser, String detail) {
    }

    final PushSubscriptionRepository subscriptions;
    final WebPush push;
    final Clock clock;

    /** To one browser of this person, the one asking. */
    public Result toBrowser(String username, String subscriptionId) {
        if (!push.configured()) {
            return new Result(Outcome.NOT_CONFIGURED, null, "Web Push is not configured on this deployment");
        }
        return subscriptions.findById(subscriptionId)
                .filter(s -> username != null && username.equals(s.username))
                .map(this::send)
                .orElse(new Result(Outcome.NOT_FOUND, null, "This browser has not enabled notifications for " + username));
    }

    /** To every browser of this person. */
    public List<Result> toUser(String username) {
        if (!push.configured()) {
            return List.of(new Result(Outcome.NOT_CONFIGURED, null, "Web Push is not configured on this deployment"));
        }
        var browsers = subscriptions.findByUsername(username);
        if (browsers.isEmpty()) {
            return List.of(new Result(Outcome.NOT_FOUND, null, username + " has not enabled notifications in any browser"));
        }
        return browsers.stream().map(this::send).toList();
    }

    static WebPush.Message message(PushSubscription s) {
        return new WebPush.Message("Prueba de avisos",
                "Si lees esto, los avisos llegan a este navegador" + (s.atFrontDesk() ? " de recepción." : "."),
                "/", "test-" + UUID.randomUUID(), false);
    }

    Result send(PushSubscription s) {
        var where = s.atFrontDesk() ? "front desk" : "console";
        try {
            push.send(s, message(s));
            s.lastSentAt = clock.instant();
            subscriptions.save(s);
            log.info("Test notification sent to a browser of {} ({})", s.username, where);
            return new Result(Outcome.SENT, where, "Sent");
        } catch (WebPush.Gone e) {
            subscriptions.deleteById(s.id);
            return new Result(Outcome.GONE, where, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Test notification to a browser of {} failed: {}", s.username, e.getMessage());
            return new Result(Outcome.FAILED, where, e.getMessage());
        }
    }
}
