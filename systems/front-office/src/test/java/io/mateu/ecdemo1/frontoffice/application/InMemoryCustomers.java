package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory;
import io.mateu.ecdemo1.frontoffice.domain.customer.LoyaltyStatus;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The MDM, the customer history and Riu Class, in memory: what the tests of the desk's recognition set up. */
final class InMemoryCustomers {

  /** The MDM: documents, emails and member numbers to customers; candidates by surname; down when told. */
  static class Directory implements CustomerDirectory {
    final Map<String, Lookup> byKey = new ConcurrentHashMap<>();
    final Map<String, List<Candidate>> candidatesByLastName = new ConcurrentHashMap<>();
    final List<String> writes = new ArrayList<>();
    volatile boolean down;

    void document(String number, Customer customer) {
      byKey.put("doc:" + number, Lookup.found(customer, "DOCUMENT"));
    }

    void ambiguousDocument(String number) {
      byKey.put("doc:" + number, Lookup.ambiguous("DOCUMENT", 2));
    }

    void riuClass(String number, Customer customer) {
      byKey.put("rc:" + number, Lookup.found(customer, "RIU_CLASS"));
    }

    void email(String email, Customer customer) {
      byKey.put("email:" + email, Lookup.found(customer, "EMAIL"));
    }

    void candidate(String lastName, Candidate candidate) {
      candidatesByLastName.computeIfAbsent(lastName, k -> new ArrayList<>()).add(candidate);
    }

    void reset() {
      byKey.clear();
      candidatesByLastName.clear();
      writes.clear();
      operaProfiles.clear();
      down = false;
    }

    @Override
    public Lookup lookup(LookupQuery q) {
      if (down) {
        throw new IllegalStateException("MDM down");
      }
      var key = q.documentNumber() != null ? "doc:" + q.documentNumber()
          : q.email() != null ? "email:" + q.email() : "rc:" + q.riuClass();
      return byKey.getOrDefault(key, Lookup.none());
    }

    @Override
    public List<Candidate> candidates(String firstName, String lastName, LocalDate birthDate, String nationality) {
      if (down || birthDate == null) {
        return List.of();
      }
      return candidatesByLastName.getOrDefault(lastName, List.of()).stream()
          .filter(c -> birthDate.equals(c.birthDate())).toList();
    }

    @Override
    public boolean addDocument(String customerId, String type, String number, String issuingCountry, LocalDate expiry,
                               String origin, LocalDate birthDate, String nationality) {
      writes.add("document " + customerId + " " + type + " " + number + " " + issuingCountry + " " + origin);
      return true;
    }

    final Map<String, String> operaProfiles = new ConcurrentHashMap<>();

    @Override
    public java.util.Optional<String> byOperaProfile(String profileId) {
      return down ? java.util.Optional.empty() : java.util.Optional.ofNullable(operaProfiles.get(profileId));
    }

    @Override
    public boolean setRiuClass(String customerId, String memberNumber) {
      writes.add("riuClass " + customerId + " " + memberNumber);
      return true;
    }
  }

  static class History implements StayHistory {
    final Map<String, HistorySummary> summaries = new ConcurrentHashMap<>();
    final Map<String, Integer> seeded = new ConcurrentHashMap<>();

    @Override
    public Optional<HistorySummary> summary(String customerCode) {
      return Optional.ofNullable(summaries.get(customerCode));
    }

    @Override
    public boolean seedDemo(String customerCode, int count) {
      seeded.put(customerCode, count);
      return true;
    }
  }

  static class RiuClass implements LoyaltyStatus {
    final Map<String, Member> members = new ConcurrentHashMap<>();

    record Member(String customerCode, String tier, int points, LocalDate since) {}

    @Override
    public Optional<LoyaltyStatus.Loyalty> of(String customerCode, String riuClassNumber) {
      return members.entrySet().stream()
          .filter(e -> riuClassNumber != null ? e.getKey().equals(riuClassNumber) : e.getValue().customerCode().equals(customerCode))
          .findFirst()
          .map(e -> new LoyaltyStatus.Loyalty(e.getValue().tier(), e.getValue().points(), e.getKey(), e.getValue().since(),
              LocalDate.now(), "Riu Class"));
    }

    @Override
    public boolean enroll(String memberNumber, String customerCode, String tier, int points, LocalDate memberSince) {
      members.put(memberNumber, new Member(customerCode, tier, points, memberSince));
      return true;
    }
  }

  private InMemoryCustomers() {}
}
