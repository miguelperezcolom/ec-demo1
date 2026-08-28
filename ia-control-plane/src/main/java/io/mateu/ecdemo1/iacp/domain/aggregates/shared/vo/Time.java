package io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo;

import java.time.LocalDateTime;

/** A moment. Wrapped so the aggregates never carry a bare LocalDateTime. */
public record Time(LocalDateTime value) {
    @Override public String toString() { return String.valueOf(value); }
}
