package io.mateu.ecdemo1.partners.infra.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "partner")
@NoArgsConstructor
@Getter
@Setter
public class PartnerEntity {

    @Id
    String code;
    @Column(nullable = false)
    String type;
    @Column(nullable = false)
    String name;
    String taxId;
    String addressLine;
    String city;
    String postalCode;
    String countryCode;
    String email;
    String phone;
    @Column(nullable = false)
    String billingMode;
    boolean active;
    long version;
    Instant created;
    Instant updated;
}
