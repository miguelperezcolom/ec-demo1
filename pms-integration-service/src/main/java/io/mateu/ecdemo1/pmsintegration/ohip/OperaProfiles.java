package io.mateu.ecdemo1.pmsintegration.ohip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Profiles in Opera (crm). Looked up before they are created — by the id this adapter gave them, as
 * an external reference — so a retry after a lost answer updates instead of creating a duplicate.
 *
 * <p>A profile belongs to the chain, not a hotel, but OHIP still wants a hotel header on the call:
 * the first property this client may see is used.
 */
@Component
@RequiredArgsConstructor
public class OperaProfiles {

    public record Ensured(String profileId, boolean created) {
    }

    final OhipClient ohip;
    final OhipProperties properties;
    final ObjectMapper objectMapper;

    /**
     * The reservation's holder, as the provisional guest profile Opera needs to take a reservation
     * (F001). One per reservation: whether the same person already has a profile is resolved later,
     * at check-in, against the CRM — not here (R11).
     */
    public Ensured ensureGuest(String hotelId, String locator, Person holder) {
        var profile = objectMapper.createObjectNode();
        var details = profile.putObject("profileDetails");
        details.put("profileType", "Guest");
        var name = details.putObject("customer").putArray("personName").addObject();
        name.put("givenName", holder.firstName()).put("surname", holder.lastName()).put("nameType", "Primary");
        if (holder.nationality() != null) {
            details.with("customer").put("nationality", holder.nationality());
        }
        if (holder.email() != null) {
            details.putObject("emails").putArray("emailInfo").addObject().putObject("email")
                    .put("emailAddress", holder.email()).put("primaryInd", true);
        }
        if (holder.phone() != null) {
            details.putObject("telephones").putArray("telephoneInfo").addObject().putObject("telephone")
                    .put("phoneNumber", holder.phone()).put("primaryInd", true);
        }
        return ensure(hotelId, "HOLDER-" + locator, profile);
    }

    /** A partner as the profile Opera routes and bills through: Agent, Company or Source (F004). */
    public Ensured ensurePartner(String hotelId, Partner partner, String profileType) {
        var profile = objectMapper.createObjectNode();
        var details = profile.putObject("profileDetails");
        details.put("profileType", profileType);
        details.putObject("company").put("companyName", partner.name());
        if (partner.taxId() != null) {
            details.putObject("taxInfo").put("tax1No", partner.taxId());
        }
        if (partner.address() != null) {
            var address = details.putObject("addresses").putArray("addressInfo").addObject().putObject("address");
            address.putArray("addressLine").add(partner.address().line());
            address.put("cityName", partner.address().city()).put("postalCode", partner.address().postalCode());
            address.putObject("country").put("code", partner.address().countryCode());
        }
        if (partner.email() != null) {
            details.putObject("emails").putArray("emailInfo").addObject().putObject("email")
                    .put("emailAddress", partner.email()).put("primaryInd", true);
        }
        details.putObject("userDefinedFields").putArray("numericUDFs").addObject()
                .put("name", properties.versionUdf()).put("value", partner.version());
        return ensure(hotelId, "PARTNER-" + partner.code(), profile);
    }

    /** The version of the partner last written to its profile, or -1 if it has none. */
    public long projectedVersion(JsonNode profile) {
        for (var udf : profile.path("profileDetails").path("userDefinedFields").path("numericUDFs")) {
            if (properties.versionUdf().equals(udf.path("name").asText())) {
                return udf.path("value").asLong(-1);
            }
        }
        return -1;
    }

    public java.util.Optional<JsonNode> byExternalId(String hotelId, String externalId) {
        return ohip.find(hotelId, "/crm/v1/externalSystems/{ext}/profiles/{id}", properties.externalSystemCode(), externalId);
    }

    Ensured ensure(String hotelId, String externalId, ObjectNode profile) {
        profile.putArray("externalReferences").addObject().put("id", externalId).put("idContext", properties.externalSystemCode());
        var existing = byExternalId(hotelId, externalId);
        if (existing.isPresent()) {
            var id = existing.get().path("profileIdList").path(0).path("id").asText();
            ohip.put(hotelId, "/crm/v1/profiles/{id}", profile, id);
            return new Ensured(id, false);
        }
        var created = ohip.post(hotelId, "/crm/v1/profiles", profile);
        return new Ensured(lastSegment(created.location()), true);
    }

    static String lastSegment(String location) {
        if (location == null) {
            throw new PmsRejectedException(500, null, "Opera created a profile and did not say where");
        }
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
