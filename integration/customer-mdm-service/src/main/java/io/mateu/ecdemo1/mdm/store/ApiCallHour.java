package io.mateu.ecdemo1.mdm.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One hour's count of the MDM's calls to Salesforce for one purpose and outcome — what the usage
 * counters ({@code salesforce.ApiCalls}) keep in memory, saved so a restart does not forget the
 * calls that still count against the org's rolling 24 hours. Older rows are deleted as they leave
 * the window.
 *
 * <p>The outcome is a plain string, not an enum column: Hibernate's check constraint on an enum is
 * never rewritten by {@code ddl-auto: update}, and a new value would break a long-lived database.
 */
@Entity
@Table(name = "salesforce_api_calls")
@NoArgsConstructor
public class ApiCallHour {

    /** {@code <epoch hour>|<purpose>|<outcome>} */
    @Id
    public String id;
    public Instant hour;
    public String purpose;
    public String outcome;
    public long calls;

    public ApiCallHour(Instant hour, String purpose, String outcome, long calls) {
        this.id = hour.getEpochSecond() / 3600 + "|" + purpose + "|" + outcome;
        this.hour = hour;
        this.purpose = purpose;
        this.outcome = outcome;
        this.calls = calls;
    }
}
