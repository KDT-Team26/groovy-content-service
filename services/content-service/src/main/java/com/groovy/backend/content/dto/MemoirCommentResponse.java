package com.groovy.backend.content.dto;

import java.time.LocalDateTime;

import com.groovy.backend.content.MemoirComment;

public record MemoirCommentResponse(
	String id,
	String memoirId,
	String authorId,
	String authorName,
	String content,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	// MSA 전환(content-service 추출): author가 더 이상 JPA 연관관계가 아니라 authorId(Long)뿐이라
	// comment.getAuthor().getName()을 쓸 수 없다 — 호출부(MemoirCommentService)가
	// UserServiceClient로 조회한 이름을 넘겨준다.
	public static MemoirCommentResponse from(MemoirComment comment, String authorName) {
		return new MemoirCommentResponse(
			String.valueOf(comment.getId()),
			String.valueOf(comment.getMemoir().getId()),
			String.valueOf(comment.getAuthorId()),
			authorName,
			comment.getContent(),
			comment.getCreatedAt(),
			comment.getUpdatedAt()
		);
	}
}
