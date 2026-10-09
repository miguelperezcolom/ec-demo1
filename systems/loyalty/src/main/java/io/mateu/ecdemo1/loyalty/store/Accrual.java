package io.mateu.ecdemo1.loyalty.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The points a stay earned a member — its audit and its idempotency: the id is the stay and the member
 * ({@code stayId|memberNumber}), so a StayClosed delivered again earns nothing a second time.
 */
@Entity
@Table(name = "accrual", indexes = @Index(name = "accrual_member", columnList = "memberNumber"))
public class Accrual {

    @Id
    @Column(length = 200)
    public String id;

    @Column(length = 32, nullable = false)
    public String memberNumber;

    @Column(length = 100, nullable = false)
    public String stayId;

    @Column(length = 20)
    public String hotelCode;

    public int nights;

    public long points;

    /** When the stay closed (the event's time), or when it was taken if the event has none. */
    public Instant at;

    public static String id(String stayId, String memberNumber) {
        return stayId + "|" + memberNumber;
    }
}
