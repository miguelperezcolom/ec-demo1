package io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo;

/**
 * What kind of description an API is published with, and therefore how its operations are read and
 * how a call to one is made.
 *
 * <p>Only {@link #REST} is implemented. {@link #SOAP} can be catalogued and is refused with a
 * sentence when its operations are imported — the same shape the RAG catalogue uses for the vector
 * stores it does not implement, and for the same reason: an entry that can be declared before it
 * can be served is useful, and a silent no-op is not.
 */
public enum ApiKind {
    /** An OpenAPI document. Operations are its path + method pairs. */
    REST,
    /** A WSDL. Not implemented — cataloguing one is allowed, importing from it is refused. */
    SOAP
}
