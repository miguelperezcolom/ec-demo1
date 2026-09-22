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
