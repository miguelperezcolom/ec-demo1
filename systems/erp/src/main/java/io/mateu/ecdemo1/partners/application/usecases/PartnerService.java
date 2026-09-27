package io.mateu.ecdemo1.partners.application.usecases;

import io.mateu.ecdemo1.partners.application.out.PartnerRepository;
import io.mateu.ecdemo1.partners.domain.partner.Partner;
import io.mateu.ecdemo1.partners.domain.partner.PartnerDetails;
import io.mateu.ecdemo1.partners.domain.partner.PmsProfile;
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

    private Partner locked(String code) {
        return repository.findByCodeForUpdate(code)
                .orElseThrow(() -> new NoSuchElementException("Partner not found: " + code));
    }
}
