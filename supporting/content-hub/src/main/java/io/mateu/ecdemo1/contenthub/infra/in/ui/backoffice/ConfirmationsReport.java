package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.application.out.AcknowledgementRepository;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.ack.Acknowledgement;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * For the items that must be confirmed: who should (the employees the visibility function says see it)
 * and who has, in the version that counts.
 */
@Service
@RequiredArgsConstructor
@Editors
public class ConfirmationsReport {

    final ItemRepository items;
    final EmployeeDirectory directory;
    final Visibility visibility;
    final AcknowledgementRepository acks;

    public record Line(String itemId, String itemTitle, Employee employee, boolean confirmed) {
    }

    public List<Line> lines() {
        var out = new ArrayList<Line>();
        var employees = directory.all().stream().filter(Employee::enabled).toList();
        for (var item : items.findAll()) {
            if (item.status() != ItemStatus.PUBLISHED || item.confirmationVersion() == 0) {
                continue;
            }
            var title = item.publishedVersion().orElseThrow().content().in(item.defaultLanguage(), item.defaultLanguage()).title();
            var confirmed = acks.users(item.id(), Acknowledgement.Type.CONFIRMATION, item.confirmationVersion());
            for (var e : employees) {
                if (visibility.isVisible(e.attributes(), item.id())) {
                    out.add(new Line(item.id(), title, e, confirmed.contains(e.userId())));
                }
            }
        }
        return out;
    }

    public static String hotel(Employee e) {
        return e.attributes().get(Attribute.HOTEL).orElse("(servicios centrales)");
    }

    public static String department(Employee e) {
        return e.attributes().get(Attribute.DEPARTMENT).orElse("—");
    }
}
