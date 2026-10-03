package io.mateu.ecdemo1.gateway.infra.config;

import static io.mateu.ecdemo1.gateway.infra.config.StaticAssetCachingFilter.IMMUTABLE;
import static io.mateu.ecdemo1.gateway.infra.config.StaticAssetCachingFilter.REVALIDATE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class StaticAssetCachingFilterTest {

    @Test
    void redwoodVersionedPathsAreImmutable() {
        assertThat(StaticAssetCachingFilter.policyFor("/version_1791040948086/bundles/vb-app-bundle.js"))
                .isEqualTo(IMMUTABLE);
        assertThat(StaticAssetCachingFilter.policyFor("/version_1791040948086/flows/main/main-flow.json"))
                .isEqualTo(IMMUTABLE);
    }

    @Test
    void unversionedStaticFilesRevalidate() {
        assertThat(StaticAssetCachingFilter.policyFor("/assets/mateu-vaadin.js")).isEqualTo(REVALIDATE);
        assertThat(StaticAssetCachingFilter.policyFor("/js/keycloak.min.js")).isEqualTo(REVALIDATE);
        assertThat(StaticAssetCachingFilter.policyFor("/assets/index.css")).isEqualTo(REVALIDATE);
        assertThat(StaticAssetCachingFilter.policyFor("/_inbox/push/sw.js")).isEqualTo(REVALIDATE);
    }

    @Test
    void pagesAndApisAreLeftAlone() {
        assertThat(StaticAssetCachingFilter.policyFor("/")).isNull();
        assertThat(StaticAssetCachingFilter.policyFor("/inbox/pending")).isNull();
        assertThat(StaticAssetCachingFilter.policyFor("/mateu/v3/sync/home")).isNull();
        assertThat(StaticAssetCachingFilter.policyFor("/_booking/mateu/v3/sync/x")).isNull();
        assertThat(StaticAssetCachingFilter.policyFor("/version_/x.js")).isEqualTo(REVALIDATE);
        assertThat(StaticAssetCachingFilter.policyFor("/a.b/c")).isNull();
    }

    @Test
    void replacesSpringSecurityNoStoreOnSuccess() {
        var headers = securityDefaults();
        StaticAssetCachingFilter.apply(IMMUTABLE, 200, headers);
        assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo(IMMUTABLE);
        assertThat(headers.getFirst(HttpHeaders.PRAGMA)).isNull();
        assertThat(headers.getFirst(HttpHeaders.EXPIRES)).isNull();
    }

    @Test
    void notModifiedWithoutCacheControlGetsThePolicy() {
        var headers = new HttpHeaders();
        StaticAssetCachingFilter.apply(REVALIDATE, 304, headers);
        assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo(REVALIDATE);
    }

    @Test
    void errorsAreNeverMadeCacheable() {
        var headers = securityDefaults();
        StaticAssetCachingFilter.apply(IMMUTABLE, 404, headers);
        assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    @Test
    void aBackendsOwnPolicyIsKept() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "max-age=60");
        StaticAssetCachingFilter.apply(IMMUTABLE, 200, headers);
        assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("max-age=60");
    }

    private static HttpHeaders securityDefaults() {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set(HttpHeaders.EXPIRES, "0");
        return headers;
    }
}
