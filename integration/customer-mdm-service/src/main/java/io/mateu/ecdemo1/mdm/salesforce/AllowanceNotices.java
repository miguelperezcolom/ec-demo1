package io.mateu.ecdemo1.mdm.salesforce;

import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import io.mateu.ecdemo1.mdm.outbox.Outbox;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Tells the integration's administrators, once, that Salesforce's daily API allowance is spent and the
 * MDM is holding its calls — customers wait to be cleaned, merges and decisions wait to be read — and
 * closes that notice when Salesforce answers again. Nothing is lost meanwhile: it all goes on its own.
 */
@Component
public class AllowanceNotices implements SalesforceBudget.Listener {

    /** What every such notice is about: they all close when calls go again. */
    public static final String SUBJECT = "salesforce/api-allowance";
    static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    final Outbox outbox;
    final TransactionTemplate tx;
    final SalesforceBudget budget;

    public AllowanceNotices(Outbox outbox, TransactionTemplate tx, SalesforceBudget budget) {
        this.outbox = outbox;
        this.tx = tx;
        this.budget = budget;
        budget.listener(this);
    }

    @Override
    public void paused(SalesforceBudget.Pause pause) {
        tx.executeWithoutResult(s -> outbox.appendNotification(new NotificationRequested(UUID.randomUUID().toString(),
                NotificationType.INTEGRATION_NEEDS_ATTENTION, null, SUBJECT,
                "Salesforce: daily API allowance spent",
                ("Salesforce answers REQUEST_LIMIT_EXCEEDED (%s): the org's calls in the last 24 hours reached its limit. "
                        + "The customer MDM holds its calls and tries again from %s, less often each time it is refused. "
                        + "Customers wait to be projected, and merges and decisions to be read; nothing is lost, it all goes "
                        + "when the allowance is back. This notice closes then. Scripts and tests against the org spend the "
                        + "same allowance.").formatted(budget.usage(), HOUR.format(pause.until())),
                null, "salesforce-allowance:" + pause.since().toEpochMilli(), pause.since())));
    }

    @Override
    public void resumed(SalesforceBudget.Pause pause) {
        tx.executeWithoutResult(s -> outbox.appendResolution(SUBJECT, "customer-mdm"));
    }
}
