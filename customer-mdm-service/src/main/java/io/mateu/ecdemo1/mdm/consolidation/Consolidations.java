package io.mateu.ecdemo1.mdm.consolidation;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The inbox of «Ciclo de Limpieza» (HLA CRM-MDM): a contact carrying an MDM id left Salesforce, and
 * this finds out why. The event cannot say — before the delete, a merge's survivor is not yet on
 * the record — so the absorbed contact is read back, deleted, where {@code MasterRecordId} now
 * names the survivor. The event and the poll both land here; whichever comes second finds the work
 * done.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Consolidations {

    final SalesforceClient salesforce;
    final Survivorship survivorship;
    final ConsolidationRepository consolidations;

    public void received(String absorbedMdmId, String absorbedContactId, String via) {
        if (absorbedMdmId == null || absorbedMdmId.isBlank()) {
            return;
        }
        var known = consolidations.findById(absorbedMdmId);
        if (known.isPresent() && known.get().appliedAt != null) {
            log.debug("{} already consolidated (seen again via {})", absorbedMdmId, via);
            return;
        }
        var absorbed = salesforce.contact(absorbedContactId).orElse(null);
        if (absorbed == null || !absorbed.path("IsDeleted").asBoolean(false)) {
            // Restored from the recycle bin, or never gone: nothing to do.
            log.info("{} ({}) is not deleted in Salesforce; ignored", absorbedMdmId, absorbedContactId);
            return;
        }
        var masterId = text(absorbed, "MasterRecordId");
        if (masterId == null) {
            survivorship.removed(absorbedMdmId, absorbedContactId, via);
            return;
        }
        var master = salesforce.contact(masterId).orElseThrow(() -> new IllegalStateException("No contact " + masterId));
        var survivorMdmId = text(master, "MDM_Id__c");
        if (survivorMdmId == null) {
            // Merged into a contact someone made in Salesforce by hand, which is nobody in the MDM yet:
            // it becomes this customer — the absorbed one lives on, as that contact.
            salesforce.assignMdmId(masterId, absorbedMdmId);
            survivorship.adopted(absorbedMdmId, absorbedContactId, masterId, master, via);
            return;
        }
        survivorship.merged(survivorMdmId, absorbedMdmId, masterId, absorbedContactId, master, via);
    }

    static String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
