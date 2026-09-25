package io.mateu.ecdemo1.operamock.api;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An error the way OPERA answers one: a problem document with its own error code. The codes that
 * exist in the real API are the real ones (OPERAWS-GEN01244, …); the others say "MOCK" so nobody
 * mistakes them for Oracle's.
 */
public class OperaError extends RuntimeException {

    final HttpStatus status;
    final String code;

    public OperaError(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public static OperaError badRequest(String code, String detail) {
        return new OperaError(HttpStatus.BAD_REQUEST, code, detail);
    }

    public static OperaError notFound(String detail) {
        return new OperaError(HttpStatus.NOT_FOUND, "MOCK-NOT-FOUND", detail);
    }

    public Map<String, Object> body() {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", status.getReasonPhrase());
        body.put("title", getMessage());
        body.put("status", status.value());
        body.put("detail", getMessage());
        body.put("o:errorCode", code);
        body.put("language", "en");
        return body;
    }

    public HttpStatus status() {
        return status;
    }
}
