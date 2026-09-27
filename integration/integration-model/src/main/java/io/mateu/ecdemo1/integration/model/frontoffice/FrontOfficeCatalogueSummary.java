package io.mateu.ecdemo1.integration.model.frontoffice;

import java.time.Instant;
import java.util.Map;

/**
 * What a front office holds of the PMS's catalogue, as it tells whoever asks ({@code GET
 * /api/pms-catalogue/summary}): the pms-fo integration's catalogue gate opens on it.
 *
 * @param pmsHotelCode the property the catalogue is of; null if it has none
 * @param commandId    the {@link FrontOfficeCommand.ReplaceCatalogue} it came with
 * @param counts       entries by {@link FrontOfficeCommand.CatalogueType} name
 */
public record FrontOfficeCatalogueSummary(String pmsHotelCode, String commandId, Instant syncedAt,
                                          Map<String, Integer> counts) {
}
