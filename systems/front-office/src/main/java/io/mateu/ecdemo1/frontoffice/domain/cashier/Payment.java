package io.mateu.ecdemo1.frontoffice.domain.cashier;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A payment on a stay's account, taken at the desk: a payment of what is owed, or an advance (anticipo)
 * on what will be. A pay link is PENDING until the guest pays it; the card terminal may decline.
 */
public record Payment(String id, String stayId, Kind kind, Method method, BigDecimal amount, String currency,
                      Status status, String reference, String linkToken, String email, Integer receiptNo,
                      Instant createdAt, String createdBy, Instant capturedAt) {

  public enum Kind {
    PAYMENT("Cobro"), DEPOSIT("Anticipo");

    public final String label;

    Kind(String label) {
      this.label = label;
    }
  }

  public enum Method {
    CASH("Efectivo"), CARD_PINPAD("Tarjeta (datáfono)"), PAY_LINK("Link de pago por email"), TRANSFER("Transferencia"),
    MANUAL("Manual");

    public final String label;

    Method(String label) {
      this.label = label;
    }
  }

  public enum Status {
    PENDING("Pendiente"), CAPTURED("Cobrado"), DECLINED("Denegado"), CANCELLED("Anulado");

    public final String label;

    Status(String label) {
      this.label = label;
    }
  }

  public boolean captured() {
    return status == Status.CAPTURED;
  }

  public Payment withStatus(Status status, String reference, Integer receiptNo, Instant capturedAt) {
    return new Payment(id, stayId, kind, method, amount, currency, status, reference == null ? this.reference : reference,
        linkToken, email, receiptNo == null ? this.receiptNo : receiptNo, createdAt, createdBy,
        capturedAt == null ? this.capturedAt : capturedAt);
  }
}
