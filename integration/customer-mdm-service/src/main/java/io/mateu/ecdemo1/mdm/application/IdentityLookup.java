package io.mateu.ecdemo1.mdm.application;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.documents.CustomerDocuments;
import io.mateu.ecdemo1.mdm.resolution.Normalizer;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.Xref;
import io.mateu.ecdemo1.mdm.store.XrefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * The desk asks who a guest is — by the document in their hand, their email, their Riu Class card — or
 * who they might be — by their name and birth date. Read only: what the desk then confirms comes back as a
 * scan ({@code RecordScannedIdentity.confirmedCustomerId}), and only then does the MDM consolidate anything.
 *
 * <p>Every query is on an indexed key, never a walk through every customer: the desk is waiting.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityLookup {

    static final EnumSet<CustomerStatus> LIVE = EnumSet.of(CustomerStatus.PROVISIONAL, CustomerStatus.CONSOLIDATED);
    static final int MAX_CANDIDATES = 5;

    final CustomerRepository customers;
    final XrefRepository xrefs;
    final CustomerDocuments documents;

    /** What an exact lookup found: one customer, nobody, or more than one. */
    public sealed interface Lookup permits Found, NotFound, Ambiguous {
    }

    /** The one customer the key is — the survivor, if it was merged. */
    public record Found(String customerId, CustomerStatus status, String matchedBy, String firstName, String lastName,
                        LocalDate birthDate) implements Lookup {
    }

    public record NotFound(String matchedBy) implements Lookup {
    }

    /** More than one customer holds it: how many, and nothing about any of them. */
    public record Ambiguous(boolean ambiguous, String matchedBy, int count) implements Lookup {
    }

    /** Someone the guest may be: same name and birth date, and nationality if it says so. */
    public record Candidate(String customerId, CustomerStatus status, String firstName, String lastName,
                            LocalDate birthDate, String nationality, List<String> matched) {
    }

    /**
     * By exactly one key: a document (and, if known, the country that issued it — the same number issued by
     * two countries is two documents), an email, or a Riu Class number.
     *
     * @throws IllegalArgumentException when not exactly one key is given
     */
    public Lookup lookup(String documentNumber, String country, String email, String riuClass) {
        var given = (blank(documentNumber) ? 0 : 1) + (blank(email) ? 0 : 1) + (blank(riuClass) ? 0 : 1);
        if (given != 1) {
            throw new IllegalArgumentException("Exactly one of documentNumber (with an optional country), email or riuClass");
        }
        if (!blank(documentNumber)) {
            return answer("DOCUMENT", documents.owners(documentNumber, blank(country) ? null : country));
        }
        if (!blank(email)) {
            return answer("EMAIL", survivors(customers.findByEmailKeyAndStatusIn(Normalizer.email(email), LIVE).stream()
                    .map(c -> c.id).toList()));
        }
        var reference = Xref.reference(Xref.Target.RIU_CLASS, riuClass);
        return answer("RIU_CLASS", survivors(xrefs.findBySystemAndReference(Xref.Target.RIU_CLASS.name(), reference).stream()
                .map(x -> x.customerId).toList()));
    }

    /**
     * Who the guest may be, for the desk to ask them: live customers born that day whose name, however
     * spelled, is the guest's — the same nationality first, then the consolidated ones, then the most
     * recently changed. Without a birth date, nobody: a name alone is too many people. Never merges.
     */
    public List<Candidate> candidates(String firstName, String lastName, LocalDate birthDate, String nationality) {
        var name = Normalizer.name(firstName, lastName);
        if (birthDate == null || name == null) {
            return List.of();
        }
        var country = blank(nationality) ? null : nationality.trim().toUpperCase(Locale.ROOT);
        return customers.findByBirthDateAndStatusIn(birthDate, LIVE).stream()
                .filter(c -> c.aliasOf == null && name.equals(Normalizer.name(c.firstName, c.lastName)))
                .sorted(Comparator.comparing((Customer c) -> country == null || !country.equalsIgnoreCase(c.nationality))
                        .thenComparing(c -> c.status != CustomerStatus.CONSOLIDATED)
                        .thenComparing(c -> c.updatedAt == null ? Instant.MIN : c.updatedAt, Comparator.reverseOrder()))
                .limit(MAX_CANDIDATES)
                .map(c -> {
                    var matched = new ArrayList<>(List.of("NAME", "BIRTH_DATE"));
                    if (country != null && country.equalsIgnoreCase(c.nationality)) {
                        matched.add("NATIONALITY");
                    }
                    return new Candidate(c.id, c.status, c.firstName, c.lastName, c.birthDate, c.nationality, List.copyOf(matched));
                })
                .toList();
    }

    static Lookup answer(String matchedBy, List<Customer> found) {
        if (found.isEmpty()) {
            return new NotFound(matchedBy);
        }
        if (found.size() > 1) {
            return new Ambiguous(true, matchedBy, found.size());
        }
        var c = found.get(0);
        return new Found(c.id, c.status, matchedBy, c.firstName, c.lastName, c.birthDate);
    }

    /** Each code's survivor, once, if it is live. */
    List<Customer> survivors(List<String> ids) {
        var found = new LinkedHashMap<String, Customer>();
        for (var id : ids) {
            var c = customers.findById(id).orElse(null);
            var hops = 0;
            while (c != null && c.aliasOf != null && hops++ < 20) {
                c = customers.findById(c.aliasOf).orElse(null);
            }
            if (c != null && LIVE.contains(c.status)) {
                found.putIfAbsent(c.id, c);
            }
        }
        return List.copyOf(found.values());
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
