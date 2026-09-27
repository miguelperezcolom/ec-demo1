package io.mateu.ecdemo1.frontoffice.domain.automation;

import java.util.List;

/**
 * Automation aggregate root — a monitored back-office process (credit invoicing, prepayments,
 * digital check-in…) with the systems it integrates and its execution counters. The status is
 * always derived from the counters.
 */
public record Automation(
    String id,
    String name,
    int okCount,
    int warningCount,
    int errorCount,
    List<ConnectedSystem> systems) {

  public Automation {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Automation id is required");
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("Automation name is required");
    systems = systems == null ? List.of() : List.copyOf(systems);
  }

  public AutomationStatus status() {
    if (errorCount > 0) return AutomationStatus.ERROR;
    if (warningCount > 0) return AutomationStatus.WARNING;
    return AutomationStatus.OK;
  }

  /** Marks every warning as fixed, moving them to the OK counter. */
  public Automation resolveWarnings() {
    return new Automation(id, name, okCount + warningCount, 0, errorCount, systems);
  }
}
