package io.mateu.ecdemo1.integrations.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A hotel's pms-fo integration: its front office fed from its PMS — the second link of the chain
 * CRS → PMS → front office. The front office consumes the PMS: its catalogue, its reservations as
 * the PMS holds them, and every change to them, wherever it was made. One per PMS property.
 *
 * <p>Its onboarding moves it through gates as the crs-pms one does: {@link #gate} is the message the
 * onboarding process waits for now, and what opens each gate is recorded here.
 */
@Entity
@Table(name = "fo_integration")
@NoArgsConstructor
@Getter
@Setter
public class FrontOfficeIntegration {

    /** Which reservations of the property reach the front office. */
    public enum Scope {
        /** Every reservation of the property, wherever it was made. */
        ALL,
        /** Only those the chain's integration wrote: Opera's «Custom Reference». */
        CHAIN
    }

    @Id
    public String id;
    /** The PMS property: what the screens address it by. */
    @Column(nullable = false)
    public String pmsHotelCode;
    /** The front office's hotel, as it names itself. */
    public String frontOfficeCode;
    public String name;
    /** Where the front office answers the integration's queries; its commands go by Kafka. */
    public String frontOfficeUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Scope scope;
    /** How many days ahead of today the backfill and the polling look at. */
    public int horizonDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private FoIntegrationStatus status;
    public String processKey;
    public String gate;

    public Boolean connectivityOk;
    @Column(length = 2000)
    public String connectivityMessage;
    public Instant connectivityCheckedAt;

    public Instant catalogueRequestedAt;
    public String catalogueCommandId;
    @Column(length = 2000)
    public String catalogueSummary;
    public Instant catalogueSyncedAt;

    /** The PMS's last modification projected, ISO local date-time: the next poll asks from it. */
    public String pollCursor;
    public Instant lastPollAt;
    public Integer lastPollChanges;

    public Instant activationRequestedAt;
    public String activationRequestedBy;
    public Instant activatedAt;
    public Instant pausedAt;
    public String pausedBy;
    public Instant decommissionedAt;
    public String decommissionedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    public List<Integration.HistoryEntry> history = new ArrayList<>();

    public Instant createdAt;
    public String createdBy;
    @Version
    public Long version;

    public void begin() {
        if (status != null) {
            throw new IllegalStateException("pms-fo integration " + id + " has already begun; it is " + status);
        }
        status = FoIntegrationTransition.INITIAL;
    }

    public FoIntegrationStatus apply(FoIntegrationTransition transition) {
        status = transition.from(status);
        return status;
    }

    public boolean is(FoIntegrationStatus s) {
        return status == s;
    }

    public void record(Instant at, String by, String what) {
        if (history == null) {
            history = new ArrayList<>();
        }
        history.add(new Integration.HistoryEntry(at, by, what));
    }
}
