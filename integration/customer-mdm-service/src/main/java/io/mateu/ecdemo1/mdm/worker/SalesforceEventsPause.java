package io.mateu.ecdemo1.mdm.worker;

import io.mateu.ecdemo1.demoreset.ConsumerPause;
import io.mateu.ecdemo1.mdm.salesforce.ConsolidationEvents;

/**
 * Salesforce's Pub/Sub subscription, held still while the MDM empties its tables: an event handled
 * half-way through would write into what is being emptied. Restarted only if it was running —
 * unconfigured (locally), it never was.
 *
 * <p>The scheduled jobs are left running: they work from the MDM's own customers (the projection,
 * the marking, the clean-up, the merges, the change requests), which the reset empties, so after it
 * they find nothing. The one that reads Salesforce into the MDM, the poll for merged contacts, runs
 * once a day and is held by {@link SalesforceCleanup} while Salesforce is emptied.
 */
public class SalesforceEventsPause implements ConsumerPause {

    private final ConsolidationEvents events;
    private boolean wasRunning;

    public SalesforceEventsPause(ConsolidationEvents events) {
        this.events = events;
    }

    @Override
    public synchronized void pause() {
        wasRunning = events.isRunning();
        if (wasRunning) {
            events.stop();
        }
    }

    @Override
    public synchronized void resume() {
        if (wasRunning) {
            events.start();
        }
    }

    @Override
    public String describe() {
        return "Salesforce's Pub/Sub subscription";
    }
}
