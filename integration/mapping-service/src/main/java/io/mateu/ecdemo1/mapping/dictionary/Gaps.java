package io.mateu.ecdemo1.mapping.dictionary;

import io.mateu.ecdemo1.integration.model.integration.FutureUsage;
import io.mateu.ecdemo1.integration.model.integration.Gap;
import io.mateu.ecdemo1.mapping.store.PartnerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The backfill's pre-pass (HLA F010, «Backfill» #10): of what the hotel's future reservations
 * really use, what has no approved equivalent or is not a PMS profile yet — most blocking first. A
 * query, it touches nothing: it is what keeps a backfill from suspending thousands of processes on
 * a handful of causes.
 */
@Service
@RequiredArgsConstructor
public class Gaps {

    final Dictionary dictionary;
    final PartnerProfileRepository partnerProfiles;

    public List<Gap> of(FutureUsage usage) {
        var gaps = new ArrayList<Gap>();
        for (var code : usage.codes()) {
            if (dictionary.resolve(usage.hotelCode(), code.type(), code.code()).isEmpty()) {
                gaps.add(new Gap("MAPPING", code.type().name(), code.code(), code.reservations()));
            }
        }
        for (var partner : usage.partners()) {
            if (!partnerProfiles.existsById(partner.partnerCode())) {
                gaps.add(new Gap("PARTNER", "PARTNER", partner.partnerCode(), partner.reservations()));
            }
        }
        gaps.sort(Comparator.comparingInt(Gap::reservations).reversed());
        return gaps;
    }
}
