package io.mateu.ecdemo1.contenthub.infra.out.health;

import io.mateu.ecdemo1.contenthub.application.out.HttpProbe;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * {@link HttpProbe} on the JDK's client: redirects not followed (the checker follows them, checking where
 * each one goes), and the body discarded unread.
 */
public class JdkHttpProbe implements HttpProbe {

    final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public Response request(URI uri, String method, Duration timeout, String userAgent) throws IOException {
        var request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            // a GET's body is closed unread: the status is all that is wanted, not a 20 MB PDF
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            response.body().close();
            return new Response(response.statusCode(), response.headers().firstValue("Location").orElse(null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }
}
