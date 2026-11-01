package io.mateu.ecdemo1.users.infra.in.seed;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;

import static io.mateu.ecdemo1.users.infra.in.seed.DemoDirectorySeeder.EMPLOYEES;
import static io.mateu.ecdemo1.users.infra.in.seed.DemoDirectorySeeder.GENERAL_MANAGER;
import static io.mateu.ecdemo1.users.infra.in.seed.DemoDirectorySeeder.profileOf;
import static org.assertj.core.api.Assertions.assertThat;

/** The org chart the seed gives the demo's employees. */
class DemoDirectorySeederHierarchyTest {

    static String managerOf(String id) {
        return profileOf(EMPLOYEES.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow()).managerId();
    }

    @Test
    void staffReportToTheirHeadHeadsToTheDirectorDirectorsToTheGeneralManager() {
        assertThat(managerOf("ana.perez")).isEqualTo("carlos.diaz");          // receptionist → head of reception
        assertThat(managerOf("carlos.diaz")).isEqualTo("elena.rodriguez");    // head → hotel director
        assertThat(managerOf("pedro.gonzalez")).isEqualTo("elena.rodriguez"); // no head of maintenance → director
        assertThat(managerOf("elena.rodriguez")).isEqualTo(GENERAL_MANAGER);   // director → general manager
        assertThat(managerOf("isabel.torres")).isEqualTo(GENERAL_MANAGER);     // central services
        assertThat(managerOf(GENERAL_MANAGER)).isNull();
    }

    @Test
    void everyChainEndsAtTheGeneralManagerWithoutLoopsAndEveryManagerExists() {
        var managers = new HashMap<String, String>();
        EMPLOYEES.forEach(e -> managers.put(e.id(), profileOf(e).managerId()));
        for (var id : managers.keySet()) {
            var seen = new HashSet<String>();
            var at = id;
            while (managers.get(at) != null) {
                assertThat(seen.add(at)).as("loop at " + at).isTrue();
                at = managers.get(at);
                assertThat(managers).as("manager " + at + " is seeded").containsKey(at);
            }
            assertThat(at).isEqualTo(GENERAL_MANAGER);
        }
    }

    @Test
    void phonesAreDistinctAndLocal() {
        var phones = EMPLOYEES.stream().map(e -> profileOf(e).phone()).toList();
        assertThat(new HashSet<>(phones)).hasSameSizeAs(phones);
        assertThat(profileOf(EMPLOYEES.stream().filter(e -> "MRU01".equals(e.hotel())).findFirst().orElseThrow())
                .phone()).startsWith("+230");
    }
}
