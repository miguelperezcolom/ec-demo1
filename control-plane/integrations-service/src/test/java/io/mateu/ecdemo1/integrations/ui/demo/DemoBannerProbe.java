package io.mateu.ecdemo1.integrations.ui.demo;

/** The banner's markup, for tests in other packages. */
public final class DemoBannerProbe {

    private DemoBannerProbe() {
    }

    public static String html(String until) {
        return DemoBanner.html(until);
    }
}
