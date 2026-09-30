package io.mateu.ecdemo1.notices.infra.in.rest;

import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.SubjectType;
import io.mateu.ecdemo1.notices.application.Notices;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The notices, read — what applies to a subject — and published again whole. Inside the cluster only:
 * the gateway routes the service's screens (/_notices), not this.
 */
@RestController
@RequestMapping("/notices")
public class NoticesController {

    final Notices notices;

    public NoticesController(Notices notices) {
        this.notices = notices;
    }

    /** What the desk sees of a subject at that hotel and moment, some day of the stay. */
    @GetMapping
    public List<NoticeView> applicable(@RequestParam SubjectType subjectType, @RequestParam String subjectId,
                                       @RequestParam(required = false) String hotel,
                                       @RequestParam(required = false) NoticeMoment moment,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate arrival,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departure) {
        return notices.applicable(subjectType, subjectId, hotel, moment, arrival, departure).stream()
                .map(NoticeView::of).toList();
    }

    /** Every notice on the notices topic again, as it is: for a reader that lost its copy. */
    @PostMapping("/republish")
    public Map<String, Integer> republish() {
        return Map.of("published", notices.republishAll());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ProblemDetail refused(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }
}
