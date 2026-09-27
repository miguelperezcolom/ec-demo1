package io.mateu.ecdemo1.mapping.queries;

import io.mateu.ecdemo1.mapping.store.PartnerProfile;
import io.mateu.ecdemo1.mapping.store.PartnerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The partners' PMS profiles, by partner code, paged by the database. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerProfileQueries {

    final PartnerProfileRepository profiles;

    /** The profiles whose partner code, profile type or PMS id contain {@code text}, case-insensitive. */
    public Page<PartnerProfile> page(String text, Pageable pageable) {
        return profiles.search(Like.contains(text), pageable);
    }
}
