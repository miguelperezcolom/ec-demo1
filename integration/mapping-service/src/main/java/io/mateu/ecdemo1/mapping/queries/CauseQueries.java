package io.mateu.ecdemo1.mapping.queries;

import io.mateu.ecdemo1.mapping.store.CauseRecord;
import io.mateu.ecdemo1.mapping.store.CauseRecordRepository;
import io.mateu.ecdemo1.mapping.store.Waiter;
import io.mateu.ecdemo1.mapping.store.WaiterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** What the causes' screens read: the causes, open ones first and oldest first, paged by the database. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CauseQueries {

    final CauseRecordRepository causes;
    final WaiterRepository waiters;

    /** The causes whose key contains {@code text}, case-insensitive. */
    public Page<CauseRecord> page(String text, Pageable pageable) {
        return causes.search(Like.contains(text), pageable);
    }

    public Optional<CauseRecord> cause(String key) {
        return causes.findById(key);
    }

    public long waitingOn(String causeKey) {
        return waiters.countWaitingOn(causeKey);
    }

    public List<Waiter> processesWaitingOn(String causeKey) {
        return waiters.waitingOn(causeKey);
    }
}
