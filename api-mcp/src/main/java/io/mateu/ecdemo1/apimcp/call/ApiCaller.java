package io.mateu.ecdemo1.apimcp.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.apimcp.spec.ResolvedSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Makes the call an exposed tool stands for.
 *
 * <p>The one place in this deployment where a stored API credential is put on a wire, and it is
 * deliberately small enough to read in one sitting. It logs the operation and the status and never
 * the arguments or the secret: an argument to a booking API is somebody's name, and a status code
 * is enough to tell a broken tool from a rejected one.
 *
 * <p>A non-2xx answer is a RESULT and not an exception. The model asked to call something and the
 * something said no; telling it the status and what the body said lets it explain that to the
 * person, which is the whole reason a tool call round-trips through a model at all. Only a call
 * that could not be made — a bad URL, a timeout, a refused connection — is a failure of this
 * service rather than an answer from the API.
 */
@Component
@Slf4j
public class ApiCaller {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Below the 60s the agent gives a tool call, so a slow API is reported rather than cut off. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    /** Enough for a listing, short of filling a model's context with somebody's whole database. */
    private static final int MAX_RESPONSE_CHARS = 60_000;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper json = new ObjectMapper();

    /** What the model is handed back: the text, and whether it is an answer or a failure. */
    public record Outcome(boolean ok, String text) {
    }

    public Outcome call(String baseUrl, String secret, ResolvedSpec.CredentialPlacement credential,
                        ResolvedSpec.Operation operation, Map<String, Object> arguments) {
        var args = arguments == null ? Map.<String, Object>of() : arguments;
        try {
            var missing = missingRequiredPathParameters(operation, args);
            if (!missing.isEmpty()) {
                // Caught here rather than sent: a path parameter left out produces a URL for a
                // different resource, and an API answering 200 for the wrong thing is the one
                // failure a model cannot notice.
                return new Outcome(false, "This tool needs " + String.join(", ", missing)
                        + " and it was not supplied.");
            }
            var request = build(baseUrl, secret, credential, operation, args);
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var body = truncate(response.body());
            log.info("{} {} answered {}", operation.method(), operation.pathTemplate(),
                    response.statusCode());
            if (response.statusCode() / 100 == 2) {
                return new Outcome(true, body.isBlank()
                        // A 204 is a successful call with nothing to read, and "" reads to a model
                        // as a failure it should retry.
                        ? "The call succeeded and returned no content."
                        : body);
            }
            return new Outcome(false, "The API answered HTTP " + response.statusCode()
                    + (body.isBlank() ? "." : ": " + body));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(false, "The call was interrupted.");
        } catch (Exception e) {
            // The message and not the stack: this string is read by a model and then, through it,
            // by a person. It must never carry the URL's query string, which may hold the key.
            log.error("{} {} could not be called: {}", operation.method(),
                    operation.pathTemplate(), e.toString());
            return new Outcome(false, "The call could not be made: " + e.getMessage());
        }
    }

    private static List<String> missingRequiredPathParameters(ResolvedSpec.Operation operation,
                                                             Map<String, Object> args) {
        return operation.parameters().stream()
                .filter(p -> "path".equals(p.in()))
                .filter(p -> args.get(p.name()) == null)
                .map(ResolvedSpec.Parameter::name)
                .toList();
    }

    private HttpRequest build(String baseUrl, String secret,
                              ResolvedSpec.CredentialPlacement credential,
                              ResolvedSpec.Operation operation, Map<String, Object> args)
            throws Exception {
        var path = operation.pathTemplate();
        var query = new ArrayList<String>();
        var headers = new ArrayList<String[]>();

        for (var parameter : operation.parameters()) {
            var value = args.get(parameter.name());
            if (value == null) {
                continue;
            }
            switch (parameter.in()) {
                case "path" -> path = path.replace("{" + parameter.name() + "}",
                        encodePathSegment(String.valueOf(value)));
                case "query" -> query.add(encode(parameter.name()) + "=" + encode(String.valueOf(value)));
                case "header" -> headers.add(new String[]{parameter.name(), String.valueOf(value)});
                // A cookie parameter is not forwarded. Nothing here has a cookie jar, and quietly
                // dropping it is better than inventing a Cookie header from a model's argument.
                default -> log.debug("Ignoring {} parameter {}", parameter.in(), parameter.name());
            }
        }

        var hasSecret = secret != null && !secret.isBlank();
        if (hasSecret && credential.kind() == ResolvedSpec.CredentialPlacement.Kind.QUERY) {
            query.add(encode(credential.name()) + "=" + encode(secret));
        }

        var url = trimTrailingSlash(baseUrl) + path
                + (query.isEmpty() ? "" : "?" + String.join("&", query));
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT);

        for (var header : headers) {
            builder.header(header[0], header[1]);
        }
        if (hasSecret) {
            switch (credential.kind()) {
                case HEADER -> builder.header(credential.name(),
                        authorizationValue(credential, secret));
                case COOKIE -> builder.header("Cookie", credential.name() + "=" + secret);
                case QUERY -> { /* already in the url */ }
            }
        }

        var bodyPublisher = HttpRequest.BodyPublishers.noBody();
        if (operation.hasBody() && args.get("body") != null) {
            builder.header("Content-Type", "application/json");
            bodyPublisher = HttpRequest.BodyPublishers.ofString(
                    json.writeValueAsString(args.get("body")), StandardCharsets.UTF_8);
        }
        builder.header("Accept", "application/json");
        return builder.method(operation.method(), bodyPublisher).build();
    }

    /**
     * The value the Authorization header carries.
     *
     * <p>A stored secret containing a space already names its scheme — an operator who typed
     * "Basic abc" or "Bearer abc" meant it, and prefixing that again produces a header no API
     * accepts. A bare token gets {@code Bearer }, which is what a spec that declares nothing is
     * assumed to want. Any other header takes the secret exactly as stored.
     */
    private static String authorizationValue(ResolvedSpec.CredentialPlacement credential,
                                             String secret) {
        if (!credential.isAuthorizationHeader() || secret.contains(" ")) {
            return secret;
        }
        return "Bearer " + secret;
    }

    /** Form encoding, for a query string. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Path encoding, which is not form encoding: a space in a path segment is {@code %20} and a
     * literal {@code +} there is a plus sign, not a space. URLEncoder only does the form flavour,
     * so the one difference that matters is undone by hand.
     */
    private static String encodePathSegment(String value) {
        return encode(value).replace("+", "%20");
    }

    private static String trimTrailingSlash(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_RESPONSE_CHARS ? body
                : body.substring(0, MAX_RESPONSE_CHARS)
                        + "\n… truncated; the API returned more than this tool will pass on.";
    }
}
