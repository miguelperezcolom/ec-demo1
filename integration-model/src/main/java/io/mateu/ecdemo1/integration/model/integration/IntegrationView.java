package io.mateu.ecdemo1.integration.model.integration;

/**
 * A hotel's integration as the other services need to see it: which Opera property the CRS hotel
 * is, and whether its reservations may flow.
 */
public record IntegrationView(String id, String crsHotelCode, String pmsHotelCode, IntegrationStatus status) {
}
