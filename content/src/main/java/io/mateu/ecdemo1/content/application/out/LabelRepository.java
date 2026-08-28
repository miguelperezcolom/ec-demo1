package io.mateu.ecdemo1.content.application.out;

import io.mateu.ecdemo1.content.domain.aggregates.label.Label;
import io.mateu.ecdemo1.content.domain.aggregates.label.vo.LabelId;

public interface LabelRepository extends Repository<Label, LabelId> {
}
