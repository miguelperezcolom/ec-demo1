package io.mateu.ecdemo1.partners.domain.partner;

import io.mateu.workflow.ddd.AggregateRoot;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * A trading partner as the master holds it. Every change bumps {@link #version} and announces it.
 * The code is the partner's identity for life: it is what reservations and the PMS refer to it by.
 */
@Getter
public class Partner extends AggregateRoot {

    private final String code;
    private PartnerDetails details;
    private boolean active;
    private final Instant created;
    private Instant updated;
    private long version;
    /** Which profile it is in the PMS, once it is one there. Not part of its details: integration data. */
    private PmsProfile pmsProfile;

    public Partner(String code, PartnerDetails details, boolean active, Instant created, Instant updated,
                   long version) {
        this(code, details, active, created, updated, version, null);
    }

    public Partner(String code, PartnerDetails details, boolean active, Instant created, Instant updated,
                   long version, PmsProfile pmsProfile) {
        this.code = code;
        this.details = details;
        this.active = active;
        this.created = created;
        this.updated = updated;
        this.version = version;
        this.pmsProfile = pmsProfile;
    }

    public static Partner create(String code, PartnerDetails details, Instant now) {
        if (code == null || !code.matches("[A-Z0-9-]{2,30}")) {
            throw new IllegalArgumentException("A partner code is 2 to 30 upper-case letters, digits or dashes");
        }
        var partner = new Partner(code, details, true, now, now, 1);
        partner.announce(now);
        return partner;
    }

    public void update(PartnerDetails details, Instant now) {
        this.details = details;
        changed(now);
    }

    /**
     * Records which profile the partner is in the PMS — created there by the integration, or found
     * there already. Not a change of the partner: no new version and nothing announced, or recording
     * it would project the partner again.
     */
    public void recordPmsProfile(PmsProfile profile, Instant now) {
        this.pmsProfile = profile;
        this.updated = now;
    }

    public void deactivate(Instant now) {
        if (active) {
            active = false;
            changed(now);
        }
    }

    public void activate(Instant now) {
        if (!active) {
            active = true;
            changed(now);
        }
    }

    /**
     * Announces the partner as it stands, without changing it — so whoever projects it can be made
     * to do it again.
     */
    public void announce(Instant now) {
        send(new PartnerChanged(UUID.randomUUID().toString(), code, version, now));
    }

    private void changed(Instant now) {
        version++;
        updated = now;
        announce(now);
    }
}
