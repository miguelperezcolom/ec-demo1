package io.mateu.ecdemo1.pmsintegration.write;

import java.util.Set;

/**
 * What a property says about its packages, which decides whether a board goes on a reservation as a
 * package of its own: whether the property sells the package separately at all, and which packages a
 * rate plan already carries.
 */
public interface PackageRules {

    /** Whether the property sells this package on its own. One it does not know, it is left to answer. */
    boolean soldSeparately(String hotelId, String packageCode);

    /** The packages the rate plan carries: its own, and those of its package groups. */
    Set<String> includedIn(String hotelId, String ratePlanCode);

    /** No rules: every package is sold separately and no rate carries one. */
    PackageRules NONE = new PackageRules() {
        @Override
        public boolean soldSeparately(String hotelId, String packageCode) {
            return true;
        }

        @Override
        public Set<String> includedIn(String hotelId, String ratePlanCode) {
            return Set.of();
        }
    };
}
