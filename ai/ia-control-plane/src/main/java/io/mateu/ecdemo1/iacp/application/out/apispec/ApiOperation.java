package io.mateu.ecdemo1.iacp.application.out.apispec;

/**
 * One operation as an API's own description declares it, before anybody decides to expose it.
 *
 * <p>{@code reference} is the durable handle — {@code GET /bookings/{id}} for REST — and it is what
 * an ExposedTool records. {@code summary} is whatever the spec said, offered to the operator as a
 * starting point for a description and never used as one: a summary copied out of a spec is
 * written for someone reading the spec, not for a model deciding whether to call the thing.
 */
public record ApiOperation(String reference, String operationId, String summary) {
}
