package io.mateu.ecdemo1.notices.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, String>, JpaSpecificationExecutor<Notice> {

    List<Notice> findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc(String subjectType, String subjectId);

    List<Notice> findBySubjectTypeAndActiveTrue(String subjectType);
}
