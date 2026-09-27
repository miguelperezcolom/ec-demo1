package io.mateu.ecdemo1.integration.model.audit;

import java.time.Instant;

/**
 * {@code AccionAuditada}: a person did something with consequences on a hotel's operation from the
 * control plane — through the console, the REST API or an agent's tool alike (HLA F016). The audit
 * service keeps it as it came: an audit record is never changed.
 *
 * @param actionId   unique per action: the same id delivered twice is one record
 * @param service    the service the action was taken on
 * @param action     the auditable kind of action, e.g. "Activate integration"
 * @param hotelCode  the CRS hotel it acts on; null for a chain-wide one
 * @param by         who: the person, or the agent acting for them
 * @param parameters what it was asked with, as JSON, secrets masked
 * @param succeeded  whether it was carried out
 * @param response   what it answered: the outcome, or why it was refused
 */
public record AuditedAction(String actionId,
                            Instant at,
                            String service,
                            String action,
                            String hotelCode,
                            String by,
                            String parameters,
                            boolean succeeded,
                            String response) {
}
