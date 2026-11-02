package io.mateu.ecdemo1.contenthub.application.out;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

/**
 * One HTTP request to an external URL, as the link checker makes it: no redirects followed (the checker
 * follows them itself, checking each hop's address), the body never read.
 */
public interface HttpProbe {

    /** @param location the Location header of a redirect, else null */
    record Response(int status, String location) {
    }

    /** @throws java.net.http.HttpTimeoutException when it did not answer in time; IOException when it could not connect */
    Response request(URI uri, String method, Duration timeout, String userAgent) throws IOException;

    /** The host's addresses, to refuse private ones before asking. */
    default List<InetAddress> resolve(String host) throws UnknownHostException {
        return List.of(InetAddress.getAllByName(host));
    }
}
