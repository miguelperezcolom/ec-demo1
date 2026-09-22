package io.mateu.ecdemo1.partners.infra.out.persistence;

import io.mateu.ecdemo1.partners.application.out.PartnerRepository;
import io.mateu.ecdemo1.partners.domain.partner.Address;
import io.mateu.ecdemo1.partners.domain.partner.BillingMode;
import io.mateu.ecdemo1.partners.domain.partner.Partner;
import io.mateu.ecdemo1.partners.domain.partner.PartnerChanged;
import io.mateu.ecdemo1.partners.domain.partner.PartnerDetails;
import io.mateu.ecdemo1.partners.domain.partner.PartnerType;
import io.mateu.ecdemo1.partners.infra.out.outbox.OutboxWriter;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PartnerDBRepository implements PartnerRepository {

    final PartnerEntityRepository repository;
    final EntityManager entityManager;
    final OutboxWriter outbox;

    @Override
    public Optional<Partner> findByCode(String code) {
        return repository.findById(code).map(PartnerDBRepository::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Partner> findByCodeForUpdate(String code) {
        return repository.findByCodeForUpdate(code).map(PartnerDBRepository::toDomain);
    }

    @Override
    public List<Partner> search(String text, int page, int size) {
        return repository.search(text, PageRequest.of(page, size)).map(PartnerDBRepository::toDomain).getContent();
    }

    @Override
    public long count() {
        return repository.count();
    }

    /**
     * Writes the partner and its events. An event announcing a new version must continue the stored
     * one; a re-announcement carries the stored version itself.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Partner partner) {
        var events = partner.popEvents().stream().map(PartnerChanged.class::cast).toList();
        var existing = repository.findById(partner.getCode());
        if (existing.isPresent()) {
            var stored = existing.get().version;
            if (partner.getVersion() != stored && partner.getVersion() != stored + 1) {
                throw new IllegalStateException("Partner %s changed concurrently: stored version %d, saving %d"
                        .formatted(partner.getCode(), stored, partner.getVersion()));
            }
            copy(partner, existing.get());
        } else {
            var entity = new PartnerEntity();
            copy(partner, entity);
            entityManager.persist(entity);
        }
        events.forEach(outbox::append);
    }

    static Partner toDomain(PartnerEntity e) {
        return new Partner(e.code,
                new PartnerDetails(PartnerType.valueOf(e.type), e.name, e.taxId,
                        new Address(e.addressLine, e.city, e.postalCode, e.countryCode),
                        e.email, e.phone, BillingMode.valueOf(e.billingMode)),
                e.active, e.created, e.updated, e.version);
    }

    static void copy(Partner p, PartnerEntity e) {
        var d = p.getDetails();
        e.code = p.getCode();
        e.type = d.type().name();
        e.name = d.name();
        e.taxId = d.taxId();
        e.addressLine = d.address() != null ? d.address().line() : null;
        e.city = d.address() != null ? d.address().city() : null;
        e.postalCode = d.address() != null ? d.address().postalCode() : null;
        e.countryCode = d.address() != null ? d.address().countryCode() : null;
        e.email = d.email();
        e.phone = d.phone();
        e.billingMode = d.billingMode().name();
        e.active = p.isActive();
        e.version = p.getVersion();
        e.created = p.getCreated();
        e.updated = p.getUpdated();
    }
}
