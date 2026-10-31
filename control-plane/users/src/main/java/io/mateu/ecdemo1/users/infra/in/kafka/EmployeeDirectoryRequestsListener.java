package io.mateu.ecdemo1.users.infra.in.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.users.application.usecases.user.directory.PublishEmployeeDirectorySnapshotUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Requests for the whole employee directory, from the content service when its copy is empty:
 * {@code {"requestId":"…","requestedBy":"content-hub"}} on {@value #TOPIC}. Each is answered with every
 * user republished on {@code employee-directory} ({@link PublishEmployeeDirectorySnapshotUseCase}),
 * once per request id.
 *
 * <p>An unreadable request is logged and skipped — retrying it would never make it readable. A failure
 * answering it (the database) is thrown, and the listener's error handler redelivers it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmployeeDirectoryRequestsListener {

    public static final String TOPIC = "employee-directory-requests";
    public static final String GROUP = "ec-demo1-users-directory";

    static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** The request, as the content service sends it. */
    record DirectoryRequest(String requestId, String requestedBy) {
    }

    final PublishEmployeeDirectorySnapshotUseCase snapshot;

    @KafkaListener(topics = TOPIC, groupId = GROUP)
    public void take(ConsumerRecord<String, byte[]> record) {
        DirectoryRequest request;
        try {
            request = JSON.readValue(record.value(), DirectoryRequest.class);
        } catch (IOException | RuntimeException e) {
            log.error("Unreadable directory request at {}-{}@{}, skipped: {}", record.topic(), record.partition(),
                    record.offset(), e.getMessage());
            return;
        }
        if (request == null || request.requestId() == null || request.requestId().isBlank()) {
            log.error("Directory request without a requestId at {}-{}@{}, skipped", record.topic(),
                    record.partition(), record.offset());
            return;
        }
        snapshot.handle(request.requestId(), request.requestedBy());
    }
}
