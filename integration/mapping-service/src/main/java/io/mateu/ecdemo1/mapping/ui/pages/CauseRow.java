package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.integration.model.mapping.CauseType;
import io.mateu.uidl.data.Status;

/** @param reason what the key does not say: for a PMS_REJECTED, Opera's own words and error code (e.g. RSV00138) */
public record CauseRow(String key, String type, String hotel, String reason, long waiting, String since, int openings,
                       Status status) {

    /**
     * The part of the description the key does not already say: Opera's message and code for a
     * rejection («The PMS refused … of reservation … of hotel …: <Opera's words> — RSV00138»), the whole
     * description otherwise.
     */
    static String reason(CauseType type, String description) {
        if (description == null) {
            return "";
        }
        if (type == CauseType.PMS_REJECTED) {
            var colon = description.indexOf(": ");
            return colon < 0 ? description : description.substring(colon + 2);
        }
        return description;
    }
}
