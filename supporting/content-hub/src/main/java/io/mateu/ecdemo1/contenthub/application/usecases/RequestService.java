package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.HumanTasks;
import io.mateu.ecdemo1.contenthub.application.out.Mailer;
import io.mateu.ecdemo1.contenthub.application.out.ProcessStarter;
import io.mateu.ecdemo1.contenthub.application.out.RequestRepository;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.request.EmployeeRequest;
import io.mateu.ecdemo1.contenthub.domain.request.RequestStatus;
import io.mateu.ecdemo1.contenthub.domain.request.RequestType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Employees' requests: made from the intranet, decided by the solicitud-empleado process (their manager,
 * who escalates to theirs after two days, then HR), followed here from what that process announces on
 * «solicitudes». Approving or rejecting happens in the engine's task inbox, as the person deciding.
 */
@Service
@Slf4j
public class RequestService {

    public static final String PROCESS = "solicitud-empleado";
    public static final String MANAGER_FORM = "aprobar-solicitud";
    public static final String HR_FORM = "revisar-solicitud-rrhh";

    /** A pending approval: the engine's task and the request it is about. */
    public record Approval(HumanTasks.Task task, EmployeeRequest request, boolean hr) {
    }

    final RequestRepository requests;
    final EmployeeDirectory directory;
    final ProcessStarter processes;
    final HumanTasks tasks;
    final Mailer mailer;
    final Clock clock;
    final String hrUser;
    final String portalUrl;

    public RequestService(RequestRepository requests, EmployeeDirectory directory, ProcessStarter processes,
                          HumanTasks tasks, Mailer mailer, Clock clock,
                          @Value("${content-hub.requests.hr-user:isabel.torres}") String hrUser,
                          @Value("${content-hub.portal-url:http://ec1.localhost:8800/intranet}") String portalUrl) {
        this.requests = requests;
        this.directory = directory;
        this.processes = processes;
        this.tasks = tasks;
        this.mailer = mailer;
        this.clock = clock;
        this.hrUser = hrUser;
        this.portalUrl = portalUrl;
    }

    /** Makes the request and starts its process, in one transaction (the outbox). */
    @Transactional
    public EmployeeRequest create(Reader reader, RequestType type, LocalDate from, LocalDate to, String description) {
        var employee = directory.find(reader.userId())
                .orElseThrow(() -> new IllegalArgumentException("No estás en el directorio de empleados"));
        var request = EmployeeRequest.create(UUID.randomUUID().toString().substring(0, 8), employee.userId(),
                employee.name(), type, from, to, description, employee.managerId(), clock.instant());
        requests.save(request);
        // the manager's manager takes it if it waits too long; at the top, it stays with the manager
        var escalateTo = directory.find(employee.managerId()).map(Employee::managerId).orElse(null);
        var variables = new LinkedHashMap<String, String>();
        variables.put("requestId", request.id());
        variables.put("employeeId", employee.userId());
        variables.put("employeeName", employee.name());
        variables.put("type", type.name());
        variables.put("managerId", employee.managerId());
        if (escalateTo != null) {
            variables.put("escalateToId", escalateTo);
        }
        variables.put("hrUserId", hrUser);
        processes.start(PROCESS, request.businessKey(), variables);
        return request;
    }

    public List<EmployeeRequest> mine(Reader reader) {
        return requests.byEmployee(reader.userId());
    }

    public Optional<EmployeeRequest> find(String id) {
        return requests.find(id);
    }

    /** What the process announced. Tells the employee when it is decided. */
    @Transactional
    public void apply(String requestId, RequestStatus status, String by, String comment) {
        var request = requests.find(requestId).orElse(null);
        if (request == null) {
            log.warn("Request {} announced as {} but unknown here", requestId, status);
            return;
        }
        var next = request.advance(status, by, comment, clock.instant());
        if (next == request) {
            return;
        }
        requests.save(next);
        if (next.status().decided()) {
            tell(next, comment);
        }
    }

    void tell(EmployeeRequest request, String comment) {
        var employee = directory.find(request.employeeId()).orElse(null);
        if (employee == null || employee.email() == null) {
            return;
        }
        var verb = request.status() == RequestStatus.APROBADA ? "aprobada" : "rechazada";
        try {
            mailer.send(List.of(employee.email()), "Tu solicitud de " + request.type().label().toLowerCase() + " ha sido " + verb,
                    "<p>Hola, " + esc(employee.name()) + ":</p><p>Tu solicitud de <b>" + esc(request.type().label().toLowerCase())
                            + "</b>" + (request.from() == null ? "" : " (" + request.from() + (request.to() == null ? "" : " → " + request.to()) + ")")
                            + " ha sido <b>" + verb + "</b>.</p>"
                            + (comment == null || comment.isBlank() ? "" : "<blockquote>" + esc(comment) + "</blockquote>")
                            + "<p><a href=\"" + esc(portalUrl) + "/solicitudes/" + esc(request.id()) + "\">Ver la solicitud</a></p>");
        } catch (RuntimeException e) {
            log.warn("Could not tell {} about request {}: {}", employee.userId(), request.id(), e.getMessage());
        }
    }

    /** What the person has to decide, newest first: as a manager, and as HR. */
    public List<Approval> approvals(String bearer) {
        return tasks.open(bearer, List.of(MANAGER_FORM, HR_FORM)).stream()
                .filter(t -> t.businessKey() != null && t.businessKey().startsWith("solicitud:"))
                .map(t -> requests.find(t.businessKey().substring("solicitud:".length()))
                        .map(r -> new Approval(t, r, HR_FORM.equals(t.formId()))).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Approves or rejects, as the person deciding — the engine checks the task is theirs. */
    public void decide(String bearer, Approval approval, boolean approve, String comment) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(approval.hr() ? "decisionRrhh" : "decision", approve ? "APROBAR" : "RECHAZAR");
        values.put(approval.hr() ? "comentarioRrhh" : "comentario", comment == null ? "" : comment);
        tasks.complete(bearer, approval.task(), values);
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
