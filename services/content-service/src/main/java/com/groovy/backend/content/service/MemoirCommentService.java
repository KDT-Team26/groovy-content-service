package com.groovy.backend.content.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.content.Memoir;
import com.groovy.backend.content.MemoirComment;
import com.groovy.backend.content.client.StudyServiceClient;
import com.groovy.backend.client.UserServiceClient;
import com.groovy.backend.content.dto.MemoirCommentRequest;
import com.groovy.backend.content.dto.MemoirCommentResponse;
import com.groovy.backend.common.exception.ForbiddenException;
import com.groovy.backend.content.notification.NotificationOutboxPublisher;
import com.groovy.backend.content.repository.MemoirCommentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MSA 전환(content-service 추출): author가 더 이상 JPA 연관관계가 아니라 authorId(Long)뿐이라,
 * groovy 시절 comment.getAuthor().getName()으로 바로 읽던 이름을 UserServiceClient로 배치
 * 조회해야 한다(getComments).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoirCommentService {

	// 댓글 작성 시 회고록이 연결된 스터디가 얻는 경험치. 레벨업 기준은 study-service의 Study.addExp를 참고.
	private static final int COMMENT_EXP = 5;

	private final MemoirCommentRepository memoirCommentRepository;
	private final MemoirService memoirService;
	private final UserServiceClient userServiceClient;
	private final NotificationOutboxPublisher notificationOutboxPublisher;
	private final StudyServiceClient studyServiceClient;

	@Transactional
	public MemoirCommentResponse createComment(Long authorId, Long memoirId, MemoirCommentRequest request) {
		Memoir memoir = memoirService.getMemoirEntity(memoirId);

		MemoirComment comment = MemoirComment.builder()
			.memoir(memoir)
			.authorId(authorId)
			.content(request.content())
			.build();

		MemoirComment saved = memoirCommentRepository.save(comment);
		studyServiceClient.addExp(memoir.getStudyId(), COMMENT_EXP);
		log.info("회고록 댓글 작성: memoirId={}, commentId={}, authorId={}", memoirId, saved.getId(), authorId);

		String authorName = userServiceClient.findNamesByIds(List.of(authorId)).get(authorId);

		// 회고록 작성자 본인이 자기 글에 단 댓글은 알림을 보내지 않는다.
		if (!memoir.isAuthor(authorId)) {
			notificationOutboxPublisher.memoirCommentAdded(
				memoir.getAuthorId(), authorName, memoirId, memoir.getTitle());
		}

		return MemoirCommentResponse.from(saved, authorName);
	}

	public List<MemoirCommentResponse> getComments(Long memoirId) {
		List<MemoirComment> comments = memoirCommentRepository.findByMemoirIdWithAuthor(memoirId);
		List<Long> authorIds = comments.stream().map(MemoirComment::getAuthorId).distinct().toList();
		Map<Long, String> authorNameByAuthorId = userServiceClient.findNamesByIds(authorIds);

		return comments.stream()
			.map(comment -> MemoirCommentResponse.from(comment, authorNameByAuthorId.get(comment.getAuthorId())))
			.toList();
	}

	@Transactional
	public MemoirCommentResponse updateComment(Long userId, Long memoirId, Long commentId, MemoirCommentRequest request) {
		MemoirComment comment = getCommentEntity(memoirId, commentId);
		validateAuthor(comment, userId);

		comment.update(request.content());
		log.info("회고록 댓글 수정: memoirId={}, commentId={}, userId={}", memoirId, commentId, userId);

		String authorName = userServiceClient.findNamesByIds(List.of(comment.getAuthorId())).get(comment.getAuthorId());
		return MemoirCommentResponse.from(comment, authorName);
	}

	@Transactional
	public void deleteComment(Long userId, Long memoirId, Long commentId) {
		MemoirComment comment = getCommentEntity(memoirId, commentId);
		validateAuthor(comment, userId);

		memoirCommentRepository.delete(comment);
		log.info("회고록 댓글 삭제: memoirId={}, commentId={}, userId={}", memoirId, commentId, userId);
	}

	private MemoirComment getCommentEntity(Long memoirId, Long commentId) {
		return memoirCommentRepository.findById(commentId)
			.filter(comment -> comment.getMemoir().getId().equals(memoirId))
			.orElseThrow(() -> {
				log.warn("존재하지 않는 댓글: memoirId={}, commentId={}", memoirId, commentId);
				return new IllegalArgumentException("존재하지 않는 댓글입니다.");
			});
	}

	private void validateAuthor(MemoirComment comment, Long userId) {
		if (!comment.isAuthor(userId)) {
			log.warn("댓글 작성자 아님: commentId={}, userId={}", comment.getId(), userId);
			throw new ForbiddenException("작성자만 수행할 수 있는 작업입니다.");
		}
	}
}
