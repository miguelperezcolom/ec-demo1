package io.mateu.ecdemo1.apimcp.spec;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An API's own description, reduced to the two things making a call needs: what each operation
 * takes, and where the credential goes.
 *
 * <p>Everything else the document says — servers, tags, examples, response schemas — is
 * deliberately dropped. The base url comes from the catalogue and not from the spec's server list,
 * because the spec describes where the API is published and the catalogue records where THIS
 * deployment reaches it, which is regularly not the same host.
 */
public record ResolvedSpec(Map<String, Operation> operations, CredentialPlacement credential) {

    /** An operation the spec declares, keyed by the reference an ExposedTool records. */
    public record Operation(String method, String pathTemplate, List<Parameter> parameters,
                            boolean hasBody, Map<String, Object> inputSchema) {
    }

    /**
     * One input of an operation.
     *
     * <p>{@code in} is the OpenAPI location — {@code path}, {@code query}, {@code header} or
     * {@code cookie}. A path parameter that the model does not supply is a broken URL rather than
     * a missing filter, which is why required-ness is carried here and not only in the schema.
     */
    public record Parameter(String name, String in, boolean required) {
    }

    /**
     * Where an API wants its credential.
     *
     * <p>Read from the spec's own security schemes rather than asked of the operator, because the
     * document already says it and a second copy in the catalogue would be a second thing to get
     * wrong. {@link #bearer()} is the fallback when a document declares nothing, which is the
     * common case for the internal APIs this deployment talks to.
     */
    public record CredentialPlacement(Kind kind, String name) {

        public enum Kind { HEADER, QUERY, COOKIE }

        /** {@code Authorization: Bearer <secret>} — the assumption when the spec is silent. */
        public static CredentialPlacement bearer() {
            return new CredentialPlacement(Kind.HEADER, "Authorization");
        }

        /** Whether the value needs the {@code Bearer } prefix that only this header takes. */
        public boolean isAuthorizationHeader() {
            return kind == Kind.HEADER && "Authorization".equalsIgnoreCase(name);
        }
    }

    public Optional<Operation> operation(String reference) {
        return Optional.ofNullable(operations.get(reference));
    }
}
