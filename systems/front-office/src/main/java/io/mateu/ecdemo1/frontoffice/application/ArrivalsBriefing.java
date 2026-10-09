package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.customer.ArrivalBriefings;
import io.mateu.ecdemo1.frontoffice.domain.customer.ArrivalBriefings.Briefing;
import io.mateu.ecdemo1.frontoffice.domain.customer.LoyaltyStatus;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory;
import io.mateu.ecdemo1.frontoffice.domain.customer.StayHistory.HistorySummary;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * The summary of the day's arrivals, before they arrive: for every pax of a stay arriving today or
 * tomorrow (or overdue) whom the reservation names by a chain code ({@code C-…}) — or by an Opera profile
 * the MDM knows as one —, their stays in the
 * chain and their Riu Class standing — kept ({@link ArrivalBriefings}), so the arrivals list shows who
 * is coming back and the check-in has it without asking then.
 *
 * <p>Only who has stayed with us: a provisional code with no stays is nobody the desk knows yet. Prepared
 * every few minutes, and on demand from the arrivals list; a service that does not answer leaves the
 * pax's last briefing as it was. The stays no longer arriving lose theirs.
 */
@Service
public class ArrivalsBriefing {

  static final Logger log = LoggerFactory.getLogger(ArrivalsBriefing.class);

  /** How many days ahead: today's and tomorrow's arrivals. */
  static final int DAYS_AHEAD = 1;

  final StayRepository stays;
  final GuestRepository guests;
  final StayHistory history;
  final LoyaltyStatus loyalty;
  final ArrivalBriefings briefings;
  final io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory directory;
  final boolean enabled;
  Clock clock = Clock.systemDefaultZone();

  public ArrivalsBriefing(StayRepository stays, GuestRepository guests, StayHistory history, LoyaltyStatus loyalty,
                          ArrivalBriefings briefings,
                          io.mateu.ecdemo1.frontoffice.domain.customer.CustomerDirectory directory,
                          @Value("${frontoffice.arrivals-briefing.enabled:true}") boolean enabled) {
    this.stays = stays;
    this.guests = guests;
    this.history = history;
    this.loyalty = loyalty;
    this.briefings = briefings;
    this.directory = directory;
    this.enabled = enabled;
  }

  /** What one preparation did. */
  public record Prepared(int arrivals, int known, int removed) {}

  @Scheduled(initialDelayString = "${frontoffice.arrivals-briefing.initial-delay:PT30S}",
      fixedDelayString = "${frontoffice.arrivals-briefing.interval:PT10M}")
  public void scheduled() {
    if (!enabled) {
      return;
    }
    try {
      var p = prepare();
      if (p.known() > 0 || p.removed() > 0) {
        log.info("Arrivals briefed: {} arriving, {} pax known, {} stays no longer arriving", p.arrivals(), p.known(),
            p.removed());
      }
    } catch (RuntimeException e) {
      log.warn("Arrivals not briefed now: {}", e.getMessage());
    }
  }

  /** Briefs every arrival due by tomorrow. */
  public Prepared prepare() {
    var until = LocalDate.now(clock).plusDays(DAYS_AHEAD);
    var arriving = stays.findArrivals().stream().filter(s -> !s.checkIn().isAfter(until)).toList();
    var known = 0;
    for (var stay : arriving) {
      known += brief(stay);
    }
    var removed = briefings.keepOnly(arriving.stream().map(Stay::id).toList());
    return new Prepared(arriving.size(), known, removed);
  }

  /** Briefs each pax of the stay; how many the chain knows. */
  int brief(Stay stay) {
    var known = 0;
    for (var pax = 1; pax <= Math.max(1, stay.pax()); pax++) {
      var code = customerOf(Recognition.paxId(stay, pax));
      if (!Recognition.chain(code)) {
        briefings.clear(stay.id(), pax);
        continue;
      }
      var summary = history.summary(code);
      if (summary.isEmpty()) {
        // customer-history did not answer: what was prepared before stays
        known += briefings.of(stay.id(), pax).isPresent() ? 1 : 0;
        continue;
      }
      if (!summary.get().any()) {
        briefings.clear(stay.id(), pax);
        continue;
      }
      briefings.save(new Briefing(stay.id(), pax, code, name(stay, pax), summary.get(),
          loyalty.of(code, null).orElse(null), clock.instant()));
      known++;
    }
    return known;
  }

  /**
   * The pax's chain code: the one the reservation gives, or — a reservation that came from Opera names the
   * guest by their Opera profile ({@code opera-…}) — the customer the MDM knows that profile as.
   */
  String customerOf(String paxId) {
    if (paxId != null && paxId.startsWith("opera-")) {
      return directory.byOperaProfile(paxId.substring("opera-".length())).orElse(null);
    }
    return paxId;
  }

  String name(Stay stay, int pax) {
    if (pax <= 1) {
      return guests.findById(stay.guestId()).map(g -> g.name()).orElse(null);
    }
    var companion = stay.companionAt(pax);
    return companion == null ? null : companion.name();
  }

  /** The returning customers among the arrivals: the holder's briefing of each stay that has one. */
  public List<Briefing> holders() {
    var result = new ArrayList<Briefing>();
    for (var b : briefings.all()) {
      if (b.pax() == 1) {
        result.add(b);
      }
    }
    return result;
  }

  /** «5 estancias · GOLD» — what the arrivals list says of a returning holder. */
  public static String line(Briefing b) {
    HistorySummary h = b.history();
    var text = h.stays() == 1 ? "1 estancia" : h.stays() + " estancias";
    return b.loyalty() == null ? "Repite · " + text : "Repite · " + text + " · " + b.loyalty().tier();
  }
}
