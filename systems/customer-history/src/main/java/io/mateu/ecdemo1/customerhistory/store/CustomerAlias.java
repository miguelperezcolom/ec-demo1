package io.mateu.ecdemo1.customerhistory.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A customer code the MDM merged into another: {@code absorbedId} is now {@code survivorId}. The stays
 * keep the code they were closed with; reading a customer follows these rows instead — both ways: from
 * an absorbed code to its survivor, and from a survivor to every code that ends in it.
 */
@Entity
@Table(name = "customer_alias", indexes = @Index(name = "customer_alias_survivor", columnList = "survivorId"))
public class CustomerAlias {

    @Id
    @Column(length = 64)
    public String absorbedId;

    @Column(length = 64, nullable = false)
    public String survivorId;

    public Instant mergedAt;
}
