package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.cashier.Payment;
import io.mateu.ecdemo1.frontoffice.domain.folio.FolioLine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * What the desk prints from the account: the <b>receipt</b> of a payment or an advance, and the folio's
 * <b>proforma</b> at any moment of the stay — charges, payments and what is still owed. PDFs of the
 * front office's own, opened from signed links (as the invoice is: {@link Invoices#valid}).
 */
@Service
// a service, never part of a page's state (Mateu serialises the pages, and the services some of them hold)
@com.fasterxml.jackson.annotation.JsonIgnoreType
public class Receipts {

  static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Madrid"));

  final Cashier cashier;
  final StayQueries queries;
  final Invoices invoices;
  final String hotel;

  public Receipts(Cashier cashier, StayQueries queries, Invoices invoices, @Value("${frontoffice.hotel:MRU01}") String hotel) {
    this.cashier = cashier;
    this.queries = queries;
    this.invoices = invoices;
    this.hotel = hotel;
  }

  public record Pdf(byte[] bytes, String fileName) {}

  // ── signed links ─────────────────────────────────────────────────────────────

  public String receiptLink(String paymentId) {
    return "/caja/recibo/" + paymentId + signed("recibo:" + paymentId);
  }

  public String proformaLink(String stayId) {
    return "/caja/proforma/" + stayId + signed("proforma:" + stayId);
  }

  String signed(String what) {
    var expires = invoices.clock.instant().plus(Invoices.LINK_LIFETIME).getEpochSecond();
    return "?e=" + expires + "&s=" + invoices.sign(what, expires);
  }

  public boolean validReceipt(String paymentId, long expires, String signature) {
    return invoices.valid("recibo:" + paymentId, expires, signature);
  }

  public boolean validProforma(String stayId, long expires, String signature) {
    return invoices.valid("proforma:" + stayId, expires, signature);
  }

  // ── the documents ────────────────────────────────────────────────────────────

  /** A captured payment's receipt; empty for one not captured. */
  public Optional<Pdf> receipt(String paymentId) {
    var payment = cashier.payment(paymentId).filter(p -> p.captured() || p.status() == Payment.Status.CANCELLED)
        .filter(p -> p.receiptNo() != null).orElse(null);
    if (payment == null) {
      return Optional.empty();
    }
    var view = queries.view(payment.stayId());
    var text = new ArrayList<String>();
    text.add("Hotel " + hotel + " · estancia " + payment.stayId()
        + (view.stay().roomNumber() == null ? "" : " · habitación " + view.stay().roomNumber()));
    text.add("Recibido de: " + (view.guest() == null ? "" : view.guest().name()));
    text.add("Concepto: " + (payment.kind() == Payment.Kind.DEPOSIT ? "anticipo a cuenta de la estancia" : "pago a cuenta del folio"));
    text.add("Forma de pago: " + payment.method().label + (payment.reference() == null ? "" : " · " + payment.reference()));
    text.add("Fecha: " + WHEN.format(payment.capturedAt() == null ? payment.createdAt() : payment.capturedAt())
        + (payment.createdBy() == null ? "" : " · cobrado por " + payment.createdBy()));
    if (payment.status() == Payment.Status.CANCELLED) {
      text.add("ANULADO / DEVUELTO");
    }
    var account = cashier.account(payment.stayId());
    return Optional.of(new Pdf(pdf("RECIBO Nº " + payment.receiptNo(), text,
        List.<String[]>of(new String[] {payment.kind().label, amount(payment.amount(), payment.currency())}),
        List.<String[]>of(new String[] {"Saldo pendiente tras el cobro", amount(account.due(), account.currency())})),
        "recibo-" + payment.receiptNo() + ".pdf"));
  }

  /** The folio's proforma now: charges, payments and the balance still due. */
  public Optional<Pdf> proforma(String stayId) {
    if (queries.find(stayId).isEmpty()) {
      return Optional.empty();
    }
    var view = queries.view(stayId);
    var account = cashier.account(stayId);
    var lines = new ArrayList<String[]>();
    for (var line : view.folio() == null ? List.<FolioLine>of() : view.folio().lines()) {
      lines.add(new String[] {line.concept(), line.included() ? "incluido" : line.voided() ? "anulado"
          : amount(line.amount(), account.currency())});
    }
    for (var p : account.payments()) {
      if (p.captured()) {
        lines.add(new String[] {p.kind().label + " · " + p.method().label + " · recibo nº " + p.receiptNo(),
            amount(p.amount().negate(), p.currency())});
      }
    }
    var text = List.of("Hotel " + hotel + " · estancia " + stayId,
        "Huésped: " + (view.guest() == null ? "" : view.guest().name()),
        "Estancia: " + Invoices.DAY.format(view.stay().checkIn()) + " – " + Invoices.DAY.format(view.stay().checkOut())
            + (view.stay().roomNumber() == null ? "" : " · habitación " + view.stay().roomNumber()),
        account.creditCancelled() ? "Crédito cancelado" : "Límite de crédito: " + amount(account.creditLimit(), account.currency()));
    return Optional.of(new Pdf(pdf("FACTURA PROFORMA — folio en curso", text, lines, List.<String[]>of(
        new String[] {"Cargos", amount(account.charges(), account.currency())},
        new String[] {"Cobrado", amount(account.paid(), account.currency())},
        new String[] {"Saldo pendiente", amount(account.due(), account.currency())})),
        "proforma-" + stayId + ".pdf"));
  }

  byte[] pdf(String title, List<String> text, List<String[]> lines, List<String[]> totals) {
    try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      var regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      try (var content = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
        float y = 790;
        y = Invoices.write(content, bold, 16, 50, y, title);
        y -= 10;
        for (var t : text) {
          y = Invoices.write(content, regular, 11, 50, y, t);
        }
        y -= 12;
        y = Invoices.write(content, bold, 11, 50, y, "Concepto");
        Invoices.write(content, bold, 11, 430, y + 15, "Importe");
        for (var line : lines) {
          y = Invoices.write(content, regular, 11, 50, y, line[0]);
          Invoices.write(content, regular, 11, 430, y + 15, line[1]);
          if (y < 120) {
            break;
          }
        }
        y -= 8;
        for (var t : totals) {
          y = Invoices.write(content, bold, 12, 50, y, t[0]);
          Invoices.write(content, bold, 12, 430, y + 16, t[1]);
        }
        Invoices.write(content, regular, 8, 50, 40, "Front office · " + hotel + " · documento interno, no es la factura del PMS");
      }
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String amount(BigDecimal amount, String currency) {
    return amount == null ? "" : String.format(Locale.forLanguageTag("es-ES"), "%,.2f %s", amount,
        "EUR".equals(currency) ? "€" : currency);
  }
}
