package io.mateu.ecdemo1.iacp.application.out.apispec;

import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiKind;

import java.util.List;

/**
 * Reads an API's own description and says what operations it declares.
 *
 * <p>A port, so the domain and the use cases never learn what an OpenAPI document looks like, and
 * so the SOAP half can arrive as another adapter rather than as a branch through everything.
 */
public interface ApiSpecReader {

    /** Whether this reader is the one for a given kind of description. */
    boolean supports(ApiKind kind);

    /**
     * @param specUrl where the document lives
     * @return the operations it declares, in the order the document lists them
     * @throws IllegalArgumentException if the document cannot be fetched or understood — said
     *         plainly, because the operator is looking at the screen that asked for it
     */
    List<ApiOperation> read(String specUrl);
}
