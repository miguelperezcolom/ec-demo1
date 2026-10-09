package io.mateu.ecdemo1.loyalty.application;

import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.StayClosed;
import io.mateu.ecdemo1.loyalty.store.Accrual;
import io.mateu.ecdemo1.loyalty.store.AccrualRepository;
import io.mateu.ecdemo1.loyalty.store.Member;
import io.mateu.ecdemo1.loyalty.store.MemberRepository;
import io.mateu.ecdemo1.loyalty.store.Tier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Riu Class: the members, kept here (seeded by the demo, or created on the console), and the points
 * a closed stay earns them — 100 a night for the holder, 50 for a companion, once per stay and member.
 * A merge in the MDM moves a membership to the surviving customer, so the front office still finds it
 * by the code it now knows the guest by.
 */
@Service
@Slf4j
public class Loyalty {

    /** The inbox's consumer names. */
    public static final String FRONT_OFFICE_EVENTS = "front-office-events";
    public static final String CUSTOMERS = "customers";

    public static final int HOLDER_POINTS_PER_NIGHT = 100;
    public static final int COMPANION_POINTS_PER_NIGHT = 50;

    /** Where a consumer deduplicates what it receives (at-least-once delivery). */
    public interface Inbox {
        boolean firstTime(String consumer, String messageId);
    }

    /** What a member is set to: every field but the customer optional — what is not sent is kept. */
    public record MemberUpdate(String customerCode, Tier tier, Long points, LocalDate memberSince) {
    }

    final MemberRepository members;
    final AccrualRepository accruals;
    final Inbox inbox;
    final Clock clock;

    public Loyalty(MemberRepository members, AccrualRepository accruals, Inbox inbox, Clock clock) {
        this.members = members;
        this.accruals = accruals;
        this.inbox = inbox;
        this.clock = clock;
    }

    // ── reading ─────────────────────────────────────────────────────────────────

    public Optional<Member> find(String memberNumber) {
        var number = Member.number(memberNumber);
        return number == null || number.isEmpty() ? Optional.empty() : members.findById(number);
    }

    public Member get(String memberNumber) {
        return find(memberNumber).orElseThrow(() -> new NoSuchElementException("No member " + memberNumber));
    }

    /** A customer's membership; if a merge left it two, the most recently updated. */
    public Optional<Member> findByCustomer(String customerCode) {
        var code = trimmed(customerCode);
        return code == null ? Optional.empty() : members.findFirstByCustomerCodeOrderByUpdatedAtDesc(code);
    }

    public List<Accrual> accrualsOf(String memberNumber) {
        return accruals.findByMemberNumberOrderByAtDesc(Member.number(memberNumber));
    }

    // ── changing ────────────────────────────────────────────────────────────────

    /**
     * Creates or changes a member (the demo's seeding, the console). A tier sent is set as it is — an
     * explicit grant may be above or below what the points are worth; without one, a new member gets
     * the tier its points are worth and an existing one never goes down.
     */
    @Transactional
    public Member upsert(String memberNumber, MemberUpdate update) {
        var number = Member.number(memberNumber);
        if (number == null || number.isEmpty()) {
            throw new IllegalArgumentException("A member number is required");
        }
        var code = trimmed(update.customerCode());
        if (code == null) {
            throw new IllegalArgumentException("A customer code is required");
        }
        if (update.points() != null && update.points() < 0) {
            throw new IllegalArgumentException("Points cannot be negative");
        }
        var m = members.findById(number).orElseGet(() -> {
            var created = new Member();
            created.memberNumber = number;
            created.memberSince = LocalDate.now(clock);
            return created;
        });
        m.customerCode = code;
        if (update.points() != null) {
            m.points = update.points();
        }
        if (update.memberSince() != null) {
            m.memberSince = update.memberSince();
        }
        m.tier = (update.tier() != null ? update.tier() : Tier.max(m.tier(), Tier.byPoints(m.points))).name();
        m.updatedAt = clock.instant();
        return members.save(m);
    }

    /** A new member, refused if the number is taken — the console's create, which must not overwrite one. */
    @Transactional
    public Member create(String memberNumber, MemberUpdate update) {
        if (find(memberNumber).isPresent()) {
            throw new IllegalArgumentException("Member " + Member.number(memberNumber) + " already exists");
        }
        return upsert(memberNumber, update);
    }

    /**
     * The points a closed stay earns every member among its guests: nights × 100 for the holder,
     * nights × 50 for a companion. A guest is a member when the id the front office knows them by is a
     * member's customer code (a guest without an MDM code is no one's). Once per stay and member: the
     * accrual's id makes a redelivery — or the same stay closed twice — earn nothing more.
     *
     * @return the accruals this call made
     */
    @Transactional
    public List<Accrual> accrue(StayClosed stay) {
        if (stay.eventId() != null && !inbox.firstTime(FRONT_OFFICE_EVENTS, stay.eventId())) {
            return List.of();
        }
        var made = new ArrayList<Accrual>();
        if (stay.guests() == null || stay.nights() <= 0 || stay.stayId() == null) {
            return made;
        }
        for (var guest : stay.guests()) {
            if (guest == null) {
                continue;
            }
            var member = findByCustomer(guest.customerId()).orElse(null);
            if (member == null) {
                continue;
            }
            var id = Accrual.id(stay.stayId(), member.memberNumber);
            if (accruals.existsById(id)) {
                continue;
            }
            var a = new Accrual();
            a.id = id;
            a.memberNumber = member.memberNumber;
            a.stayId = stay.stayId();
            a.hotelCode = stay.hotelCode();
            a.nights = stay.nights();
            a.points = (long) stay.nights() * (guest.holder() ? HOLDER_POINTS_PER_NIGHT : COMPANION_POINTS_PER_NIGHT);
            a.at = stay.at() != null ? stay.at() : clock.instant();
            accruals.save(a);
            member.points += a.points;
            // Up, never down: an explicit grant above what the points are worth stays.
            member.tier = Tier.max(member.tier(), Tier.byPoints(member.points)).name();
            member.updatedAt = clock.instant();
            members.save(member);
            made.add(a);
            log.info("Stay {} earned member {} {} points ({} nights, {})", stay.stayId(), member.memberNumber,
                    a.points, a.nights, guest.holder() ? "holder" : "companion");
        }
        return made;
    }

    /**
     * Two customers turned out to be one: the absorbed one's memberships now belong to the survivor.
     *
     * @return how many memberships moved
     */
    @Transactional
    public int merge(CustomersMerged merged) {
        var absorbed = trimmed(merged.absorbedId());
        var survivor = trimmed(merged.customerId());
        if (absorbed == null || survivor == null || absorbed.equals(survivor)) {
            return 0;
        }
        if (merged.eventId() != null && !inbox.firstTime(CUSTOMERS, merged.eventId())) {
            return 0;
        }
        var moved = members.findByCustomerCode(absorbed);
        for (var m : moved) {
            m.customerCode = survivor;
            m.updatedAt = clock.instant();
            members.save(m);
            log.info("Member {} moved from customer {} to {} (merge)", m.memberNumber, absorbed, survivor);
        }
        return moved.size();
    }

    static String trimmed(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
