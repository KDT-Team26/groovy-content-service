package com.groovy.backend.content.dto;

import jakarta.validation.constraints.NotBlank;

public record MemoirCommentRequest(

	@NotBlank(message = "댓글 내용은 필수입니다.")
	String content
) {
}
