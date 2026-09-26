package io.mateu.ecdemo1.partners.application.out;

import io.mateu.ecdemo1.partners.domain.partner.BillingMode;
import io.mateu.ecdemo1.partners.domain.partner.Partner;
import io.mateu.ecdemo1.partners.domain.partner.PartnerType;

import java.util.List;
import java.util.Optional;

/** Saving a partner also puts the events it recorded in the outbox, in the same transaction. */
public interface PartnerRepository {

    Optional<Partner> findByCode(String code);

    /** Holds the partner until the transaction ends, so two changes cannot hand out the same version. */
    Optional<Partner> findByCodeForUpdate(String code);

    List<Partner> search(String text, int page, int size);

    /** A page of the partners matching the text and every filter given (null: any), and how many match in all. */
    PartnerPage search(String text, PartnerType type, BillingMode billingMode, Boolean active, int page, int size);

    record PartnerPage(List<Partner> partners, long total) {
    }

    long count();

    void save(Partner partner);
}
