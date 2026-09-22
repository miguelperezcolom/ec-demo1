package io.mateu.ecdemo1.integration.model.reservation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NightlyRate(LocalDate date, BigDecimal amount) {
}
