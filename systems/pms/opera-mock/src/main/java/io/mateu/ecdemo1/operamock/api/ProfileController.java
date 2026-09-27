package io.mateu.ecdemo1.operamock.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mateu.ecdemo1.operamock.store.OperaStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * crm (profiles): create, update, and read by the id an external system gave it — which is what
 * makes "look it up before creating it" one call. A profile belongs to the chain, not a hotel.
 */
@RestController
@RequiredArgsConstructor
public class ProfileController {

    final OperaStore store;

    /**
     * A search by type and name, as a real tenant answers it: without a name it refuses («minimum search
     * criteria not met»), and it answers summaries whose ids include the CorporateId — the partner's
     * code in the chain. Here that is the code the partner was projected with.
     */
    @GetMapping("/crm/v1/profiles")
    public java.util.Map<String, Object> search(@org.springframework.web.bind.annotation.RequestParam(required = false) String profileType,
                                                @org.springframework.web.bind.annotation.RequestParam(required = false) String profileName,
                                                @org.springframework.web.bind.annotation.RequestParam(defaultValue = "200") int limit,
                                                @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int offset) {
        if (profileName == null || profileName.isBlank()) {
            throw OperaError.badRequest("OPERAWS-GEN01346", "Minimum search criteria not met for fetching profiles, please provide more details.");
        }
        var prefix = profileName.replace("%", "").toLowerCase();
        var matching = store.profiles().stream()
                .filter(p -> profileType == null || profileType.equals(p.path("profileDetails").path("profileType").asText()))
                .filter(p -> name(p).toLowerCase().startsWith(prefix))
                .toList();
        var page = matching.stream().skip(offset).limit(limit).map(p -> {
            var ids = new java.util.ArrayList<java.util.Map<String, String>>();
            ids.add(java.util.Map.of("id", p.path("profileIdList").path(0).path("id").asText(), "type", "Profile"));
            for (var ref : p.path("externalReferences")) {
                if (ref.path("id").asText().startsWith("PARTNER-")) {
                    ids.add(java.util.Map.of("id", ref.path("id").asText().substring("PARTNER-".length()), "type", "CorporateId"));
                }
            }
            return java.util.Map.of("profileIdList", ids, "profile", java.util.Map.of("company", java.util.Map.of("companyName", name(p))));
        }).toList();
        return java.util.Map.of("profileSummaries", java.util.Map.of("profileInfo", page, "totalResults", matching.size(),
                "hasMore", offset + page.size() < matching.size()));
    }

    static String name(com.fasterxml.jackson.databind.JsonNode p) {
        var details = p.path("profileDetails");
        var company = details.path("company").path("companyName").asText("");
        return !company.isBlank() ? company : details.path("customer").path("personName").path(0).path("surname").asText("");
    }

    @PostMapping("/crm/v1/profiles")
    public ResponseEntity<Map<String, Object>> create(@RequestBody ObjectNode profile) {
        if (!profile.path("profileDetails").isObject()) {
            throw OperaError.badRequest("MOCK-PROFILE", "profileDetails is required");
        }
        var type = profile.path("profileDetails").path("profileType").asText();
        if (!List.of("Guest", "Agent", "Company", "Source").contains(type)) {
            throw OperaError.badRequest("MOCK-PROFILE", "Unsupported profileType " + type);
        }
        var id = store.nextId();
        profile.putArray("profileIdList").addObject().put("id", id).put("type", "Profile");
        store.putProfile(id, profile);
        var href = "/crm/v1/profiles/" + id;
        return ResponseEntity.created(URI.create(href)).body(Map.of("links", List.of(Map.of("href", href, "rel", "self"))));
    }

    @PutMapping("/crm/v1/profiles/{profileId}")
    public Map<String, Object> update(@PathVariable String profileId, @RequestBody ObjectNode profile) {
        store.profile(profileId).orElseThrow(() -> OperaError.notFound("Profile %s not found".formatted(profileId)));
        profile.putArray("profileIdList").addObject().put("id", profileId).put("type", "Profile");
        store.putProfile(profileId, profile);
        return Map.of("links", List.of());
    }

    @GetMapping("/crm/v1/profiles/{profileId}")
    public ObjectNode get(@PathVariable String profileId) {
        return store.profile(profileId).orElseThrow(() -> OperaError.notFound("Profile %s not found".formatted(profileId)));
    }

    @GetMapping("/crm/v1/externalSystems/{extSystemCode}/profiles/{profileExternalId}")
    public ObjectNode byExternalId(@PathVariable String extSystemCode, @PathVariable String profileExternalId) {
        return store.profiles().stream()
                .filter(p -> {
                    for (var ref : p.path("externalReferences")) {
                        if (profileExternalId.equals(ref.path("id").asText()) && extSystemCode.equals(ref.path("idContext").asText())) {
                            return true;
                        }
                    }
                    return false;
                })
                .findFirst()
                .orElseThrow(() -> OperaError.notFound("No profile %s of %s".formatted(profileExternalId, extSystemCode)));
    }
}
