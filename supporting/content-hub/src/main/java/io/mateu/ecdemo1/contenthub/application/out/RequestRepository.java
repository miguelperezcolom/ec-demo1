package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.request.EmployeeRequest;

import java.util.List;
import java.util.Optional;

public interface RequestRepository {

    void save(EmployeeRequest request);

    Optional<EmployeeRequest> find(String id);

    /** An employee's requests, newest first. */
    List<EmployeeRequest> byEmployee(String employeeId);
}
