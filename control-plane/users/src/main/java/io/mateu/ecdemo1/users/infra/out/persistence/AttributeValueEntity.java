package io.mateu.ecdemo1.users.infra.out.persistence;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor@NoArgsConstructor
@Getter
public class AttributeValueEntity {

    /** {@code ATTRIBUTE:CODE}, e.g. {@code HOTEL:MRU01}. */
    @Id
    String id;

    String attribute;

    String code;

    String label;

    /** A hotel's country code; null for the other attributes. */
    String country;

    /** A hotel's region code; null for the other attributes. */
    String region;

}
