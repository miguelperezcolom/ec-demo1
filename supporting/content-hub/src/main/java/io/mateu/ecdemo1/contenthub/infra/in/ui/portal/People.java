package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui.esc;

/**
 * The intranet's people: the employee directory as readers see it — only the enabled, only work data
 * (name, job, where, phone, email, who they report to). What the person search, a person's card and the
 * org chart are drawn from.
 */
@Component
@RequiredArgsConstructor
public class People {

    static final Comparator<Employee> BY_NAME = Comparator.comparing(
            e -> e.name() == null ? e.userId() : e.name(), Collator.getInstance(Locale.of("es")));

    final EmployeeDirectory directory;

    /** Where the portal is, for {@link #photoAddress}. */
    @org.springframework.beans.factory.annotation.Value("${content-hub.portal-url:http://ec1.localhost:8800/intranet}")
    String portalUrl = "";

    public List<Employee> all() {
        return directory.all().stream().filter(Employee::enabled).sorted(BY_NAME).toList();
    }

    public Optional<Employee> find(String userId) {
        return directory.find(userId).filter(Employee::enabled);
    }

    /** Whoever's name, job, department, hotel or region contains every word typed (accents ignored). */
    public List<Employee> search(String text) {
        var words = text == null ? List.<String>of() : List.of(fold(text).split("\\s+")).stream()
                .filter(w -> !w.isBlank()).toList();
        return all().stream().filter(e -> {
            var haystack = fold(String.join(" ", e.userId(), e.name() == null ? "" : e.name(),
                    jobTitle(e), department(e), hotel(e), region(e)));
            return words.stream().allMatch(haystack::contains);
        }).toList();
    }

    public List<Employee> reportsOf(String userId) {
        return all().stream().filter(e -> userId.equals(e.managerId())).toList();
    }

    /** From the person up to the top: their manager, the manager's… — stops at a loop or an unknown id. */
    public List<Employee> chainOf(Employee employee) {
        var chain = new ArrayList<Employee>();
        var seen = new HashSet<String>();
        seen.add(employee.userId());
        var at = employee.managerId();
        while (at != null && seen.add(at)) {
            var manager = find(at);
            if (manager.isEmpty()) {
                break;
            }
            chain.add(manager.get());
            at = manager.get().managerId();
        }
        return chain;
    }

    /** Same department at the same hotel (or both at headquarters), them excluded. */
    public List<Employee> teamOf(Employee employee) {
        var dept = code(employee, Attribute.DEPARTMENT);
        if (dept == null) {
            return List.of();
        }
        var hotel = code(employee, Attribute.HOTEL);
        return all().stream().filter(e -> !e.userId().equals(employee.userId()))
                .filter(e -> dept.equals(code(e, Attribute.DEPARTMENT)))
                .filter(e -> java.util.Objects.equals(hotel, code(e, Attribute.HOTEL))).toList();
    }

    /**
     * The organisation by structure: company → region → hotel (or headquarters) → department → people,
     * each level sorted by its label.
     */
    public Map<String, Map<String, Map<String, Map<String, List<Employee>>>>> byStructure() {
        Function<Function<Employee, String>, java.util.stream.Collector<Employee, ?, Map<String, List<Employee>>>> group =
                key -> Collectors.groupingBy(key, java.util.TreeMap::new, Collectors.toList());
        return all().stream().collect(Collectors.groupingBy(this::company, java.util.TreeMap::new,
                Collectors.groupingBy(this::region, java.util.TreeMap::new,
                        Collectors.groupingBy(e -> hotel(e).isEmpty() ? "Servicios centrales" : hotel(e),
                                java.util.TreeMap::new, group.apply(e -> department(e).isEmpty() ? "Sin departamento"
                                        : department(e))))));
    }

    public String company(Employee e) {
        return label(e, "company", Attribute.COMPANY);
    }

    public String region(Employee e) {
        return label(e, "region", Attribute.REGION);
    }

    public String hotel(Employee e) {
        return label(e, "hotel", Attribute.HOTEL);
    }

    public String department(Employee e) {
        return label(e, "department", Attribute.DEPARTMENT);
    }

    public String jobTitle(Employee e) {
        return label(e, "jobTitle", Attribute.JOB_TITLE);
    }

    static String code(Employee e, Attribute attribute) {
        return e.attributes().get(attribute).orElse(null);
    }

    static String label(Employee e, String key, Attribute attribute) {
        return e.label(key, code(e, attribute));
    }

    public static String link(String userId) {
        return Ui.PORTAL + "/personas/" + userId;
    }

    /** A person as a link with their job beneath, for lists. */
    public String chip(Employee e) {
        var job = jobTitle(e);
        return "<a href=\"" + link(e.userId()) + "\" style=\"display:inline-flex;align-items:center;gap:.5rem;"
                + "text-decoration:none;color:inherit\">" + avatar(e, 32) + "<span><b>" + esc(name(e)) + "</b>"
                + (job.isEmpty() ? "" : "<br><span style=\"opacity:.7;font-size:.85em\">" + esc(job) + "</span>")
                + "</span></a>";
    }

    /**
     * Their photo's full address, or null: the web renderer's org chart takes anything else for initials, and
     * would write the path in the circle.
     */
    public String photoAddress(Employee e) {
        if (e.photoUrl() == null) {
            return null;
        }
        var portal = java.net.URI.create(portalUrl);
        return portal.getScheme() == null ? e.photoUrl() : portal.getScheme() + "://" + portal.getAuthority() + e.photoUrl();
    }

    /**
     * What goes where only an image's address fits, like a listing's first column: their photo, or their initials
     * drawn as an image, so that every name in the column starts at the same place.
     */
    public static String picture(Employee e) {
        if (e.photoUrl() != null) {
            return e.photoUrl();
        }
        var svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 64\"><rect width=\"64\" height=\"64\" fill=\""
                + colourOf(e) + "\"/><text x=\"32\" y=\"32\" dy=\".35em\" text-anchor=\"middle\" font-family=\"sans-serif\" "
                + "font-size=\"26\" font-weight=\"600\" fill=\"#fff\">" + esc(initials(name(e))) + "</text></svg>";
        return "data:image/svg+xml;base64," + java.util.Base64.getEncoder()
                .encodeToString(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Their photo, or their initials on a colour picked from the user id: the same person, the same colour. */
    public static String avatar(Employee e, int size) {
        if (e.photoUrl() != null) {
            return "<img src=\"" + esc(e.photoUrl()) + "\" alt=\"\" style=\"flex:none;width:" + size + "px;height:" + size
                    + "px;border-radius:50%;object-fit:cover\">";
        }
        return "<span style=\"display:inline-flex;align-items:center;justify-content:center;flex:none;width:" + size
                + "px;height:" + size + "px;border-radius:50%;background:" + colourOf(e) + ";color:#fff;font-weight:600;"
                + "font-size:" + Math.round(size * 0.4) + "px\">" + esc(initials(name(e))) + "</span>";
    }

    private static String colourOf(Employee e) {
        var colours = List.of("#b42318", "#175cd3", "#067647", "#9e4c0a", "#6941c6", "#c11574", "#0e7090");
        return colours.get(Math.floorMod(e.userId().hashCode(), colours.size()));
    }

    static String name(Employee e) {
        return e.name() == null || e.name().isBlank() ? e.userId() : e.name();
    }

    static String initials(String name) {
        var parts = name.trim().split("\\s+");
        var first = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
        var last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    static String fold(String s) {
        return java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
