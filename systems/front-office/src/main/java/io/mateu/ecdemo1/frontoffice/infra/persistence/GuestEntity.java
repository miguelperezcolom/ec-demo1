package io.mateu.ecdemo1.frontoffice.infra.persistence;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestTier;
import io.mateu.ecdemo1.frontoffice.domain.guest.Preference;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/** How a {@link Guest} is kept: the {@code guest} table and its preferences. */
@Table("guest")
record GuestEntity(
    @Id String id,
    String name,
    String document,
    boolean documentVerified,
    String email,
    String phone,
    GuestTier tier,
    int loyaltyPoints,
    int stays,
    int nights,
    int yearsAsClient,
    int complaints,
    int hotels,
    String lastStaySummary,
    String lastStayComplementaryInfo,
    @MappedCollection(idColumn = "guest_id", keyColumn = "idx") List<PreferenceRow> preferences) {

  @Table("guest_preference")
  record PreferenceRow(String text) {}

  static GuestEntity of(Guest g) {
    return new GuestEntity(g.id(), g.name(), g.document(), g.documentVerified(), g.email(), g.phone(), g.tier(),
        g.loyaltyPoints(), g.stays(), g.nights(), g.yearsAsClient(), g.complaints(), g.hotels(), g.lastStaySummary(),
        g.lastStayComplementaryInfo(), g.preferences().stream().map(p -> new PreferenceRow(p.text())).toList());
  }

  Guest toDomain() {
    return new Guest(id, name, document, documentVerified, email, phone, tier, loyaltyPoints, stays, nights,
        yearsAsClient, complaints, hotels, lastStaySummary, lastStayComplementaryInfo,
        preferences == null ? List.of() : preferences.stream().map(p -> new Preference(p.text())).toList());
  }
}
