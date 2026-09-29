package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInChecklist;
import io.mateu.ecdemo1.frontoffice.domain.stay.CheckInOpsRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.infra.pms.ReceptionReports;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The desk marks a pax as not arrived — or takes the mark back. When nobody of an arriving
 * reservation came, it is a no-show of the reservation (HLA F006). It goes up the chain: to the PMS,
 * the master of the stay, which records it and reports it to the CRS, the master of the sale, whose
 * fee rule decides what it costs — an event in the outbox, in the mark's own transaction.
 */
@Service
public class NoShowService {

  /**
   * @param noShow        whether the pax is now marked as a no-show
   * @param nobodyArrived whether that made the whole reservation a no-show
   * @param crsNotice     what the desk is told of where the no-show went, when it went; null otherwise
   */
  public record Outcome(boolean noShow, boolean nobodyArrived, String crsNotice) {}

  final StayRepository stays;
  final CheckInOpsRepository checkInOps;
  final ReceptionReports reception;
  final TransactionTemplate transaction;

  public NoShowService(StayRepository stays, CheckInOpsRepository checkInOps, ReceptionReports reception,
                       PlatformTransactionManager transactions) {
    this.stays = stays;
    this.checkInOps = checkInOps;
    this.reception = reception;
    this.transaction = new TransactionTemplate(transactions);
  }

  public Outcome paxToggled(String stayId, int pax) {
    return transaction.execute(status -> {
      var stay = stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("No stay " + stayId));
      var ops = checkInOps.save(stayId, checkInOps.of(stayId).toggleNoShow(pax));
      var nobodyArrived = stay.status() == StayStatus.ARRIVING && CheckInChecklist.nobodyArrived(stay, ops);
      // The report leaves with the mark: both saved, or neither.
      return new Outcome(ops.isNoShow(pax), nobodyArrived,
          nobodyArrived ? reception.noShow(stayId, stay.pax(), "front office") : null);
    });
  }
}
