package io.mateu.ecdemo1.content.application.usecases.content;

import io.mateu.ecdemo1.content.domain.aggregates.content.vo.CountryCode;
import io.mateu.ecdemo1.content.domain.aggregates.content.vo.LanguageCode;

public record ContentValueDto(CountryCode country, LanguageCode language, String value) {
}
