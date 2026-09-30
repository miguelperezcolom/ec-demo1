package io.mateu.ecdemo1.notices;

import io.mateu.ecdemo1.integration.model.notice.NoticeChanged;
import io.mateu.ecdemo1.notices.application.Notices;
import io.mateu.ecdemo1.notices.store.Notice;
import io.mateu.ecdemo1.notices.store.NoticeRepository;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The notices service on memory: its repository, what it publishes, its inbox and the ERP's partners. */
public class InMemory {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T09:30:00Z"), ZoneOffset.UTC);

    public final Map<String, Notice> rows = new LinkedHashMap<>();
    public final List<NoticeChanged> published = new ArrayList<>();
    public final Set<String> seen = new HashSet<>();
    public final Map<String, String> partners = new LinkedHashMap<>(Map.of("NORDTRAVEL", "Nordic Travel Group AB"));

    public final NoticeRepository repository = (NoticeRepository) Proxy.newProxyInstance(
            InMemory.class.getClassLoader(), new Class<?>[]{NoticeRepository.class}, (proxy, method, args) ->
                    switch (method.getName()) {
                        case "save" -> {
                            var n = (Notice) args[0];
                            rows.put(n.id, n);
                            yield n;
                        }
                        case "findById" -> Optional.ofNullable(rows.get((String) args[0]));
                        case "findAll" -> new ArrayList<>(rows.values());
                        case "findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc" -> rows.values().stream()
                                .filter(n -> n.subjectType.equals(args[0]) && n.subjectId.equals(args[1]))
                                .sorted(Comparator.comparing(n -> n.createdAt)).toList();
                        case "findBySubjectTypeAndActiveTrue" -> rows.values().stream()
                                .filter(n -> n.subjectType.equals(args[0]) && n.active).toList();
                        case "toString" -> "InMemory repository";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });

    public final Notices notices = new Notices(repository, published::add,
            code -> Optional.ofNullable(partners.get(code)), (consumer, id) -> seen.add(consumer + "/" + id), CLOCK);
}
