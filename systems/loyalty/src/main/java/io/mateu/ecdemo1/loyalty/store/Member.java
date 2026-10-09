package io.mateu.ecdemo1.loyalty.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A Riu Class member: its card number, the MDM customer it is (C-…), its tier and its points.
 *
 * <p>The tier is kept as a plain string, not as a JPA enum: Hibernate's {@code ddl-auto: update}
 * writes a check constraint for an enum column and never rewrites it, so a tier added later would be
 * refused by every database created before it.
 */
@Entity
@Table(name = "member", indexes = @Index(name = "member_customer_code", columnList = "customerCode"))
public class Member {

    /** The card number (RC12345678), trimmed and uppercase — how it is looked up. */
    @Id
    @Column(length = 32)
    public String memberNumber;

    /** The MDM's customer code; null for a member the MDM does not know yet. */
    @Column(length = 64)
    public String customerCode;

    @Column(length = 16, nullable = false)
    public String tier;

    public long points;

    public LocalDate memberSince;

    public Instant updatedAt;

    public Tier tier() {
        return Tier.parse(tier);
    }

    /** The number as it is kept and looked up: trimmed and uppercase, so " rc12345678" finds RC12345678. */
    public static String number(String memberNumber) {
        return memberNumber == null ? null : memberNumber.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
