package io.mateu.ecdemo1.mdm.application;

import io.mateu.ecdemo1.mdm.store.Consolidation;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The read side of what Salesforce's cleaning concluded: a page of it, newest first unless the page asks for another order. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsolidationQueries {

    public static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "receivedAt");

    final ConsolidationRepository consolidations;

    public Page<Consolidation> page(Pageable pageable) {
        return consolidations.findAll(pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
    }
}
