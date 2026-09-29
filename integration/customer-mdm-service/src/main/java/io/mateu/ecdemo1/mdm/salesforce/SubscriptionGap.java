package io.mateu.ecdemo1.mdm.salesforce;

/**
 * A Pub/Sub subscription started from now, not from where the last one left off — the first time, or
 * after its replay id aged out of Salesforce's retention (three days): what the topic said meanwhile
 * was not heard. The topic's poll — the safety net, otherwise daily — runs at once.
 *
 * @param topic the topic, as {@code /event/ClienteConsolidado__e}
 */
public record SubscriptionGap(String topic) {
}
