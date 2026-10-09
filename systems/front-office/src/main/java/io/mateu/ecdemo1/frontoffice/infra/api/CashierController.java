package io.mateu.ecdemo1.frontoffice.infra.api;

import io.mateu.ecdemo1.frontoffice.application.Cashier;
import io.mateu.ecdemo1.frontoffice.application.Receipts;
import io.mateu.ecdemo1.frontoffice.domain.cashier.Payment;
import io.mateu.ecdemo1.frontoffice.ui.common.OtherSystems;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The account's documents in a tab of their own — a payment's receipt, the folio's proforma — from
 * signed links that expire (403 without a valid signature), and the guest's page of a payment link
 * ({@code /pagar/<token>}): what it is for, and «Pagar». The PoC has no payment provider: paying is the
 * button; the token is the secret.
 */
@RestController
public class CashierController {

  final Cashier cashier;
  final Receipts receipts;

  public CashierController(Cashier cashier, Receipts receipts) {
    this.cashier = cashier;
    this.receipts = receipts;
  }

  @GetMapping("/caja/recibo/{paymentId}")
  public ResponseEntity<byte[]> receipt(@PathVariable String paymentId, @RequestParam(name = "e", defaultValue = "0") long e,
                                        @RequestParam(name = "s", required = false) String s) {
    if (!receipts.validReceipt(paymentId, e, s)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return receipts.receipt(paymentId).map(CashierController::pdf).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/caja/proforma/{stayId}")
  public ResponseEntity<byte[]> proforma(@PathVariable String stayId, @RequestParam(name = "e", defaultValue = "0") long e,
                                         @RequestParam(name = "s", required = false) String s) {
    if (!receipts.validProforma(stayId, e, s)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return receipts.proforma(stayId).map(CashierController::pdf).orElseGet(() -> ResponseEntity.notFound().build());
  }

  static ResponseEntity<byte[]> pdf(Receipts.Pdf pdf) {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.fileName()).build().toString())
        .body(pdf.bytes());
  }

  @GetMapping(value = "/pagar/{token}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> payPage(@PathVariable String token) {
    return cashier.byLinkToken(token).map(p -> ResponseEntity.ok(page(p, token, null)))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(page(null, token, "Este enlace de pago no existe.")));
  }

  @PostMapping(value = "/pagar/{token}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> pay(@PathVariable String token) {
    return cashier.payLink(token).map(p -> ResponseEntity.ok(page(p, token,
            p.captured() ? "Pago recibido. Gracias — el hotel ya lo ve en su cuenta." : null)))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(page(null, token, "Este enlace de pago no existe.")));
  }

  static String page(Payment p, String token, String said) {
    var body = new StringBuilder();
    if (p != null) {
      body.append("<h1>").append(Receipts.amount(p.amount(), p.currency())).append("</h1>")
          .append("<p>").append(p.kind() == Payment.Kind.DEPOSIT ? "Anticipo" : "Pago").append(" de su estancia ")
          .append(OtherSystems.escape(p.stayId())).append("</p>");
      switch (p.status()) {
        case PENDING -> body.append("<form method=\"post\" action=\"/pagar/").append(OtherSystems.escape(token))
            .append("\"><p class=\"demo\">Demo: no hay pasarela de pago — el botón paga.</p>")
            .append("<button type=\"submit\">Pagar</button></form>");
        case CAPTURED -> body.append("<p class=\"ok\">Pagado · ").append(OtherSystems.escape(p.reference())).append("</p>");
        default -> body.append("<p>Este enlace ya no admite pagos (").append(p.status().label.toLowerCase()).append(").</p>");
      }
    }
    if (said != null) {
      body.append("<p class=\"said\">").append(OtherSystems.escape(said)).append("</p>");
    }
    return """
        <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Pago de su estancia</title><style>
        body{font-family:system-ui,sans-serif;max-width:28rem;margin:3rem auto;padding:0 1rem;color:#1d2a33}
        h1{font-size:2.2rem;margin:.2rem 0} button{font-size:1.1rem;padding:.7rem 2rem;border:0;border-radius:.4rem;background:#0b5f7a;color:#fff;cursor:pointer}
        .demo{font-size:.8rem;opacity:.7} .ok,.said{color:#1d7a3a;font-weight:600}
        </style></head><body><p>RIU Hotels &amp; Resorts</p>%s</body></html>""".formatted(body);
  }
}
