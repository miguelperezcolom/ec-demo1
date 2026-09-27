package io.mateu.ecdemo1.booking.infra.out.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/** A rate plan opened in a hotel while the CRS runs. */
@Entity
@Table(name = "catalog_rate_plan")
@IdClass(AddedRatePlanEntity.Key.class)
@NoArgsConstructor
public class AddedRatePlanEntity {

    @Id
    String hotelCode;
    @Id
    String code;
    String name;
    BigDecimal factor;
    Instant addedAt;
    String addedBy;

    AddedRatePlanEntity(String hotelCode, String code, String name, BigDecimal factor, Instant addedAt, String addedBy) {
        this.hotelCode = hotelCode;
        this.code = code;
        this.name = name;
        this.factor = factor;
        this.addedAt = addedAt;
        this.addedBy = addedBy;
    }

    public record Key(String hotelCode, String code) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
