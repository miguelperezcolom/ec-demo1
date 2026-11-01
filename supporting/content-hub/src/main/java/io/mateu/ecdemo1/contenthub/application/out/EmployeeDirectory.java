package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.employee.Employee;

import java.util.List;
import java.util.Optional;

/**
 * The local projection of the employees and their attributes, fed by the users service. Where the
 * universe of profile hashes and the newsletter's recipients come from.
 */
public interface EmployeeDirectory {

    /** @return the employee as it was before, if it was known */
    Optional<Employee> upsert(Employee employee);

    Optional<Employee> remove(String userId);

    Optional<Employee> find(String userId);

    List<Employee> all();

    long count();
}
