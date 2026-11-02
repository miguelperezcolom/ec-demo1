package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.UsageRepository;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;

/**
 * Writes down what readers do, for the editors' figures. An editor looking through «ver como» is not a
 * reader, and a failure here never reaches the page: the figures are not worth anyone's screen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsageTracker {

    static final int MAX_QUERY = 200;

    final UsageRepository usage;
    final Clock clock;

    public void itemViewed(Reader reader, String itemId) {
        write(reader, new UsageRepository.Event(UsageRepository.Kind.ITEM_VIEW, clock.instant(), reader.userId(),
                reader.attributes().hash(), itemId, null, null));
    }

    public void homeVisited(Reader reader) {
        write(reader, new UsageRepository.Event(UsageRepository.Kind.HOME_VISIT, clock.instant(), reader.userId(),
                reader.attributes().hash(), null, null, null));
    }

    /** What was searched and how much it found, with the reader's profile hash and not who they are. */
    public void searched(Reader reader, String text, long results) {
        var query = normalise(text);
        if (!query.isEmpty()) {
            write(reader, new UsageRepository.Event(UsageRepository.Kind.SEARCH, clock.instant(), null,
                    reader.attributes().hash(), null, query, Math.toIntExact(Math.min(results, Integer.MAX_VALUE))));
        }
    }

    /** «Vacaciones  » and «vacaciones» are the same search. */
    static String normalise(String text) {
        var query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return query.length() <= MAX_QUERY ? query : query.substring(0, MAX_QUERY);
    }

    void write(Reader reader, UsageRepository.Event event) {
        if (reader.viewingAs() != null) {
            return;
        }
        try {
            usage.record(event);
        } catch (RuntimeException e) {
            log.warn("Usage event not recorded: {}", e.getMessage());
        }
    }
}
