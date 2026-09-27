package io.mateu.ecdemo1.communication.application;

import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** The read side of the recipients: a page of them, by name unless the page asks for another order, and one. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipientQueries {

    public static final Sort BY_NAME = Sort.by("name", "id");

    final RecipientRepository recipients;

    public Page<Recipient> page(Pageable pageable) {
        return recipients.findAll(pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_NAME));
    }

    public Optional<Recipient> find(String id) {
        return recipients.findById(id);
    }
}
