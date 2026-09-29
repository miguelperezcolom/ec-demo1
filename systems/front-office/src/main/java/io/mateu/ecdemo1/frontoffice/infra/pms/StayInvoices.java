package io.mateu.ecdemo1.frontoffice.infra.pms;

import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The invoice the PMS issued at each stay's check-out — the PMS is the master of the folio: its number
 * and figures, and its document when the PMS gave one. Written by state: the same invoice twice is one.
 */
@Repository
public class StayInvoices {

  /** An invoice of a stay, as the PMS issued it. {@code pdf} is null when the PMS gave only its figures. */
  public record StayInvoice(String stayId, String source, String number, LocalDate date, BigDecimal amount,
                            String currency, byte[] pdf, Instant receivedAt) {

    public boolean hasDocument() {
      return pdf != null && pdf.length > 0;
    }
  }

  final JdbcTemplate jdbc;

  public StayInvoices(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(String stayId, FrontOfficeCommand.Invoice invoice, Instant at) {
    var pdf = invoice.pdf() == null || invoice.pdf().isBlank() ? null : Base64.getDecoder().decode(invoice.pdf());
    var updated = jdbc.update("update stay_invoice set source = ?, number = ?, invoice_date = ?, amount = ?, currency = ?, "
            + "pdf = ?, received_at = ? where stay_id = ?", invoice.source(), invoice.number(), invoice.date(),
        invoice.amount(), invoice.currency(), pdf, Timestamp.from(at), stayId);
    if (updated == 0) {
      jdbc.update("insert into stay_invoice (stay_id, source, number, invoice_date, amount, currency, pdf, received_at) "
              + "values (?, ?, ?, ?, ?, ?, ?, ?)", stayId, invoice.source(), invoice.number(), invoice.date(),
          invoice.amount(), invoice.currency(), pdf, Timestamp.from(at));
    }
  }

  public Optional<StayInvoice> of(String stayId) {
    return jdbc.query("select stay_id, source, number, invoice_date, amount, currency, pdf, received_at from stay_invoice "
            + "where stay_id = ?", (rs, n) -> new StayInvoice(rs.getString(1), rs.getString(2), rs.getString(3),
            rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(), rs.getBigDecimal(5), rs.getString(6),
            rs.getBytes(7), rs.getTimestamp(8).toInstant()), stayId)
        .stream().findFirst();
  }
}
