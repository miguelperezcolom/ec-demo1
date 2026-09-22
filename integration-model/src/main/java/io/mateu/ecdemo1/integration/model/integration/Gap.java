package io.mateu.ecdemo1.integration.model.integration;

/**
 * Something the backfill needs and does not have: a code with no approved equivalent, or a partner
 * that is not a PMS profile yet — with how many of the reservations to project it would block.
 *
 * @param kind {@code MAPPING} or {@code PARTNER}
 */
public record Gap(String kind, String type, String code, int reservations) {
}
