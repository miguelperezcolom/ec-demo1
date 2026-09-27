package io.mateu.ecdemo1.mapping.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Which PMS profile a partner of the CRS is, and at which version of the partner it was last
 * projected. An equivalence between identifiers — the mapping's job — and metadata of the
 * integration: it is kept here and not written into either system (HLA, «Proyectar Interlocutor»).
 * By partner, not by hotel: in Opera a profile belongs to the chain (R12).
 */
@Entity
@Table(name = "partner_profile")
@NoArgsConstructor
@Getter
@Setter
public class PartnerProfile {

    @Id
    public String partnerCode;
    public String profileType;
    public String pmsProfileId;
    public long projectedVersion;
    public Instant updatedAt;
}
