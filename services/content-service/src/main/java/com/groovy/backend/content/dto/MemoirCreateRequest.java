package com.groovy.backend.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemoirCreateRequest(

	@NotNull(message = "스터디 선택은 필수입니다.")
	Long studyId,

	@NotBlank(message = "제목은 필수입니다.")
	String title,

	@NotBlank(message = "내용은 필수입니다.")
	String content
) {
}
