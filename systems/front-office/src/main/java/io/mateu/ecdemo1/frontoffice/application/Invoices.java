package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.folio.FolioLine;
import io.mateu.ecdemo1.frontoffice.infra.pms.StayInvoices;
import io.mateu.ecdemo1.frontoffice.infra.pms.StayInvoices.StayInvoice;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The invoice of a closed stay, for the desk to open («Abrir factura»). The PMS is the master of the
 * folio: when Opera gave its document at the check-out, that is the invoice, as Opera issued it. When
 * it did not — the tenant stores no printable folio, or the check-out has not reached Opera yet — the
 * desk gets the front office's own folio as a PDF, labelled «Factura proforma (front office)» on every
 * page: it is not the PMS's invoice, and says so (with the PMS's number and total, when Opera gave
 * those).
 *
 * <p>The document is served by the front office, never by a link to Opera; the link carries a
 * signature that expires ({@link #link}), since the page that shows it is behind the desk's login and
 * the document is not.
 */
@Service
public class Invoices {

  /** A document to send: its bytes, what it is, and a file name. */
  public record Document(byte[] pdf, boolean fromThePms, String fileName) {}

  /** What the desk is shown of a closed stay's invoice. */
  public record Summary(boolean fromThePms, String label) {}

  static final Duration LINK_LIFETIME = Duration.ofHours(8);
  static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  final StayQueries queries;
  final StayInvoices invoices;
  final String hotel;
  final byte[] key;
  final Clock clock = Clock.systemUTC();

  public Invoices(StayQueries queries, StayInvoices invoices, @Value("${frontoffice.hotel:MRU01}") String hotel,
      @Value("${frontoffice.link-secret:}") String secret) {
    this.queries = queries;
    this.invoices = invoices;
    this.hotel = hotel;
    if (secret == null || secret.isBlank()) {
      this.key = new byte[32];
      new SecureRandom().nextBytes(this.key);
    } else {
      this.key = secret.getBytes(StandardCharsets.UTF_8);
    }
  }

  public Summary summary(String stayId) {
    var invoice = invoices.of(stayId);
    if (invoice.isPresent() && invoice.get().hasDocument()) {
      return new Summary(true, "Abrir factura" + (invoice.get().number() == null ? "" : " · Opera " + invoice.get().number()));
    }
    return new Summary(false, "Abrir factura proforma (front office)");
  }

  /** Where the desk opens the invoice: the front office's own, signed for a few hours. */
  public String link(String stayId) {
    var expires = clock.instant().plus(LINK_LIFETIME).getEpochSecond();
    return "/invoices/" + stayId + "?e=" + expires + "&s=" + sign(stayId, expires);
  }

  public boolean valid(String stayId, long expires, String signature) {
    return signature != null && expires >= clock.instant().getEpochSecond()
        && MessageDigest.isEqual(sign(stayId, expires).getBytes(StandardCharsets.US_ASCII),
            signature.getBytes(StandardCharsets.US_ASCII));
  }

  String sign(String stayId, long expires) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding()
          .encodeToString(mac.doFinal((stayId + "|" + expires).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The stay's invoice: the PMS's document, or the front office's proforma. Empty for a stay it does not have. */
  public Optional<Document> document(String stayId) {
    var found = queries.find(stayId);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    var invoice = invoices.of(stayId);
    if (invoice.isPresent() && invoice.get().hasDocument()) {
      return Optional.of(new Document(invoice.get().pdf(), true,
          "factura-" + (invoice.get().number() == null ? stayId : invoice.get().number()) + ".pdf"));
    }
    return Optional.of(new Document(proforma(stayId, invoice.orElse(null)), false, "proforma-" + stayId + ".pdf"));
  }

  /** The front office's folio as a PDF, labelled as the proforma it is. */
  byte[] proforma(String stayId, StayInvoice pms) {
    var view = queries.view(stayId);
    var stay = view.stay();
    var lines = new ArrayList<String[]>();
    var total = BigDecimal.ZERO;
    for (var line : view.folio() == null ? List.<FolioLine>of() : view.folio().lines()) {
      lines.add(new String[]{line.concept(), line.included() ? (line.includedLabel() == null ? "incluido" : line.includedLabel())
          : euros(line.amount())});
      if (line.amount() != null && !line.included()) {
        total = total.add(line.amount());
      }
    }
    var text = new ArrayList<String>();
    text.add("Hotel " + hotel + " · estancia " + stayId);
    text.add("Huésped: " + (view.guest() == null ? "" : view.guest().name()));
    text.add("Estancia: " + DAY.format(stay.checkIn()) + " – " + DAY.format(stay.checkOut())
        + (stay.roomNumber() == null ? "" : " · habitación " + stay.roomNumber()));
    if (pms != null && pms.number() != null) {
      text.add("Factura de Opera: " + pms.number() + (pms.amount() == null ? "" : " por " + pms.amount().toPlainString()
          + " " + (pms.currency() == null ? "" : pms.currency())) + " — Opera no ha dado su documento.");
    }
    try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
      var page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      var regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      try (var content = new PDPageContentStream(document, page)) {
        float y = 790;
        y = write(content, bold, 16, 50, y, "FACTURA PROFORMA (front office)");
        y = write(content, regular, 9, 50, y - 4,
            "No es la factura del PMS: Opera, el maestro del folio, no ha dado su documento. Folio del front office.");
        y -= 12;
        for (var t : text) {
          y = write(content, regular, 11, 50, y, t);
        }
        y -= 12;
        y = write(content, bold, 11, 50, y, "Concepto");
        write(content, bold, 11, 430, y + 15, "Importe");
        for (var line : lines) {
          y = write(content, regular, 11, 50, y, line[0]);
          write(content, regular, 11, 430, y + 15, line[1]);
          if (y < 80) {
            break;
          }
        }
        y -= 8;
        y = write(content, bold, 12, 50, y, "Total");
        write(content, bold, 12, 430, y + 16, euros(total));
        write(content, regular, 8, 50, 40, "Factura proforma (front office) · generada " + DateTimeFormatter.ISO_INSTANT
            .format(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
      }
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static float write(PDPageContentStream content, PDType1Font font, float size, float x, float y, String text)
      throws IOException {
    content.beginText();
    content.setFont(font, size);
    content.newLineAtOffset(x, y);
    content.showText(printable(text));
    content.endText();
    return y - size - 4;
  }

  /** What the standard fonts can draw (WinAnsi): anything else becomes «?». */
  static String printable(String text) {
    if (text == null) {
      return "";
    }
    var out = new StringBuilder();
    for (var c : text.toCharArray()) {
      out.append(c == '–' || c == '—' || c == '·' || c == '€' || c < 0x100 && c >= 0x20 ? c : '?');
    }
    return out.toString();
  }

  static String euros(BigDecimal amount) {
    return amount == null ? "" : String.format(Locale.forLanguageTag("es-ES"), "%,.2f €", amount);
  }
}
