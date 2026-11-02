package io.mateu.ecdemo1.contenthub.infra.out.drive;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.contenthub.application.out.DriveClient;
import io.mateu.ecdemo1.contenthub.domain.drive.DriveFile;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Drive API v3 with a service account, over plain HTTP (no Google library for one call): a signed JWT is
 * exchanged for an access token (OAuth 2.0 JWT bearer), then {@code files.get} asks for the metadata only —
 * the {@code drive.metadata.readonly} scope cannot read a file's content.
 *
 * <p>{@code GOOGLE_DRIVE_CREDENTIALS_FILE} is the path of the key file Google gives, mounted from an optional
 * secret; without it (or if it cannot be read or does not parse) this is not configured and nothing asks it.
 * {@code GOOGLE_DRIVE_IMPERSONATE} is the Workspace user to act as with domain-wide delegation; empty, the
 * default, the account sees what is shared with its own address.
 */
@Component
@Slf4j
public class GoogleDriveClient implements DriveClient {

    static final String SCOPE = "https://www.googleapis.com/auth/drive.metadata.readonly";
    static final String FIELDS = "name,mimeType,modifiedTime,lastModifyingUser(displayName,emailAddress),webViewLink,thumbnailLink";

    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final String apiUrl;
    final String impersonate;
    String clientEmail;
    String tokenUri;
    PrivateKey key;

    String token;
    Instant tokenExpires = Instant.EPOCH;

    @Autowired
    public GoogleDriveClient(@Value("${content-hub.drive.credentials-file:}") String credentialsFile,
                             @Value("${content-hub.drive.impersonate:}") String impersonate) {
        this(keyFile(credentialsFile), impersonate, "https://www.googleapis.com");
    }

    /** The key file's content; empty when there is none, which is the normal state without the secret. */
    static String keyFile(String credentialsFile) {
        if (credentialsFile == null || credentialsFile.isBlank()) {
            return "";
        }
        try {
            return Files.readString(Path.of(credentialsFile.trim()));
        } catch (java.io.IOException | RuntimeException e) {
            log.info("No Drive credentials at {} ({}); Drive stays off", credentialsFile, e.getClass().getSimpleName());
            return "";
        }
    }

    GoogleDriveClient(String serviceAccountJson, String impersonate, String apiUrl) {
        this.apiUrl = apiUrl;
        this.impersonate = impersonate == null || impersonate.isBlank() ? null : impersonate.trim();
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            return;
        }
        try {
            var account = Json.MAPPER.readTree(serviceAccountJson);
            clientEmail = account.path("client_email").asText();
            tokenUri = account.path("token_uri").asText("https://oauth2.googleapis.com/token");
            key = privateKey(account.path("private_key").asText());
            if (clientEmail.isBlank()) {
                throw new IllegalArgumentException("no client_email");
            }
        } catch (Exception e) {
            // never its content in the log: it is a secret. A bad key must not stop the service either.
            log.warn("GOOGLE_DRIVE_CREDENTIALS_FILE is not a service account key file ({}); Drive stays off",
                    e.getClass().getSimpleName());
            key = null;
        }
    }

    static PrivateKey privateKey(String pem) throws Exception {
        var base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    @Override
    public boolean configured() {
        return key != null;
    }

    @Override
    public DriveFile.Metadata metadata(String fileId) {
        if (!configured()) {
            throw new DriveException("Google Drive no está configurado");
        }
        var url = apiUrl + "/drive/v3/files/" + URLEncoder.encode(fileId, StandardCharsets.UTF_8)
                + "?supportsAllDrives=true&fields=" + URLEncoder.encode(FIELDS, StandardCharsets.UTF_8);
        var response = send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken()).GET().build());
        if (response.statusCode() == 404) {
            throw new DriveException("Drive no encuentra el fichero, o no está compartido con la cuenta de servicio");
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            token = null;
            throw new DriveException("Drive no da acceso a este fichero (" + response.statusCode() + ")");
        }
        if (response.statusCode() / 100 != 2) {
            throw new DriveException("Drive ha contestado " + response.statusCode());
        }
        return metadataOf(read(response.body()));
    }

    static DriveFile.Metadata metadataOf(JsonNode f) {
        var user = f.path("lastModifyingUser");
        var modifiedBy = text(user, "displayName");
        return new DriveFile.Metadata(text(f, "name"), text(f, "mimeType"),
                f.hasNonNull("modifiedTime") ? Instant.parse(f.get("modifiedTime").asText()) : null,
                modifiedBy != null ? modifiedBy : text(user, "emailAddress"),
                text(f, "webViewLink"), text(f, "thumbnailLink"));
    }

    static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    synchronized String accessToken() {
        var now = Instant.now();
        if (token != null && now.isBefore(tokenExpires)) {
            return token;
        }
        var form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion(now), StandardCharsets.UTF_8);
        var response = send(HttpRequest.newBuilder(URI.create(tokenUri)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        if (response.statusCode() / 100 != 2) {
            throw new DriveException("Google no ha dado acceso a la cuenta de servicio (" + response.statusCode() + ")");
        }
        var answer = read(response.body());
        token = answer.path("access_token").asText();
        tokenExpires = now.plusSeconds(Math.max(0, answer.path("expires_in").asLong(3600) - 60));
        return token;
    }

    /** The signed JWT (RS256) that asks for a token, for the service account or the user it acts as. */
    String assertion(Instant now) {
        var claims = new LinkedHashMap<String, Object>(Map.of("iss", clientEmail, "scope", SCOPE, "aud", tokenUri,
                "iat", now.getEpochSecond(), "exp", now.getEpochSecond() + 3600));
        if (impersonate != null) {
            claims.put("sub", impersonate);
        }
        var signed = encode(Json.write(Map.of("alg", "RS256", "typ", "JWT")).getBytes(StandardCharsets.UTF_8)) + "."
                + encode(Json.write(claims).getBytes(StandardCharsets.UTF_8));
        try {
            var signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(signed.getBytes(StandardCharsets.US_ASCII));
            return signed + "." + encode(signature.sign());
        } catch (java.security.GeneralSecurityException e) {
            throw new DriveException("No se ha podido firmar la petición a Google", e);
        }
    }

    static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static JsonNode read(String body) {
        try {
            return Json.MAPPER.readTree(body);
        } catch (java.io.IOException e) {
            throw new DriveException("Respuesta de Google no válida", e);
        }
    }

    HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DriveException("Interrumpido al preguntar a Google", e);
        } catch (java.io.IOException e) {
            throw new DriveException("No se puede contactar con Google: " + e.getMessage(), e);
        }
    }
}
