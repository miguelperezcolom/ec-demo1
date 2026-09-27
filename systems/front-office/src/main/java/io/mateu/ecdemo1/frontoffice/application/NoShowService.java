package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInChecklist;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.infra.crs.NoShows;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The desk marks a pax as not arrived — or takes the mark back. When nobody of an arriving
 * reservation came, it is a no-show of the reservation, and that is the CRS's to decide (HLA F006): the
 * report goes into the outbox in the mark's own transaction, and reaches the CRS by Kafka.
 */
@Service
public class NoShowService {

  /**
   * @param noShow        whether the pax is now marked as a no-show
   * @param nobodyArrived whether that made the whole reservation a no-show
   * @param crsNotice     what the CRS answered, when it was told; null otherwise
   */
  public record Outcome(boolean noShow, boolean nobodyArrived, String crsNotice) {}

  final StayRepository stays;
  final CheckInOpsRepository checkInOps;
  final NoShows crs;
  final TransactionTemplate transaction;

  public NoShowService(StayRepository stays, CheckInOpsRepository checkInOps, NoShows crs,
                       PlatformTransactionManager transactions) {
    this.stays = stays;
    this.checkInOps = checkInOps;
    this.crs = crs;
    this.transaction = new TransactionTemplate(transactions);
  }

  public Outcome paxToggled(String stayId, int pax) {
    return transaction.execute(status -> {
      var stay = stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
      var ops = checkInOps.save(stayId, checkInOps.of(stayId).toggleNoShow(pax));
      var nobodyArrived = stay.status() == StayStatus.ARRIVING && CheckInChecklist.nobodyArrived(stay, ops);
      // The report leaves with the mark: both saved, or neither.
      return new Outcome(ops.isNoShow(pax), nobodyArrived, nobodyArrived ? crs.report(stayId) : null);
    });
  }
}
