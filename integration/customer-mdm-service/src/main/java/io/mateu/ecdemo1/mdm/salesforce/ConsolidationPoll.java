package io.mateu.ecdemo1.mdm.salesforce;

import io.mateu.ecdemo1.mdm.consolidation.Consolidations;
import io.mateu.ecdemo1.mdm.store.Cursor;
import io.mateu.ecdemo1.mdm.store.CursorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The safety net under the subscription (HLA CRM-MDM: «fallback / conciliación»): contacts with an
 * MDM id deleted since the last look, read from the recycle bin. An event lost while the MDM was
 * down, or older than the replay window, is found here.
 *
 * <p>A net, not the way merges arrive: every quarter of an hour, one query (two for a page past 200),
 * about a hundred calls a day out of the org's allowance — a minute's poll was 1,440. Not while the
 * allowance pauses calls; the cursor is only moved once what was found is applied, so nothing is
 * skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConsolidationPoll {

    static final DateTimeFormatter SOQL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    final SalesforceClient salesforce;
    final Consolidations consolidations;
    final CursorRepository cursors;
    final Clock clock;

    @Scheduled(fixedDelayString = "${mdm.poll:15m}", initialDelayString = "${mdm.poll-initial-delay:2m}")
    public void poll() {
        if (!salesforce.available()) {
            return;
        }
        try {
            var cursor = cursors.findById(Cursor.POLL).orElseGet(() -> {
                var fresh = new Cursor();
                fresh.name = Cursor.POLL;
                // Salesforce keeps what it deleted fifteen days: no point looking further back.
                fresh.until = clock.instant().minus(Duration.ofDays(15));
                return fresh;
            });
            var since = OffsetDateTime.ofInstant(cursor.until, ZoneOffset.UTC).format(SOQL);
            var deleted = salesforce.queryAll(SalesforceClient.Purpose.POLL, ("SELECT Id, MDM_Id__c, SystemModstamp FROM Contact WHERE IsDeleted = true "
                    + "AND MDM_Id__c != null AND SystemModstamp >= %s ORDER BY SystemModstamp ASC LIMIT 200").formatted(since));
            var until = cursor.until;
            for (var contact : deleted) {
                consolidations.received(contact.path("MDM_Id__c").asText(), contact.path("Id").asText(), "POLL");
                var at = OffsetDateTime.parse(contact.path("SystemModstamp").asText().replace("+0000", "Z")).toInstant();
                until = at.isAfter(until) ? at : until;
            }
            cursor.until = until;
            cursors.save(cursor);
        } catch (SalesforceClient.LimitExceeded e) {
            log.debug("Polling Salesforce for merges waits for the allowance: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Polling Salesforce for merges failed, next time: {}", e.getMessage());
        }
    }
}
