package io.mateu.ecdemo1.contenthub.infra.out.drive;

import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.contenthub.application.out.DriveClient;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The client against a local stand-in for Google's two endpoints: the requests as the documentation describes them. */
class GoogleDriveClientTest {

    HttpServer google;
    PublicKey publicKey;
    String serviceAccount;
    final List<String> assertions = new ArrayList<>();
    final List<String> fileRequests = new ArrayList<>();
    int fileStatus = 200;
    String fileBody = """
            {"name":"Plan 2027","mimeType":"application/vnd.google-apps.document","modifiedTime":"2026-10-30T09:15:00.000Z",
             "lastModifyingUser":{"displayName":"Ana Ruiz","emailAddress":"ana@riu.com"},
             "webViewLink":"https://docs.google.com/document/d/x/edit","thumbnailLink":"https://lh3/t"}""";

    @BeforeEach
    void start() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        var pair = keys.generateKeyPair();
        publicKey = pair.getPublic();
        google = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        google.createContext("/token", e -> {
            var form = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertions.add(URLDecoder.decode(form.substring(form.indexOf("assertion=") + 10), StandardCharsets.UTF_8));
            respond(e, 200, "{\"access_token\":\"tok\",\"expires_in\":3600}");
        });
        google.createContext("/drive/v3/files/", e -> {
            fileRequests.add(e.getRequestURI().getRawPath() + "?" + e.getRequestURI().getQuery() + " "
                    + e.getRequestHeaders().getFirst("Authorization"));
            respond(e, fileStatus, fileBody);
        });
        google.start();
        var pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        serviceAccount = Json.write(Map.of("type", "service_account", "client_email", "intranet@demo.iam.gserviceaccount.com",
                "private_key", pem, "token_uri", base() + "/token"));
    }

    void respond(com.sun.net.httpserver.HttpExchange e, int status, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        e.sendResponseHeaders(status, bytes.length);
        e.getResponseBody().write(bytes);
        e.close();
    }

    String base() {
        return "http://127.0.0.1:" + google.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        google.stop(0);
    }

    @Test
    void reads_the_metadata_with_the_read_only_scope_and_a_signed_token_request() throws Exception {
        var client = new GoogleDriveClient(serviceAccount, "", base());
        assertThat(client.configured()).isTrue();

        var m = client.metadata("file_123");

        assertThat(m.name()).isEqualTo("Plan 2027");
        assertThat(m.mimeType()).isEqualTo("application/vnd.google-apps.document");
        assertThat(m.modifiedTime()).isEqualTo(Instant.parse("2026-10-30T09:15:00Z"));
        assertThat(m.lastModifiedBy()).isEqualTo("Ana Ruiz");
        assertThat(m.webViewLink()).contains("docs.google.com");
        assertThat(m.thumbnailLink()).isEqualTo("https://lh3/t");
        assertThat(fileRequests.getFirst()).startsWith("/drive/v3/files/file_123?").contains("supportsAllDrives=true")
                .contains("lastModifyingUser(displayName,emailAddress)").endsWith("Bearer tok");

        var parts = assertions.getFirst().split("\\.");
        var verify = Signature.getInstance("SHA256withRSA");
        verify.initVerify(publicKey);
        verify.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verify.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        var claims = Json.MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.path("iss").asText()).isEqualTo("intranet@demo.iam.gserviceaccount.com");
        assertThat(claims.path("scope").asText()).isEqualTo("https://www.googleapis.com/auth/drive.metadata.readonly");
        assertThat(claims.path("aud").asText()).isEqualTo(base() + "/token");
        assertThat(claims.has("sub")).as("no delegation by default").isFalse();
    }

    @Test
    void impersonation_is_optional_and_goes_as_the_jwt_subject() throws Exception {
        new GoogleDriveClient(serviceAccount, "editor@riu.com", base()).metadata("file_123");
        var claims = Json.MAPPER.readTree(Base64.getUrlDecoder().decode(assertions.getFirst().split("\\.")[1]));
        assertThat(claims.path("sub").asText()).isEqualTo("editor@riu.com");
    }

    @Test
    void the_token_is_reused_while_it_lasts() {
        var client = new GoogleDriveClient(serviceAccount, null, base());
        client.metadata("file_123");
        client.metadata("file_456");
        assertThat(assertions).hasSize(1);
    }

    @Test
    void a_file_it_cannot_see_says_so() {
        var client = new GoogleDriveClient(serviceAccount, null, base());
        fileStatus = 404;
        assertThatThrownBy(() -> client.metadata("file_123")).isInstanceOf(DriveClient.DriveException.class)
                .hasMessageContaining("no está compartido");
        fileStatus = 403;
        assertThatThrownBy(() -> client.metadata("file_123")).hasMessageContaining("403");
    }

    @Test
    void the_thumbnail_is_optional() {
        fileBody = "{\"name\":\"Hoja\",\"mimeType\":\"application/vnd.google-apps.spreadsheet\","
                + "\"modifiedTime\":\"2026-10-30T09:15:00.000Z\",\"lastModifyingUser\":{\"displayName\":\"Ana\"}}";
        var m = new GoogleDriveClient(serviceAccount, null, base()).metadata("file_123");
        assertThat(m.name()).isEqualTo("Hoja");
        assertThat(m.lastModifiedBy()).isEqualTo("Ana");
        assertThat(m.thumbnailLink()).isNull();
    }

    @Test
    void the_key_comes_from_the_file_the_env_var_names_and_its_absence_is_no_error(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var file = dir.resolve("credentials.json");
        java.nio.file.Files.writeString(file, serviceAccount);
        assertThat(new GoogleDriveClient(file.toString(), "").configured()).isTrue();
        assertThat(new GoogleDriveClient(dir.resolve("absent.json").toString(), "").configured()).isFalse();
        assertThat(new GoogleDriveClient("", "").configured()).isFalse();
    }

    @Test
    void without_config_or_with_a_bad_key_it_stays_off_without_stopping_the_service() {
        assertThat(new GoogleDriveClient("", "", base()).configured()).isFalse();
        assertThat(new GoogleDriveClient(null, null, base()).configured()).isFalse();
        assertThat(new GoogleDriveClient("not json", "", base()).configured()).isFalse();
        assertThat(new GoogleDriveClient("{\"client_email\":\"x\",\"private_key\":\"nope\"}", "", base()).configured()).isFalse();
    }
}
