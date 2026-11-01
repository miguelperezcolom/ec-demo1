package io.mateu.ecdemo1.contenthub.domain.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * An employee's request — vacation, training, equipment — as the intranet shows it to them. Decided
 * by a process in the workflow engine (solicitud-empleado: their manager, then HR); this is the
 * projection of what that process announced, with its history.
 *
 * @param managerId who decides it first (the directory's manager when it was made)
 */
public record EmployeeRequest(String id, String employeeId, String employeeName, RequestType type, LocalDate from,
                              LocalDate to, String description, RequestStatus status, String managerId,
                              Instant createdAt, List<Step> history) {

    /** One thing that happened to it: the status it reached, who did it, what they said. */
    public record Step(Instant at, RequestStatus status, String by, String comment) {
    }

    public EmployeeRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public static EmployeeRequest create(String id, String employeeId, String employeeName, RequestType type,
                                         LocalDate from, LocalDate to, String description, String managerId,
                                         Instant now) {
        if (type == null) {
            throw new IllegalArgumentException("Elige qué pides");
        }
        if (type == RequestType.VACACIONES && (from == null || to == null || to.isBefore(from))) {
            throw new IllegalArgumentException("Las vacaciones necesitan unas fechas, la de fin no antes de la de inicio");
        }
        if (managerId == null) {
            throw new IllegalArgumentException("No tienes responsable en el directorio: RRHH tiene que asignártelo");
        }
        return new EmployeeRequest(id, employeeId, employeeName, type, from, to, description,
                RequestStatus.PENDIENTE_RESPONSABLE, managerId, now,
                List.of(new Step(now, RequestStatus.PENDIENTE_RESPONSABLE, employeeId, description)));
    }

    /**
     * What the process announced. The same announcement twice (a redelivery) changes nothing, and a
     * decided request stays decided.
     */
    public EmployeeRequest advance(RequestStatus next, String by, String comment, Instant at) {
        if (next == null || next == status || status.decided()) {
            return this;
        }
        var steps = new ArrayList<>(history);
        steps.add(new Step(at, next, by, comment == null || comment.isBlank() ? null : comment));
        return new EmployeeRequest(id, employeeId, employeeName, type, from, to, description, next, managerId,
                createdAt, steps);
    }

    /** The process's business key in the workflow engine. */
    public String businessKey() {
        return "solicitud:" + id;
    }
}
