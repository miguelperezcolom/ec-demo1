package io.mateu.ecdemo1.erp.application.usecases;

import io.mateu.ecdemo1.erp.application.out.PartnerRepository;
import io.mateu.ecdemo1.erp.domain.partner.Partner;
import io.mateu.ecdemo1.erp.domain.partner.BillingMode;
import io.mateu.ecdemo1.erp.domain.partner.PartnerDetails;
import io.mateu.ecdemo1.erp.domain.partner.PartnerType;
import io.mateu.ecdemo1.erp.domain.partner.PmsProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;

/** The master's use cases. Every one that changes a partner goes through the locked read. */
@Service
@RequiredArgsConstructor
public class PartnerService {

    final PartnerRepository repository;
    final Clock clock;

    @Transactional
    public String create(String code, PartnerDetails details) {
        if (repository.findByCode(code).isPresent()) {
            throw new IllegalStateException("Partner %s already exists".formatted(code));
        }
        repository.save(Partner.create(code, details, clock.instant()));
        return code;
    }

    @Transactional
    public void update(String code, PartnerDetails details) {
        var partner = locked(code);
        partner.update(details, clock.instant());
        repository.save(partner);
    }

    @Transactional
    public void setActive(String code, boolean active) {
        var partner = locked(code);
        if (active) {
            partner.activate(clock.instant());
        } else {
            partner.deactivate(clock.instant());
        }
        repository.save(partner);
    }

    /** Records which profile the partner is in the PMS; announces nothing. */
    @Transactional
    public void recordPmsProfile(String code, PmsProfile profile) {
        var partner = locked(code);
        partner.recordPmsProfile(profile, clock.instant());
        repository.save(partner);
    }

    /** Announces the partner again, unchanged, so the integration projects it once more. */
    @Transactional
    public void resync(String code) {
        var partner = locked(code);
        partner.announce(clock.instant());
        repository.save(partner);
    }

    /**
     * A partner as the PMS has it, brought in: created if the master does not have it — who pays is the
     * guest, at the desk, until someone here says otherwise — or its name and type brought up to date,
     * keeping what only the master knows (tax id, address, contact, billing). Then which PMS profile it
     * is, so that it is never created there again. Brought in twice, the second time changes nothing.
     */
    @Transactional
    public void importFromPms(String code, PartnerType type, String name, PmsProfile profile) {
        var existing = repository.findByCodeForUpdate(code);
        Partner partner;
        if (existing.isEmpty()) {
            partner = Partner.create(code, new PartnerDetails(type, name, null, null, null, null, BillingMode.Front),
                    clock.instant());
        } else {
            partner = existing.get();
            var d = partner.getDetails();
            if (d.type() != type || !d.name().equals(name)) {
                partner.update(new PartnerDetails(type, name, d.taxId(), d.address(), d.email(), d.phone(), d.billingMode()),
                        clock.instant());
            }
        }
        partner.recordPmsProfile(profile, clock.instant());
        repository.save(partner);
    }

    private Partner locked(String code) {
        return repository.findByCodeForUpdate(code)
                .orElseThrow(() -> new NoSuchElementException("Partner not found: " + code));
    }
}
