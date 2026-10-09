package io.mateu.ecdemo1.customerhistory.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One customer's part in one closed stay: a stay with two customers of the chain on it is two rows.
 * Its id is the pair (stay, customer), so taking the same stay again — a redelivery, the demo's seed run
 * twice — writes the same row, never a second one.
 *
 * <p>The customer is the code the stay was closed with, never rewritten: a merge in the MDM is resolved
 * when the history is read (see {@code customer_alias}). The source is a plain string, not a JPA enum:
 * Hibernate's {@code ddl-auto: update} freezes an enum's check constraint at the table's creation.
 */
@Entity
@Table(name = "customer_stay",
        uniqueConstraints = @UniqueConstraint(name = "customer_stay_stay_customer", columnNames = {"stayId", "customerId"}),
        indexes = @Index(name = "customer_stay_customer_departure", columnList = "customerId, departure desc"))
public class CustomerStay {

    public static final String FRONT_OFFICE = "FRONT_OFFICE";
    public static final String DEMO = "DEMO";

    /** {@code <stayId>|<customerId>}: the pair, as one key. */
    @Id
    @Column(length = 200)
    public String id;

    @Column(length = 64, nullable = false)
    public String customerId;

    @Column(length = 20)
    public String hotelCode;

    @Column(length = 100, nullable = false)
    public String stayId;

    @Column(length = 40)
    public String crsLocator;

    public LocalDate arrival;

    public LocalDate departure;

    public int nights;

    @Column(length = 20)
    public String roomNumber;

    @Column(length = 40)
    public String roomType;

    @Column(length = 40)
    public String board;

    /** Whether this customer held the reservation. */
    public boolean holder;

    @Column(precision = 14, scale = 2)
    public BigDecimal addOnTotal;

    @Column(precision = 14, scale = 2)
    public BigDecimal lateCheckOutTotal;

    @Column(precision = 14, scale = 2)
    public BigDecimal consumptionTotal;

    @Column(precision = 14, scale = 2)
    public BigDecimal total;

    @Column(length = 3)
    public String currency;

    public Instant closedAt;

    @Column(length = 20, nullable = false)
    public String source;

    public static String id(String stayId, String customerId) {
        return stayId + "|" + customerId;
    }
}
