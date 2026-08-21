package com.groovy.backend.content.dto;

import java.time.LocalDateTime;

import com.groovy.backend.content.Memoir;

public record MemoirResponse(
	String id,
	String studyId,
	String studyTitle,
	String authorId,
	String authorName,
	String title,
	String content,
	long commentCount,
	long likeCount,
	boolean liked,
	Integer studyLevel,
	Integer studyExpPoint,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	// MSA 전환(content-service 추출): study/author 둘 다 더 이상 Memoir 엔티티에 물려있는 엔티티
	// 참조가 아니라 studyId/authorId(Long)뿐이라, 제목/레벨/경험치/이름은 호출부(MemoirService)가
	// StudyServiceClient/UserServiceClient를 통해 조회해서 넘겨준다.
	public static MemoirResponse from(
		Memoir memoir,
		String authorName,
		String studyTitle,
		Integer studyLevel,
		Integer studyExpPoint,
		long commentCount,
		long likeCount,
		boolean liked
	) {
		return new MemoirResponse(
			String.valueOf(memoir.getId()),
			String.valueOf(memoir.getStudyId()),
			studyTitle,
			String.valueOf(memoir.getAuthorId()),
			authorName,
			memoir.getTitle(),
			memoir.getContent(),
			commentCount,
			likeCount,
			liked,
			studyLevel,
			studyExpPoint,
			memoir.getCreatedAt(),
			memoir.getUpdatedAt()
		);
	}
}
