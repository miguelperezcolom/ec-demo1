package io.mateu.ecdemo1.mapping.dictionary;

import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.store.PartnerProfile;
import io.mateu.ecdemo1.mapping.store.PartnerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Which PMS profile a partner already is, as an import from the PMS found it — when the PMS is where
 * the chain's partners were kept. Whatever waited for the partner to be a profile goes on. Recorded
 * twice, it changes nothing.
 */
@Service
@RequiredArgsConstructor
public class ImportedProfiles {

    final PartnerProfileRepository partnerProfiles;
    final Causes causes;
    final Clock clock;

    @Transactional
    public PartnerProfile record(String partnerCode, String pmsProfileId, String profileType) {
        var profile = partnerProfiles.findById(partnerCode).orElseGet(PartnerProfile::new);
        var unchanged = pmsProfileId.equals(profile.getPmsProfileId()) && profileType.equals(profile.getProfileType());
        profile.setPartnerCode(partnerCode);
        profile.setPmsProfileId(pmsProfileId);
        profile.setProfileType(profileType);
        if (!unchanged) {
            profile.setUpdatedAt(clock.instant());
            partnerProfiles.save(profile);
        }
        causes.resolveIfOpen(Cause.missingPartner(partnerCode).key(), "import from the PMS");
        return profile;
    }
}
