package io.mateu.ecdemo1.pmsintegration.ohip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.integration.model.partner.PmsPartner;
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
     * The reservation's holder, as the guest profile Opera needs to take a reservation (F001). One
     * per reservation, as before; what is new is that it arrives already knowing who the customer is
     * (HLA CRM-MDM, F001): the MDM's customer code travels as the profile's CRM reference, and a merge
     * in the MDM rewrites it. Without the code — the MDM did not answer — the profile is written all
     * the same: identity never stops a sale.
     */
    public Ensured ensureGuest(String hotelId, String locator, Person holder, String customerId, String knownProfileId) {
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
        if (!properties.profileReferences()) {
            // Nothing to find it by in Opera but the reservation: the caller passes the profile the
            // reservation already names, if it exists.
            if (knownProfileId != null) {
                ohip.put(hotelId, "/crm/v1/profiles/{id}", profile, knownProfileId);
                return new Ensured(knownProfileId, false);
            }
            return new Ensured(lastSegment(ohip.post(hotelId, "/crm/v1/profiles", profile).location()), true);
        }
        if (customerId != null && !properties.crmExternalSystem().isBlank()) {
            profile.putArray("externalReferences").addObject().put("id", customerId).put("idContext", properties.crmExternalSystem());
        }
        return ensure(hotelId, "HOLDER-" + locator, profile);
    }

    /**
     * The chain's partners as Opera has them — agencies, companies and sources — when Opera is where
     * partners are kept. Each by its CorporateId, the partner's code in the chain; one without it is
     * nobody the ERP could know it by, and is left out. A profile belongs to the chain, but OHIP still
     * wants a hotel on the call.
     */
    public java.util.List<PmsPartner> partners(String hotelId) {
        var found = new java.util.ArrayList<PmsPartner>();
        for (var type : java.util.List.of("Agent", "Company", "Source")) {
            var offset = 0;
            while (true) {
                // A name wildcard: OHIP refuses a search by type alone («minimum search criteria not met»).
                var page = ohip.get(hotelId, "/crm/v1/profiles?profileType={t}&profileName={n}&hotelId={h}&limit=200&offset={o}&summaryInfo=true",
                        type, "%", hotelId, offset).body().path("profileSummaries");
                for (var info : page.path("profileInfo")) {
                    String profileId = null;
                    String corporateId = null;
                    for (var id : info.path("profileIdList")) {
                        switch (id.path("type").asText()) {
                            case "Profile" -> profileId = id.path("id").asText();
                            case "CorporateId" -> corporateId = id.path("id").asText();
                            default -> {
                            }
                        }
                    }
                    if (profileId != null && corporateId != null && !corporateId.isBlank()) {
                        var profile = info.path("profile");
                        var name = profile.path("company").path("companyName").asText(profile.path("formerName").path("name").asText(corporateId));
                        found.add(new PmsPartner(corporateId, profileId, type, name));
                    }
                }
                if (!page.path("hasMore").asBoolean(false) || page.path("profileInfo").isEmpty()) {
                    break;
                }
                offset += page.path("profileInfo").size();
            }
        }
        return found;
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
        var references = profile.has("externalReferences") ? (ArrayNode) profile.get("externalReferences")
                : profile.putArray("externalReferences");
        references.insertObject(0).put("id", externalId).put("idContext", properties.externalSystemCode());
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
