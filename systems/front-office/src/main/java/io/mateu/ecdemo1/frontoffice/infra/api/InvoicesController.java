package io.mateu.ecdemo1.frontoffice.infra.api;

import io.mateu.ecdemo1.frontoffice.application.Invoices;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * «Abrir factura»: a closed stay's invoice, as a PDF in a tab of its own — the PMS's document when
 * Opera gave it, else the front office's proforma ({@link Invoices}). A new tab carries no token, so
 * the link the desk's page gives is signed and expires: without a valid signature, 403.
 */
@RestController
public class InvoicesController {

  final Invoices invoices;

  public InvoicesController(Invoices invoices) {
    this.invoices = invoices;
  }

  @GetMapping("/invoices/{stayId}")
  public ResponseEntity<byte[]> invoice(@PathVariable String stayId, @RequestParam(name = "e", defaultValue = "0") long expires,
                                        @RequestParam(name = "s", required = false) String signature) {
    if (!invoices.valid(stayId, expires, signature)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return invoices.document(stayId)
        .map(d -> ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(d.fileName()).build().toString())
            .header("X-Invoice-Source", d.fromThePms() ? "PMS" : "FRONT-OFFICE-PROFORMA")
            .body(d.pdf()))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
