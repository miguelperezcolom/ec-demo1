package io.mateu.ecdemo1.integrations.store;

import io.mateu.ecdemo1.integration.model.integration.Gap;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A hotel's integration (HLA F010, «Aislamiento por hotel»): which Opera property the CRS hotel is,
 * how to reach it, and where it is in its life. One per CRS hotel.
 *
 * <p>The onboarding moves it through gates. {@link #gate} is the message the onboarding process is
 * waiting for now — or null when it waits for nothing — and what opens each gate is recorded here,
 * so the gate can be looked at again at any time and the message sent until the process moves on.
 */
@Entity
@Table(name = "integration")
@NoArgsConstructor
@Getter
@Setter
public class Integration {

    public record HistoryEntry(Instant at, String by, String what) {
    }

    @Id
    public String id;
    @Column(nullable = false, unique = true)
    public String crsHotelCode;
    @Column(nullable = false)
    public String pmsHotelCode;
    public String name;

    // How to reach the property. The secret is sealed (SecretBox): never in clear, never on a screen.
    public String gatewayUrl;
    public String appKey;
    public String clientId;
    @Column(length = 1024)
    public String clientSecretSealed;
    public String enterpriseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IntegrationStatus status;
    /** The onboarding's process key: what its gates' messages are correlated by. */
    public String processKey;
    /** The gate the onboarding waits at, as the message that opens it; null when it waits for nothing. */
    public String gate;

    public Boolean connectivityOk;
    @Column(length = 2000)
    public String connectivityMessage;
    public Instant connectivityCheckedAt;

    public Boolean propertyConfigured;
    @Column(length = 2000)
    public String contrastSummary;
    public Integer pendingMappings;
    public Instant contrastedAt;

    public Instant mappingApprovedAt;
    public String mappingApprovedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    public List<String> partnersMissing = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    public List<Gap> gaps = new ArrayList<>();
    public Integer futureReservations;
    public Instant gapsCheckedAt;

    /** While a backfill loads the property its availability is not to be trusted (HLA F010): since when. */
    public Instant availabilitySuspendedSince;
    public Instant availabilityResyncedAt;

    public Instant activationRequestedAt;
    public String activationRequestedBy;
    public Instant activatedAt;
    public Instant pausedAt;
    public String pausedBy;
    public Instant decommissionedAt;
    public String decommissionedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    public List<HistoryEntry> history = new ArrayList<>();

    public Instant createdAt;
    public String createdBy;
    @Version
    public Long version;

    public void record(Instant at, String by, String what) {
        if (history == null) {
            history = new ArrayList<>();
        }
        history.add(new HistoryEntry(at, by, what));
    }

    public IntegrationView view() {
        return new IntegrationView(id, crsHotelCode, pmsHotelCode, status);
    }
}
