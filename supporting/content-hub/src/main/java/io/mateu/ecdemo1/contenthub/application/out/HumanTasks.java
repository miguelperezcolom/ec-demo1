package io.mateu.ecdemo1.contenthub.application.out;

import java.util.List;
import java.util.Map;

/**
 * The workflow engine's task inbox (its forms engine), always as the person asking: their bearer token
 * goes with every call, so they see and complete only what is theirs.
 */
public interface HumanTasks {

    record Task(String id, String formId, String businessKey, String status, String assignee, String dueDate) {
    }

    /** The open tasks of these forms the person can see (theirs, and those they are a candidate for). */
    List<Task> open(String bearer, List<String> formIds);

    /** Claims it if nobody holds it yet, then completes it with these values. */
    void complete(String bearer, Task task, Map<String, Object> values);
}
