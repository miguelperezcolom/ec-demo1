package io.mateu.ecdemo1.users.application.usecases.user.directory;

import io.mateu.ecdemo1.messaging.Inbox;
import io.mateu.ecdemo1.users.application.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers a request for the whole directory ({@code employee-directory-requests}) by republishing every
 * user as an upsert on {@code employee-directory}. The content service asks when its directory is empty
 * — at its first start, or after a reset — because the topic alone cannot rebuild it: what was
 * published before it subscribed, or was compacted away, is not there to read.
 *
 * <p>Each request is answered once: its id goes into the inbox in the same transaction as the
 * messages, so a redelivered request does nothing, and a request whose transaction failed is answered
 * on its redelivery. All users in one transaction — a few dozen in the demo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublishEmployeeDirectorySnapshotUseCase {

    static final String CONSUMER = "employee-directory-requests";

    final Inbox inbox;
    final UserRepository users;
    final EmployeeDirectoryOutboxAppender directory;

    /** @return how many users were republished; -1 for a request already answered */
    @Transactional
    public int handle(String requestId, String requestedBy) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("A directory request needs its requestId");
        }
        if (!inbox.firstTime(CONSUMER, requestId)) {
            log.debug("Directory request {} already answered", requestId);
            return -1;
        }
        var all = users.findAll();
        all.forEach(directory::appendCurrent);
        log.info("Directory request {} from {}: {} employee(s) republished", requestId, requestedBy, all.size());
        return all.size();
    }
}
