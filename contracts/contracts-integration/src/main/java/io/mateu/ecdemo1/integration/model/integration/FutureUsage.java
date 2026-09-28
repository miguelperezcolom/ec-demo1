package io.mateu.ecdemo1.integration.model.integration;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;

import java.util.List;

/**
 * What a hotel's future reservations really use — the backfill's pre-pass (HLA F010): not every code
 * the CRS can emit, only the ones these reservations carry, each with how many reservations need it.
 */
public record FutureUsage(String hotelCode, int reservations, List<CodeUsage> codes, List<PartnerUsage> partners) {

    public record CodeUsage(CodeType type, String code, int reservations) {
    }

    public record PartnerUsage(String partnerCode, int reservations) {
    }
}
