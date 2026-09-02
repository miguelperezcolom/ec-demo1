package io.mateu.ecdemo1.apimcp.spec;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads an API's OpenAPI document properly, so its operations can be called.
 *
 * <p>This is the half the control plane deliberately does not carry. Listing operations for a
 * catalogue screen is reading {@code paths.<path>.<method>} out of a document, which needs no
 * library; knowing what an operation TAKES means resolving {@code $ref}s, components and composed
 * schemas, and that is what {@code resolveFully} is for. The two halves live on the two sides of
 * the split for that reason — see {@code ApiMcp} in the control plane.
 *
 * <p>Parsed documents are cached by url. A spec is fetched over the network and parsed into a
 * fairly large object graph; doing that on every catalogue poll would make the poll interval a
 * load decision about somebody else's server. {@link #forget(String)} drops one when its entry
 * changes.
 */
@Component
@Slf4j
public class SpecResolver {

    /** What an OpenAPI path item may declare. Anything else under a path is not an operation. */
    private static final List<String> METHODS =
            List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private final Map<String, ResolvedSpec> cache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * @param specUrl where the document lives
     * @return its operations keyed by {@code METHOD /path}, and where a credential belongs
     * @throws SpecUnreadableException when the document cannot be fetched or understood, said
     *         plainly because an operator composed the entry that names it
     */
    public ResolvedSpec resolve(String specUrl) {
        return cache.computeIfAbsent(specUrl, SpecResolver::parse);
    }

    /** Drops a cached document, so the next resolve re-reads it. */
    public void forget(String specUrl) {
        cache.remove(specUrl);
    }

    private static ResolvedSpec parse(String specUrl) {
        var options = new ParseOptions();
        // Both, and the second is the point: an operation's parameters are usually $refs into
        // components, and a tool schema built from an unresolved ref describes nothing.
        options.setResolve(true);
        options.setResolveFully(true);
        var result = new OpenAPIV3Parser().readLocation(specUrl, null, options);
        var openApi = result.getOpenAPI();
        if (openApi == null) {
            throw new SpecUnreadableException("The document at " + specUrl
                    + " could not be read as OpenAPI: "
                    + String.join("; ", result.getMessages() == null ? List.of() : result.getMessages()));
        }
        if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
            throw new SpecUnreadableException(
                    "The document at " + specUrl + " declares no paths");
        }
        var operations = new LinkedHashMap<String, ResolvedSpec.Operation>();
        openApi.getPaths().forEach((path, item) -> {
            for (var method : METHODS) {
                var operation = operationOf(item, method);
                if (operation == null) {
                    continue;
                }
                var reference = method.toUpperCase(Locale.ROOT) + " " + path;
                operations.put(reference, resolveOperation(method, path, item, operation));
            }
        });
        log.info("Read {} operation(s) from {}", operations.size(), specUrl);
        return new ResolvedSpec(Map.copyOf(operations), credentialPlacement(openApi));
    }

    private static Operation operationOf(PathItem item, String method) {
        return switch (method) {
            case "get" -> item.getGet();
            case "put" -> item.getPut();
            case "post" -> item.getPost();
            case "delete" -> item.getDelete();
            case "options" -> item.getOptions();
            case "head" -> item.getHead();
            case "patch" -> item.getPatch();
            case "trace" -> item.getTrace();
            default -> null;
        };
    }

    /**
     * One operation's inputs as a single flat object schema, plus {@code body} when it takes one.
     *
     * <p>Flat on purpose. A model fills in one object, and nesting path parameters under a
     * {@code path} key and query parameters under a {@code query} key would be a shape invented
     * here that the API's own documentation never mentions — every example the model has read
     * writes {@code id}, not {@code path.id}. The body is the one exception, because its fields
     * are the API's and would otherwise collide with a parameter of the same name.
     */
    private static ResolvedSpec.Operation resolveOperation(String method, String path,
                                                           PathItem item, Operation operation) {
        var parameters = new ArrayList<ResolvedSpec.Parameter>();
        var properties = new LinkedHashMap<String, Object>();
        var required = new ArrayList<String>();

        // Path-level parameters apply to every operation under it, and a spec that declares the
        // id there rather than on each method is common enough that missing them would break
        // exactly the operations most worth exposing.
        var declared = new ArrayList<Parameter>();
        if (item.getParameters() != null) {
            declared.addAll(item.getParameters());
        }
        if (operation.getParameters() != null) {
            declared.addAll(operation.getParameters());
        }

        for (var parameter : declared) {
            if (parameter.getName() == null || parameter.getIn() == null) {
                continue;
            }
            parameters.add(new ResolvedSpec.Parameter(parameter.getName(), parameter.getIn(),
                    Boolean.TRUE.equals(parameter.getRequired())));
            var schema = SchemaMapper.toJsonSchema(parameter.getSchema());
            if (parameter.getDescription() != null) {
                schema.put("description", parameter.getDescription());
            }
            properties.put(parameter.getName(), schema);
            if (Boolean.TRUE.equals(parameter.getRequired())) {
                required.add(parameter.getName());
            }
        }

        var hasBody = operation.getRequestBody() != null;
        if (hasBody) {
            var body = operation.getRequestBody();
            var schema = body.getContent() == null ? null
                    : body.getContent().values().stream()
                            .map(io.swagger.v3.oas.models.media.MediaType::getSchema)
                            .filter(java.util.Objects::nonNull)
                            .findFirst().orElse(null);
            var bodySchema = SchemaMapper.toJsonSchema(schema);
            bodySchema.put("description", body.getDescription() != null
                    ? body.getDescription()
                    : "The request body this operation takes");
            properties.put("body", bodySchema);
            if (Boolean.TRUE.equals(body.getRequired())) {
                required.add("body");
            }
        }

        var inputSchema = new LinkedHashMap<String, Object>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.copyOf(required));
        return new ResolvedSpec.Operation(method.toUpperCase(Locale.ROOT), path,
                List.copyOf(parameters), hasBody, inputSchema);
    }

    /**
     * Where this API wants its credential, according to its own document.
     *
     * <p>The first declared security scheme wins. A document declaring several means the API
     * accepts several, and the catalogue stores one secret — so picking one is unavoidable, and
     * the first is the one the document leads with. A document declaring none gets bearer, which
     * is what the internal APIs here actually use, and is stated in the log so an operator whose
     * API wants something else has a line to find.
     */
    private static ResolvedSpec.CredentialPlacement credentialPlacement(OpenAPI openApi) {
        var schemes = openApi.getComponents() == null ? null
                : openApi.getComponents().getSecuritySchemes();
        if (schemes == null || schemes.isEmpty()) {
            return ResolvedSpec.CredentialPlacement.bearer();
        }
        var scheme = schemes.values().iterator().next();
        if (scheme.getType() == SecurityScheme.Type.APIKEY && scheme.getName() != null) {
            var in = scheme.getIn();
            var kind = in == SecurityScheme.In.QUERY ? ResolvedSpec.CredentialPlacement.Kind.QUERY
                    : in == SecurityScheme.In.COOKIE ? ResolvedSpec.CredentialPlacement.Kind.COOKIE
                    : ResolvedSpec.CredentialPlacement.Kind.HEADER;
            return new ResolvedSpec.CredentialPlacement(kind, scheme.getName());
        }
        // http (bearer or basic) and everything else: the Authorization header. For basic, the
        // stored secret is sent as-is — this service does not compose a user:password pair it was
        // never given, and the catalogue field is one string.
        return ResolvedSpec.CredentialPlacement.bearer();
    }

    public static class SpecUnreadableException extends RuntimeException {
        public SpecUnreadableException(String message) { super(message); }
    }
}
