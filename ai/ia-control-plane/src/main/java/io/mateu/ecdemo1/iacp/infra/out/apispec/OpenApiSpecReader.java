package io.mateu.ecdemo1.iacp.infra.out.apispec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.mateu.ecdemo1.iacp.application.out.apispec.ApiOperation;
import io.mateu.ecdemo1.iacp.application.out.apispec.ApiSpecReader;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads an OpenAPI document and says what operations it declares.
 *
 * <p><b>No OpenAPI library, and that is a decision rather than a shortcut.</b> Listing operations
 * is reading {@code paths.<path>.<method>} out of a JSON or YAML document, which Jackson already
 * does here — this module carries jackson-dataformat-yaml for the GitOps catalogues. A real parser
 * resolves refs, components and schemas, and all of that is needed to CALL an operation, not to
 * offer one in a list. The side that calls is a different pod and can bring the parser it needs;
 * making this module carry it to populate a listing would be paying for the hard half here.
 *
 * <p>The other half of the reason is this module's own history: spring-ai-openai is deliberately
 * absent from this deployment because it is built against Spring Framework 6 and dies on Boot 4 at
 * the first request. A dependency added to a Boot 4 module here is not free.
 */
@Component
@Slf4j
public class OpenApiSpecReader implements ApiSpecReader {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /** The methods an OpenAPI path item may declare. Anything else under a path is not one. */
    private static final List<String> METHODS =
            List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public boolean supports(ApiKind kind) {
        return kind == ApiKind.REST;
    }

    @Override
    public List<ApiOperation> read(String specUrl) {
        var root = parse(fetch(specUrl), specUrl);
        var paths = root.get("paths");
        if (paths == null || !paths.isObject()) {
            throw new IllegalArgumentException(
                    "That document has no 'paths' — it does not look like an OpenAPI description: "
                    + specUrl);
        }
        var operations = new ArrayList<ApiOperation>();
        paths.properties().forEach(path -> METHODS.forEach(method -> {
            var operation = path.getValue().get(method);
            if (operation == null || !operation.isObject()) {
                return;
            }
            operations.add(new ApiOperation(
                    method.toUpperCase(Locale.ROOT) + " " + path.getKey(),
                    text(operation, "operationId"),
                    text(operation, "summary")));
        }));
        if (operations.isEmpty()) {
            // Said rather than returned empty: a screen with nothing to choose from is
            // indistinguishable from one that failed, and the operator retries instead of looking
            // at the document.
            throw new IllegalArgumentException(
                    "That OpenAPI document declares no operations: " + specUrl);
        }
        return List.copyOf(operations);
    }

    private String fetch(String specUrl) {
        try {
            var response = http.send(
                    HttpRequest.newBuilder(URI.create(specUrl))
                            .timeout(Duration.ofSeconds(20))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalArgumentException(
                        "The spec answered HTTP " + response.statusCode() + ": " + specUrl);
            }
            return response.body();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Interrupted while fetching " + specUrl, e);
        } catch (Exception e) {
            // The operator is looking at the screen that asked for this, so the message has to be
            // about their document rather than about a stack.
            throw new IllegalArgumentException(
                    "Could not fetch the spec at " + specUrl + ": " + e.getMessage(), e);
        }
    }

    /** JSON or YAML, decided by trying rather than by the file extension, which often lies. */
    private JsonNode parse(String body, String specUrl) {
        try {
            return JSON.readTree(body);
        } catch (Exception notJson) {
            try {
                return YAML.readTree(body);
            } catch (Exception notYaml) {
                throw new IllegalArgumentException(
                        "The document at " + specUrl + " is neither JSON nor YAML", notYaml);
            }
        }
    }

    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
